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

import com.alibaba.cloud.ai.dataagent.dto.prompt.FeasibilityAssessmentOutputDTO;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.StreamLlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.web.client.RestClientException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StreamLlmServiceTest {

	@Mock
	private AiModelRegistry registry;

	@Mock
	private ChatClient chatClient;

	@Mock
	private ChatModel chatModel;

	@Mock
	private ChatOptions chatOptions;

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.StreamResponseSpec streamResponseSpec;

	@Mock
	private ChatClient.CallResponseSpec callResponseSpec;

	@Mock
	private LangfuseService langfuseService;

	private StreamLlmService streamLlmService;

	private ChatResponse mockResponse;

	@BeforeEach
	void setUp() {
		when(registry.getChatClient()).thenReturn(chatClient);
		when(registry.getChatModel()).thenReturn(chatModel);
		when(chatModel.getDefaultOptions()).thenReturn(chatOptions);
		when(chatOptions.getMaxTokens()).thenReturn(6000);
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.system(anyString())).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.advisors(any(Advisor[].class))).thenReturn(requestSpec);
		when(requestSpec.options(any(ChatOptions.class))).thenReturn(requestSpec);
		when(requestSpec.stream()).thenReturn(streamResponseSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);

		mockResponse = ChatResponseUtil.createPureResponse("streamed output");
		when(streamResponseSpec.chatResponse()).thenReturn(Flux.just(mockResponse));
		when(callResponseSpec.chatResponse()).thenReturn(mockResponse);
		when(langfuseService.traceModelStream(anyString(), anyString(), any(), any()))
			.thenAnswer(invocation -> invocation.getArgument(2));

		streamLlmService = new StreamLlmService(registry, langfuseService);
	}

	@Test
	void callUserObserved_usesExplicitObservationName() {
		Flux<ChatResponse> result = streamLlmService.callUserObserved("user-profile.extract-profile", "Hello");

		StepVerifier.create(result).expectNext(mockResponse).verifyComplete();
		verify(langfuseService).traceModelStream("user-profile.extract-profile", "user:\nHello", result, 6000);
	}

	@Test
	void callUserObservedWithMaxTokens_reportsRequestSpecificBudget() {
		Flux<ChatResponse> result = streamLlmService.callUserObservedWithMaxTokens("discovery", "Hello", 8000);

		StepVerifier.create(result).expectNext(mockResponse).verifyComplete();
		verify(requestSpec).options(any(ChatOptions.class));
		verify(langfuseService).traceModelStream("discovery", "user:\nHello", result, 8000);
	}

	@Test
	void callUser_validPrompt_returnsStreamFlux() {
		Flux<ChatResponse> result = streamLlmService.callUser("Hello");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void callSystem_validPrompt_returnsStreamFlux() {
		Flux<ChatResponse> result = streamLlmService.callSystem("System prompt");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void call_validPrompts_returnsStreamFlux() {
		Flux<ChatResponse> result = streamLlmService.call("system", "user");

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
	}

	@Test
	void callUser_structuredOutput_usesBlockingAdvisorBecauseValidationDoesNotSupportStreaming() {
		Flux<ChatResponse> result = streamLlmService.callUser("Hello", FeasibilityAssessmentOutputDTO.class);

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
		verify(requestSpec).advisors(any(Advisor[].class));
		verify(requestSpec).call();
		verify(requestSpec, never()).stream();
	}

	@Test
	void call_structuredOutput_preservesSystemRoleAndUsesValidationAdvisor() {
		Flux<ChatResponse> result = streamLlmService.call("system", "user", FeasibilityAssessmentOutputDTO.class);

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
		verify(requestSpec).system("system");
		verify(requestSpec).user("user");
		verify(requestSpec).advisors(any(Advisor[].class));
		verify(requestSpec).call();
	}

	@Test
	void callUser_structuredOutputIncompatibleResponse_fallsBackToStreaming() {
		when(callResponseSpec.chatResponse()).thenThrow(new RestClientException("incompatible response"));

		Flux<ChatResponse> firstResult = streamLlmService.callUser("Hello", FeasibilityAssessmentOutputDTO.class);

		StepVerifier.create(firstResult)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();

		Flux<ChatResponse> secondResult = streamLlmService.callUser("Hello again",
				FeasibilityAssessmentOutputDTO.class);
		StepVerifier.create(secondResult)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();

		verify(requestSpec).call();
		verify(requestSpec, times(2)).stream();
	}

	@Test
	void call_structuredOutputIncompatibleResponse_fallsBackToStreamingWithRoles() {
		when(callResponseSpec.chatResponse()).thenThrow(new RestClientException("incompatible response"));

		Flux<ChatResponse> result = streamLlmService.call("system", "user", FeasibilityAssessmentOutputDTO.class);

		StepVerifier.create(result)
			.expectNextMatches(r -> ChatResponseUtil.getText(r).equals("streamed output"))
			.verifyComplete();
		verify(requestSpec, times(2)).system("system");
		verify(requestSpec, times(2)).user("user");
		verify(requestSpec).stream();
	}

}
