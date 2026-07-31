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
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.client.RestClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@Slf4j
public class StreamLlmService implements LlmService {

	private final AiModelRegistry registry;

	private final AtomicBoolean structuredSyncCompatible = new AtomicBoolean(true);

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return registry.getChatClient().prompt().system(system).user(user).stream().chatResponse();
	}

	@Override
	public Flux<ChatResponse> call(String system, String user, Class<?> outputType) {
		if (!structuredSyncCompatible.get()) {
			return call(system, user);
		}
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(outputType)
			.maxRepeatAttempts(2)
			.build();
		return Mono
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
				return call(system, user);
			});
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return registry.getChatClient().prompt().system(system).stream().chatResponse();
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		return registry.getChatClient().prompt().user(user).stream().chatResponse();
	}

	@Override
	public Flux<ChatResponse> callUser(String user, Class<?> outputType) {
		if (!structuredSyncCompatible.get()) {
			return callUser(user);
		}
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(outputType)
			.maxRepeatAttempts(2)
			.build();
		return Mono
			.fromCallable(() -> registry.getChatClient().prompt().user(user).advisors(advisor).call().chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux()
			.onErrorResume(RestClientException.class, ex -> {
				structuredSyncCompatible.set(false);
				log.warn(
						"Structured synchronous response is incompatible with the current model endpoint; "
								+ "using streaming output for this and subsequent structured calls: {}",
						summarizeExceptionChain(ex));
				return callUser(user);
			});
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
