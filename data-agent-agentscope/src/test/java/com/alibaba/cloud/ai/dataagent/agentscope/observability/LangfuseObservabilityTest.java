package com.alibaba.cloud.ai.dataagent.agentscope.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.Model;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class LangfuseObservabilityTest {

	@Test
	void normalizesLangfuseTraceEndpoint() {
		assertThat(LangfuseTelemetry.traceEndpoint("http://localhost:13000/"))
			.isEqualTo("http://localhost:13000/api/public/otel/v1/traces");
		assertThat(LangfuseTelemetry.traceEndpoint("http://localhost:13000/api/public/otel"))
			.isEqualTo("http://localhost:13000/api/public/otel/v1/traces");
	}

	@Test
	void propagatesLangfuseBaggageToChildSpans() {
		CollectingExporter exporter = new CollectingExporter();
		try (SdkTracerProvider provider = SdkTracerProvider.builder()
			.addSpanProcessor(new LangfuseBaggageSpanProcessor())
			.addSpanProcessor(SimpleSpanProcessor.create(exporter))
			.build()) {
			Tracer tracer = OpenTelemetrySdk.builder().setTracerProvider(provider).build().getTracer("test");
			Context parent = Baggage.builder()
				.put(LangfuseBaggageSpanProcessor.SESSION_ID, "conversation-1")
				.put(LangfuseBaggageSpanProcessor.USER_ID, "7")
				.build()
				.storeInContext(Context.root());
			tracer.spanBuilder("child").setParent(parent).startSpan().end();
			SpanData span = exporter.spans.get(0);
			assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseBaggageSpanProcessor.SESSION_ID)))
				.isEqualTo("conversation-1");
			assertThat(span.getAttributes().get(AttributeKey.stringKey(LangfuseBaggageSpanProcessor.USER_ID)))
				.isEqualTo("7");
		}
	}

	@Test
	void exporterKeepsOnlyDataAgentAndAgentScopeSpans() {
		CollectingExporter exporter = new CollectingExporter();
		try (SdkTracerProvider provider = SdkTracerProvider.builder()
			.addSpanProcessor(SimpleSpanProcessor.create(new AgentScopeSpanExporter(exporter)))
			.build()) {
			OpenTelemetrySdk telemetry = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
			telemetry.getTracer("io.agentscope").spanBuilder("agent").startSpan().end();
			telemetry.getTracer("com.mysql.cj").spanBuilder("jdbc").startSpan().end();
			assertThat(exporter.spans).extracting(SpanData::getName).containsExactly("agent");
		}
	}

	@Test
	void enrichesAgentScopeModelSpanWithInputAndOutput() {
		CollectingExporter exporter = new CollectingExporter();
		try (SdkTracerProvider provider = SdkTracerProvider.builder()
			.addSpanProcessor(SimpleSpanProcessor.create(exporter))
			.build()) {
			Tracer tracer = OpenTelemetrySdk.builder().setTracerProvider(provider).build().getTracer("test");
			Span span = tracer.spanBuilder("chat model").startSpan();
			LangfuseAgentScopeMiddleware middleware = new LangfuseAgentScopeMiddleware(new ObjectMapper());
			Agent agent = mock(Agent.class);
			Model model = mock(Model.class);
			ModelCallInput input = new ModelCallInput(List.of(new UserMessage("hello")), List.of(), null, model);
			try (Scope ignored = span.makeCurrent()) {
				middleware.onModelCall(agent, RuntimeContext.builder().build(), input,
						ignoredInput -> Flux.<AgentEvent>just(new TextBlockDeltaEvent("reply", "block", "answer")))
					.blockLast();
			}
			span.end();
			SpanData data = exporter.spans.get(0);
			assertThat(data.getAttributes().get(AttributeKey.stringKey("langfuse.observation.type")))
				.isEqualTo("generation");
			assertThat(data.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")))
				.contains("hello");
			assertThat(data.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")))
				.contains("answer");
		}
	}

	private static final class CollectingExporter implements SpanExporter {

		private final List<SpanData> spans = new ArrayList<>();

		@Override
		public CompletableResultCode export(Collection<SpanData> spans) {
			this.spans.addAll(spans);
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public CompletableResultCode flush() {
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public CompletableResultCode shutdown() {
			return CompletableResultCode.ofSuccess();
		}

	}

}
