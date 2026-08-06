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
package com.alibaba.cloud.ai.dataagent.service.langfuse;

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LangfuseServiceTest {

	@Mock
	private Tracer tracer;

	@Mock
	private SpanBuilder spanBuilder;

	@Mock
	private Span span;

	private LangfuseService langfuseService;

	@BeforeEach
	void setUp() {
		langfuseService = new LangfuseService(tracer, true);
	}

	@Test
	void startLLMSpan_disabled_returnsInvalidSpan() {
		LangfuseService disabledService = new LangfuseService(tracer, false);
		GraphRequest request = new GraphRequest();
		request.setQuery("test");

		Span result = disabledService.startLLMSpan("test-span", request);

		assertFalse(result.isRecording());
	}

	@Test
	void startLLMSpan_enabled_returnsSpan() {
		GraphRequest request = new GraphRequest();
		request.setQuery("test query");
		request.setAgentId("agent-1");
		request.setThreadId("thread-1");

		when(tracer.spanBuilder("test-span")).thenReturn(spanBuilder);
		when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
		when(spanBuilder.setParent(any())).thenReturn(spanBuilder);
		when(spanBuilder.startSpan()).thenReturn(span);

		Span result = langfuseService.startLLMSpan("test-span", request);

		assertNotNull(result);
		assertEquals(span, result);
		verify(tracer).spanBuilder("test-span");
	}

	@Test
	void startLLMSpan_exceptionInTracer_returnsInvalidSpan() {
		GraphRequest request = new GraphRequest();
		when(tracer.spanBuilder(anyString())).thenThrow(new RuntimeException("tracer error"));

		Span result = langfuseService.startLLMSpan("test", request);
		assertFalse(result.isRecording());
	}

	@Test
	void accumulateTokens_nullThreadId_doesNothing() {
		LangfuseService.accumulateTokens(null, 10, 20);
	}

	@Test
	void accumulateTokens_validThreadId_accumulatesTokens() {
		GraphRequest request = new GraphRequest();
		request.setThreadId("token-thread");
		request.setQuery("q");

		when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
		when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
		when(spanBuilder.setParent(any())).thenReturn(spanBuilder);
		when(spanBuilder.startSpan()).thenReturn(span);

		langfuseService.startLLMSpan("span", request);

		LangfuseService.accumulateTokens("token-thread", 100, 200);
		LangfuseService.accumulateTokens("token-thread", 50, 100);
	}

	@Test
	void endSpanSuccess_disabled_doesNothing() {
		LangfuseService disabledService = new LangfuseService(tracer, false);
		disabledService.endSpanSuccess(span, "thread", "output");
		verify(span, never()).end();
	}

	@Test
	void endSpanSuccess_nullSpan_doesNothing() {
		langfuseService.endSpanSuccess(null, "thread", "output");
	}

	@Test
	void endSpanSuccess_validSpan_endsSpan() {
		when(span.isRecording()).thenReturn(true);

		langfuseService.endSpanSuccess(span, "thread-end", "test output");

		verify(span).setStatus(any());
		verify(span).end();
	}

	@Test
	void endSpanError_disabled_doesNothing() {
		LangfuseService disabledService = new LangfuseService(tracer, false);
		disabledService.endSpanError(span, "thread", new RuntimeException("err"));
		verify(span, never()).end();
	}

	@Test
	void endSpanError_validSpan_recordsException() {
		when(span.isRecording()).thenReturn(true);
		RuntimeException error = new RuntimeException("test error");

		langfuseService.endSpanError(span, "thread-err", error);

		verify(span).recordException(error);
		verify(span).end();
	}

	@Test
	void endSpanError_nullErrorMessage_handlesGracefully() {
		when(span.isRecording()).thenReturn(true);
		RuntimeException error = new RuntimeException((String) null);

		langfuseService.endSpanError(span, "thread-err", error);

		verify(span).end();
	}

	@Test
	void traceModelStream_recordsFinishReasonReasoningTokensAndTokenLimit() {
		when(tracer.spanBuilder("token-limited")).thenReturn(spanBuilder);
		when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
		when(spanBuilder.setParent(any())).thenReturn(spanBuilder);
		when(spanBuilder.startSpan()).thenReturn(span);
		OpenAiApi.Usage.CompletionTokenDetails details = new OpenAiApi.Usage.CompletionTokenDetails(5900, null, null,
				null);
		OpenAiApi.Usage nativeUsage = new OpenAiApi.Usage(6000, 100, 6100, null, details);
		DefaultUsage usage = new DefaultUsage(100, 6000, 6100, nativeUsage);
		Generation generation = new Generation(new AssistantMessage("{}"),
				ChatGenerationMetadata.builder().finishReason("length").build());
		ChatResponse response = new ChatResponse(List.of(generation),
				ChatResponseMetadata.builder().usage(usage).build());

		langfuseService.traceModelStream("token-limited", "input", Flux.just(response), 6000).blockLast();

		verify(span).setAttribute(AttributeKey.stringKey("gen_ai.response.finish_reason"), "length");
		verify(span).setAttribute(AttributeKey.longKey("gen_ai.usage.reasoning_tokens"), 5900L);
		verify(span).setAttribute(AttributeKey.longKey("gen_ai.request.max_tokens"), 6000L);
		verify(span).setAttribute(AttributeKey.booleanKey("data_agent.token_limit_reached"), true);
		verify(span).setAttribute(AttributeKey.booleanKey("data_agent.output_empty"), true);
	}

}
