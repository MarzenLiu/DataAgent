/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.agentscope.observability;

import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphEventType;
import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

@Component
public class LangfuseTraceService {

	private static final String TRACE_NAME = "data-agent.stream-search";

	private final LangfuseTelemetry telemetry;

	private final ObjectMapper objectMapper;

	public LangfuseTraceService(LangfuseTelemetry telemetry, ObjectMapper objectMapper) {
		this.telemetry = telemetry;
		this.objectMapper = objectMapper;
	}

	public Flux<ServerSentEvent<GraphNodeResponse>> trace(GraphRequest request, String agentId, String conversationId,
			String threadId, boolean hitl, Flux<ServerSentEvent<GraphNodeResponse>> source) {
		if (!telemetry.isEnabled()) {
			return source;
		}
		return Flux.defer(() -> {
			Span span = telemetry.getTracer()
				.spanBuilder(TRACE_NAME)
				.setSpanKind(SpanKind.SERVER)
				.setAttribute("langfuse.observation.type", "agent")
				.setAttribute(LangfuseBaggageSpanProcessor.USER_ID, agentId)
				.setAttribute(LangfuseBaggageSpanProcessor.SESSION_ID, conversationId)
				.setAttribute(LangfuseBaggageSpanProcessor.TRACE_NAME, TRACE_NAME)
				.setAttribute(LangfuseBaggageSpanProcessor.AGENT_ID, agentId)
				.setAttribute(LangfuseBaggageSpanProcessor.THREAD_ID, threadId)
				.setAttribute("langfuse.observation.input", requestJson(request, conversationId, threadId))
				.setAttribute("langfuse.observation.metadata.phase", hitl ? "hitl" : "execution")
				.startSpan();
			AtomicBoolean ended = new AtomicBoolean();
			AtomicReference<Throwable> failure = new AtomicReference<>();
			AtomicReference<String> finalOutput = new AtomicReference<>("");
			StringBuilder streamedOutput = new StringBuilder();
			Context parent = Context.current();
			Baggage baggage = Baggage.fromContext(parent)
				.toBuilder()
				.put(LangfuseBaggageSpanProcessor.USER_ID, agentId)
				.put(LangfuseBaggageSpanProcessor.SESSION_ID, conversationId)
				.put(LangfuseBaggageSpanProcessor.TRACE_NAME, TRACE_NAME)
				.put(LangfuseBaggageSpanProcessor.AGENT_ID, agentId)
				.put(LangfuseBaggageSpanProcessor.THREAD_ID, threadId)
				.build();
			Context traceContext = baggage.storeInContext(span.storeInContext(parent));
			Flux<ServerSentEvent<GraphNodeResponse>> observed = source.doOnNext(event -> {
				GraphNodeResponse data = event.data();
				if (data == null) {
					return;
				}
				if (data.error()) {
					failure.compareAndSet(null, new IllegalStateException(data.text()));
				}
				if (data.eventType() == GraphEventType.FINAL_ANSWER) {
					finalOutput.set(data.text());
				}
				else if (data.text() != null && !data.text().isBlank()) {
					streamedOutput.append(data.text());
				}
			}).doOnError(error -> failure.compareAndSet(null, error))
				.doFinally(signal -> finish(span, ended, failure.get(), signal, finalOutput.get(), streamedOutput));
			return ContextPropagationOperator.runWithContext(observed, traceContext);
		});
	}

	private void finish(Span span, AtomicBoolean ended, Throwable failure, SignalType signal, String finalOutput,
			StringBuilder streamedOutput) {
		if (!ended.compareAndSet(false, true)) {
			return;
		}
		String output = finalOutput == null || finalOutput.isBlank() ? streamedOutput.toString() : finalOutput;
		span.setAttribute("langfuse.observation.output", json(output));
		if (failure != null) {
			span.recordException(failure);
			span.setStatus(StatusCode.ERROR, failure.getMessage());
		}
		else if (signal == SignalType.CANCEL) {
			span.setStatus(StatusCode.ERROR, "cancelled");
		}
		else {
			span.setStatus(StatusCode.OK);
		}
		span.end();
	}

	private String requestJson(GraphRequest request, String conversationId, String threadId) {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("agentId", request.agentId());
		input.put("conversationId", conversationId);
		input.put("threadId", threadId);
		input.put("query", request.query());
		input.put("humanFeedback", request.humanFeedback());
		input.put("humanFeedbackContent", request.humanFeedbackContent());
		input.put("rejectedPlan", request.rejectedPlan());
		input.put("nl2sqlOnly", request.nl2sqlOnly());
		return json(input);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			return String.valueOf(value);
		}
	}

}
