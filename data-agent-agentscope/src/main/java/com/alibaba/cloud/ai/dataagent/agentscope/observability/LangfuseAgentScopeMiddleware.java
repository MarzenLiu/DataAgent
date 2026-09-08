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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolChoice;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class LangfuseAgentScopeMiddleware implements MiddlewareBase {

	private final ObjectMapper objectMapper;

	public LangfuseAgentScopeMiddleware(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext runtimeContext, AgentInput input,
			Function<AgentInput, Flux<AgentEvent>> next) {
		return Flux.deferContextual(contextView -> {
			Span span = currentSpan(contextView);
			if (!span.isRecording()) {
				return next.apply(input);
			}
			span.setAttribute("langfuse.observation.type", "agent");
			span.setAttribute("langfuse.observation.input", json(input.msgs()));
			return next.apply(input).doOnNext(event -> {
				if (event instanceof AgentResultEvent result && result.getResult() != null) {
					span.setAttribute("langfuse.observation.output", json(result.getResult()));
				}
			});
		});
	}

	@Override
	public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext runtimeContext, ModelCallInput input,
			Function<ModelCallInput, Flux<AgentEvent>> next) {
		return Flux.deferContextual(contextView -> {
			Span span = currentSpan(contextView);
			if (!span.isRecording()) {
				return next.apply(input);
			}
			StringBuilder output = new StringBuilder();
			span.setAttribute("langfuse.observation.type", "generation");
			span.setAttribute("langfuse.observation.input", json(modelRequestSnapshot(input)));
			return next.apply(input).doOnNext(event -> {
				if (event instanceof TextBlockDeltaEvent delta && delta.getDelta() != null) {
					output.append(delta.getDelta());
					span.setAttribute("langfuse.observation.output", json(output.toString()));
				}
			});
		});
	}

	@Override
	public Flux<AgentEvent> onActing(Agent agent, RuntimeContext runtimeContext, ActingInput input,
			Function<ActingInput, Flux<AgentEvent>> next) {
		return Flux.deferContextual(contextView -> {
			Span span = currentSpan(contextView);
			if (!span.isRecording()) {
				return next.apply(input);
			}
			StringBuilder output = new StringBuilder();
			span.setAttribute("langfuse.observation.type", "tool");
			span.setAttribute("langfuse.observation.input", json(input.toolCalls()));
			return next.apply(input).doOnNext(event -> {
				if (event instanceof ToolResultTextDeltaEvent delta && delta.getDelta() != null) {
					output.append(delta.getDelta());
					span.setAttribute("langfuse.observation.output", json(output.toString()));
				}
				if (event instanceof ToolResultEndEvent end && end.getState() != null) {
					span.setAttribute("langfuse.observation.metadata.result_state", end.getState().name());
				}
			});
		});
	}

	private Map<String, Object> modelRequestSnapshot(ModelCallInput input) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("messages", input.messages() != null ? input.messages() : List.of());
		snapshot.put("tools", input.tools() != null ? input.tools() : List.of());
		snapshot.put("options", safeOptions(input.options()));
		return snapshot;
	}

	private Map<String, Object> safeOptions(GenerateOptions options) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (options == null) {
			return result;
		}
		putIfNotNull(result, "modelName", options.getModelName());
		putIfNotNull(result, "stream", options.getStream());
		putIfNotNull(result, "temperature", options.getTemperature());
		putIfNotNull(result, "topP", options.getTopP());
		putIfNotNull(result, "maxTokens", options.getMaxTokens());
		putIfNotNull(result, "maxCompletionTokens", options.getMaxCompletionTokens());
		putIfNotNull(result, "frequencyPenalty", options.getFrequencyPenalty());
		putIfNotNull(result, "presencePenalty", options.getPresencePenalty());
		putIfNotNull(result, "thinkingBudget", options.getThinkingBudget());
		putIfNotNull(result, "reasoningEffort", options.getReasoningEffort());
		putIfNotNull(result, "toolChoice", toolChoice(options.getToolChoice()));
		putIfNotNull(result, "topK", options.getTopK());
		putIfNotNull(result, "seed", options.getSeed());
		putIfNotNull(result, "cacheControl", options.getCacheControl());
		putIfNotNull(result, "parallelToolCalls", options.getParallelToolCalls());
		putIfNotNull(result, "responseFormat", options.getResponseFormat());
		return result;
	}

	private Object toolChoice(ToolChoice toolChoice) {
		if (toolChoice == null) {
			return null;
		}
		if (toolChoice instanceof ToolChoice.Specific specific) {
			return Map.of("type", "specific", "toolName", specific.toolName());
		}
		return toolChoice.getClass().getSimpleName().toLowerCase(Locale.ROOT);
	}

	private void putIfNotNull(Map<String, Object> values, String name, Object value) {
		if (value != null) {
			values.put(name, value);
		}
	}

	private Span currentSpan(reactor.util.context.ContextView contextView) {
		Context context = ContextPropagationOperator.getOpenTelemetryContextFromContextView(contextView,
				Context.current());
		return Span.fromContext(context);
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
