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
package com.alibaba.cloud.ai.dataagent.service.llm;

import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

public interface LlmService {

	Flux<ChatResponse> call(String system, String user);

	default Flux<ChatResponse> callObserved(String observationName, String system, String user) {
		return call(system, user);
	}

	/**
	 * Call the model with system and user messages plus Spring AI structured-output
	 * validation.
	 */
	Flux<ChatResponse> call(String system, String user, Class<?> outputType);

	default Flux<ChatResponse> callObserved(String observationName, String system, String user, Class<?> outputType) {
		return call(system, user, outputType);
	}

	Flux<ChatResponse> callSystem(String system);

	default Flux<ChatResponse> callSystemObserved(String observationName, String system) {
		return callSystem(system);
	}

	Flux<ChatResponse> callUser(String user);

	default Flux<ChatResponse> callUserObserved(String observationName, String user) {
		return callUser(user);
	}

	/**
	 * Call the model with a request-specific completion-token budget.
	 */
	default Flux<ChatResponse> callUserObservedWithMaxTokens(String observationName, String user, Integer maxTokens) {
		return callUserObserved(observationName, user);
	}

	/**
	 * Call the model with Spring AI's structured-output validation advisor. The advisor
	 * validates the response against the schema derived from {@code outputType} and asks
	 * the model to repair invalid output.
	 */
	Flux<ChatResponse> callUser(String user, Class<?> outputType);

	default Flux<ChatResponse> callUserObserved(String observationName, String user, Class<?> outputType) {
		return callUser(user, outputType);
	}

	default Flux<String> toStringFlux(Flux<ChatResponse> responseFlux) {
		return responseFlux.map(ChatResponseUtil::getText);
	}

}
