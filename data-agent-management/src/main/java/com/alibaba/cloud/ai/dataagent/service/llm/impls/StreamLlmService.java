/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.llm.impls;

import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.client.RestClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class StreamLlmService implements LlmService {

	private final AiModelRegistry registry;

	private final LangfuseService langfuseService;

	private final AtomicBoolean structuredSyncCompatible = new AtomicBoolean(true);

	public StreamLlmService(AiModelRegistry registry, LangfuseService langfuseService) {
		this.registry = registry;
		this.langfuseService = langfuseService;
	}

	public StreamLlmService(AiModelRegistry registry) {
		this(registry, null);
	}

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return callObserved("llm-system-user", system, user);
	}

	@Override
	public Flux<ChatResponse> callObserved(String observationName, String system, String user) {
		return trace(observationName, formatInput(system, user),
				registry.getChatClient().prompt().system(system).user(user).stream().chatResponse());
	}

	@Override
	public Flux<ChatResponse> call(String system, String user, Class<?> outputType) {
		return callObserved("llm-system-user-structured", system, user, outputType);
	}

	@Override
	public Flux<ChatResponse> callObserved(String observationName, String system, String user, Class<?> outputType) {
		if (!structuredSyncCompatible.get()) {
			return callObserved(observationName, system, user);
		}
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(outputType)
			.maxRepeatAttempts(2)
			.build();
		Flux<ChatResponse> response = Mono
			.fromCallable(() -> registry.getChatClient()
				.prompt()
				.system(system)
				.user(user)
				.advisors(advisor)
				.call()
				.chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux()
			.onErrorResume(RestClientException.class, ex -> {
				structuredSyncCompatible.set(false);
				log.warn(
						"Structured synchronous response is incompatible with the current model endpoint; "
								+ "using streaming output for this and subsequent structured calls: {}",
						summarizeExceptionChain(ex));
				return callObserved(observationName, system, user);
			});
		return trace(observationName, formatInput(system, user), response);
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return callSystemObserved("llm-system", system);
	}

	@Override
	public Flux<ChatResponse> callSystemObserved(String observationName, String system) {
		return trace(observationName, "system:\n" + system,
				registry.getChatClient().prompt().system(system).stream().chatResponse());
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		return callUserObserved("llm-user", user);
	}

	@Override
	public Flux<ChatResponse> callUserObserved(String observationName, String user) {
		return trace(observationName, "user:\n" + user,
				registry.getChatClient().prompt().user(user).stream().chatResponse());
	}

	@Override
	public Flux<ChatResponse> callUserObservedWithMaxTokens(String observationName, String user, Integer maxTokens) {
		if (maxTokens == null || maxTokens <= 0) {
			return callUserObserved(observationName, user);
		}
		OpenAiChatOptions options = OpenAiChatOptions.builder().maxTokens(maxTokens).build();
		return trace(observationName, "user:\n" + user,
				registry.getChatClient().prompt().user(user).options(options).stream().chatResponse(), maxTokens);
	}

	@Override
	public Flux<ChatResponse> callUser(String user, Class<?> outputType) {
		return callUserObserved("llm-user-structured", user, outputType);
	}

	@Override
	public Flux<ChatResponse> callUserObserved(String observationName, String user, Class<?> outputType) {
		if (!structuredSyncCompatible.get()) {
			return callUserObserved(observationName, user);
		}
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(outputType)
			.maxRepeatAttempts(2)
			.build();
		Flux<ChatResponse> response = Mono
			.fromCallable(() -> registry.getChatClient().prompt().user(user).advisors(advisor).call().chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux()
			.onErrorResume(RestClientException.class, ex -> {
				structuredSyncCompatible.set(false);
				log.warn(
						"Structured synchronous response is incompatible with the current model endpoint; "
								+ "using streaming output for this and subsequent structured calls: {}",
						summarizeExceptionChain(ex));
				return callUserObserved(observationName, user);
			});
		return trace(observationName, "user:\n" + user, response);
	}

	private Flux<ChatResponse> trace(String name, String input, Flux<ChatResponse> response) {
		return trace(name, input, response, defaultMaxTokens());
	}

	private Flux<ChatResponse> trace(String name, String input, Flux<ChatResponse> response, Integer maxTokens) {
		return langfuseService == null ? response : langfuseService.traceModelStream(name, input, response, maxTokens);
	}

	private Integer defaultMaxTokens() {
		var defaultOptions = registry.getChatModel().getDefaultOptions();
		return defaultOptions == null ? null : defaultOptions.getMaxTokens();
	}

	private String formatInput(String system, String user) {
		return "system:\n" + system + "\n\nuser:\n" + user;
	}

	private String summarizeExceptionChain(Throwable error) {
		StringBuilder summary = new StringBuilder();
		Throwable current = error;
		while (current != null) {
			if (!summary.isEmpty()) {
				summary.append(" -> ");
			}
			summary.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage());
			current = current.getCause();
		}
		return summary.toString();
	}

}
