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
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author zihenzzz
 * @date 2026/2/16 13:54 基于 OpenTelemetry 的 Langfuse Reporter，用于追踪 LLM 调用
 */
@Slf4j
@Component
public class LangfuseService {

	private final Tracer tracer;

	private final boolean enabled;

	// --- Span Attribute Keys ---
	private static final AttributeKey<String> INPUT_VALUE = AttributeKey.stringKey("input.value");

	private static final AttributeKey<String> OUTPUT_VALUE = AttributeKey.stringKey("output.value");

	private static final AttributeKey<String> ATTR_AGENT_ID = AttributeKey.stringKey("data_agent.agent_id");

	private static final AttributeKey<String> ATTR_THREAD_ID = AttributeKey.stringKey("data_agent.thread_id");

	private static final AttributeKey<Boolean> ATTR_NL2SQL_ONLY = AttributeKey.booleanKey("data_agent.nl2sql_only");

	private static final AttributeKey<Boolean> ATTR_HUMAN_FEEDBACK = AttributeKey
		.booleanKey("data_agent.human_feedback");

	private static final AttributeKey<Long> GEN_AI_PROMPT_TOKENS = AttributeKey.longKey("gen_ai.usage.prompt_tokens");

	private static final AttributeKey<Long> GEN_AI_COMPLETION_TOKENS = AttributeKey
		.longKey("gen_ai.usage.completion_tokens");

	private static final AttributeKey<Long> GEN_AI_TOTAL_TOKENS = AttributeKey.longKey("gen_ai.usage.total_tokens");

	private static final AttributeKey<Long> GEN_AI_REASONING_TOKENS = AttributeKey
		.longKey("gen_ai.usage.reasoning_tokens");

	private static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS = AttributeKey
		.longKey("gen_ai.request.max_tokens");

	private static final AttributeKey<String> GEN_AI_FINISH_REASON = AttributeKey
		.stringKey("gen_ai.response.finish_reason");

	private static final AttributeKey<Boolean> TOKEN_LIMIT_REACHED = AttributeKey
		.booleanKey("data_agent.token_limit_reached");

	private static final AttributeKey<Boolean> OUTPUT_EMPTY = AttributeKey.booleanKey("data_agent.output_empty");

	private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

	private static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("error.message");

	private static final AttributeKey<String> OBSERVATION_TYPE = AttributeKey
		.stringKey("langfuse.observation.type");

	// --- Token 累计器，按 threadId 隔离 ---
	private static final ConcurrentHashMap<String, long[]> TOKEN_ACCUMULATOR = new ConcurrentHashMap<>();

	public LangfuseService(Tracer langfuseTracer,
			@Value("${spring.ai.alibaba.data-agent.langfuse.enabled:false}") boolean enabled) {
		this.tracer = langfuseTracer;
		this.enabled = enabled;
	}

	/**
	 * 开始一个 Graph 流式处理的 Span，记录完整的请求上下文
	 */
	public Span startLLMSpan(String spanName, GraphRequest request) {
		if (!enabled) {
			return Span.getInvalid();
		}

		try {
			Span span = tracer.spanBuilder(spanName)
				.setSpanKind(SpanKind.CLIENT)
				.setParent(Context.current())
				.startSpan();

			String inputValue = String.format(
					"{\"query\":\"%s\",\"agentId\":\"%s\",\"threadId\":\"%s\",\"nl2sqlOnly\":%s,\"humanFeedback\":%s}",
					request.getQuery() != null ? request.getQuery() : "",
					request.getAgentId() != null ? request.getAgentId() : "",
					request.getThreadId() != null ? request.getThreadId() : "", request.isNl2sqlOnly(),
					request.isHumanFeedback());
			span.setAttribute(INPUT_VALUE, inputValue);
			span.setAttribute(ATTR_AGENT_ID, request.getAgentId() != null ? request.getAgentId() : "");
			span.setAttribute(ATTR_THREAD_ID, request.getThreadId() != null ? request.getThreadId() : "");
			span.setAttribute(ATTR_NL2SQL_ONLY, request.isNl2sqlOnly());
			span.setAttribute(ATTR_HUMAN_FEEDBACK, request.isHumanFeedback());

			// 初始化该 threadId 的 token 累计器
			if (request.getThreadId() != null) {
				TOKEN_ACCUMULATOR.put(request.getThreadId(), new long[] { 0, 0 });
			}

			return span;
		}
		catch (Exception e) {
			log.error("Failed to start OTel span", e);
			return Span.getInvalid();
		}
	}

	/**
	 * 累计 token 用量（由 FluxUtil 在处理 ChatResponse 时调用）
	 */
	public static void accumulateTokens(Object threadId, long promptTokens, long completionTokens) {
		if (threadId == null) {
			return;
		}
		long[] tokens = TOKEN_ACCUMULATOR.get(threadId);
		if (tokens != null) {
			synchronized (tokens) {
				tokens[0] += promptTokens;
				tokens[1] += completionTokens;
			}
		}
	}

	public ChatResponse traceModelCall(String name, String input,
			java.util.function.Supplier<ChatResponse> invocation) {
		if (!enabled) {
			return invocation.get();
		}
		Span span = startModelSpan(name, input);
		try {
			ChatResponse response = invocation.get();
			ModelTraceStats stats = new ModelTraceStats(null);
			stats.accept(response);
			finishModelSpan(span, response == null ? "" : ChatResponseUtil.getText(response), stats, null);
			return response;
		}
		catch (RuntimeException error) {
			finishModelSpan(span, "", new ModelTraceStats(null), error);
			throw error;
		}
	}

	public Flux<ChatResponse> traceModelStream(String name, String input, Flux<ChatResponse> responses) {
		return traceModelStream(name, input, responses, null);
	}

	public Flux<ChatResponse> traceModelStream(String name, String input, Flux<ChatResponse> responses,
			Integer configuredMaxTokens) {
		if (!enabled) {
			return responses;
		}
		return Flux.defer(() -> {
			Span span = startModelSpan(name, input);
			StringBuilder output = new StringBuilder();
			ModelTraceStats stats = new ModelTraceStats(configuredMaxTokens);
			AtomicBoolean failed = new AtomicBoolean();
			return responses.doOnNext(response -> {
				output.append(ChatResponseUtil.getText(response));
				stats.accept(response);
			}).doOnError(error -> {
				failed.set(true);
				finishModelSpan(span, output.toString(), stats, error);
			}).doOnComplete(() -> finishModelSpan(span, output.toString(), stats, null))
				.doOnCancel(() -> {
					if (failed.compareAndSet(false, true)) {
						finishModelSpan(span, output.toString(), stats,
								new IllegalStateException("Model stream cancelled"));
					}
				});
		});
	}

	private Span startModelSpan(String name, String input) {
		Span span = tracer.spanBuilder(name).setSpanKind(SpanKind.CLIENT).setParent(Context.current()).startSpan();
		span.setAttribute(OBSERVATION_TYPE, "generation");
		span.setAttribute(INPUT_VALUE, input != null ? input : "");
		return span;
	}

	private void finishModelSpan(Span span, String output, ModelTraceStats stats, Throwable error) {
		span.setAttribute(OUTPUT_VALUE, output != null ? output : "");
		span.setAttribute(GEN_AI_PROMPT_TOKENS, stats.promptTokens);
		span.setAttribute(GEN_AI_COMPLETION_TOKENS, stats.completionTokens);
		span.setAttribute(GEN_AI_TOTAL_TOKENS, stats.promptTokens + stats.completionTokens);
		span.setAttribute(GEN_AI_REASONING_TOKENS, stats.reasoningTokens);
		if (stats.configuredMaxTokens != null) {
			span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, stats.configuredMaxTokens.longValue());
		}
		if (stats.finishReason != null) {
			span.setAttribute(GEN_AI_FINISH_REASON, stats.finishReason);
		}
		span.setAttribute(TOKEN_LIMIT_REACHED, stats.tokenLimitReached());
		span.setAttribute(OUTPUT_EMPTY, isEmptyModelOutput(output));
		if (error == null) {
			span.setStatus(StatusCode.OK);
		}
		else {
			span.setAttribute(ERROR_TYPE, error.getClass().getName());
			span.setAttribute(ERROR_MESSAGE, String.valueOf(error.getMessage()));
			span.recordException(error);
			span.setStatus(StatusCode.ERROR);
		}
		span.end();
	}

	private boolean isEmptyModelOutput(String output) {
		if (output == null || output.isBlank()) {
			return true;
		}
		String normalized = output.trim();
		return "{}".equals(normalized) || "null".equalsIgnoreCase(normalized);
	}

	private static final class ModelTraceStats {

		private final Integer configuredMaxTokens;

		private long promptTokens;

		private long completionTokens;

		private long reasoningTokens;

		private String finishReason;

		private ModelTraceStats(Integer configuredMaxTokens) {
			this.configuredMaxTokens = configuredMaxTokens;
		}

		private void accept(ChatResponse response) {
			if (response == null) {
				return;
			}
			if (response.getResult() != null && response.getResult().getMetadata() != null) {
				String currentFinishReason = response.getResult().getMetadata().getFinishReason();
				if (currentFinishReason != null && !currentFinishReason.isBlank()) {
					finishReason = currentFinishReason;
				}
			}
			if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
				return;
			}
			var usage = response.getMetadata().getUsage();
			promptTokens = Math.max(promptTokens, usage.getPromptTokens());
			completionTokens = Math.max(completionTokens, usage.getCompletionTokens());
			if (usage.getNativeUsage() instanceof OpenAiApi.Usage nativeUsage
					&& nativeUsage.completionTokenDetails() != null
					&& nativeUsage.completionTokenDetails().reasoningTokens() != null) {
				reasoningTokens = Math.max(reasoningTokens,
						nativeUsage.completionTokenDetails().reasoningTokens());
			}
		}

		private boolean tokenLimitReached() {
			if ("length".equalsIgnoreCase(finishReason)) {
				return true;
			}
			return configuredMaxTokens != null && configuredMaxTokens > 0
					&& completionTokens >= Math.ceil(configuredMaxTokens * 0.98D);
		}

	}

	/**
	 * 结束 Span（成功），附带累计的 token 用量
	 */
	public void endSpanSuccess(Span span, String threadId, String output) {
		if (!enabled || span == null || !span.isRecording()) {
			return;
		}

		try {
			span.setAttribute(OUTPUT_VALUE, output != null ? output : "");
			applyAccumulatedTokens(span, threadId);
			span.setStatus(StatusCode.OK);
		}
		catch (Exception e) {
			log.error("Failed to end OTel span", e);
		}
		finally {
			span.end();
		}
	}

	/**
	 * 结束 Span（失败）
	 */
	public void endSpanError(Span span, String threadId, Exception error) {
		if (!enabled || span == null || !span.isRecording()) {
			return;
		}

		try {
			String errorType = error.getClass().getSimpleName();
			String errorMessage = error.getMessage() != null ? error.getMessage() : "";

			span.setAttribute(ERROR_TYPE, errorType);
			span.setAttribute(ERROR_MESSAGE, errorMessage);
			applyAccumulatedTokens(span, threadId);
			span.setStatus(StatusCode.ERROR, errorType + ": " + errorMessage);
			span.recordException(error);
		}
		catch (Exception e) {
			log.error("Failed to record span error", e);
		}
		finally {
			span.end();
		}
	}

	/**
	 * 读取并清除累计的 token，写入 span attributes
	 */
	private void applyAccumulatedTokens(Span span, String threadId) {
		if (threadId == null) {
			return;
		}
		long[] tokens = TOKEN_ACCUMULATOR.remove(threadId);
		if (tokens != null) {
			synchronized (tokens) {
				if (tokens[0] > 0 || tokens[1] > 0) {
					span.setAttribute(GEN_AI_PROMPT_TOKENS, tokens[0]);
					span.setAttribute(GEN_AI_COMPLETION_TOKENS, tokens[1]);
					span.setAttribute(GEN_AI_TOTAL_TOKENS, tokens[0] + tokens[1]);
				}
			}
		}
	}

}
