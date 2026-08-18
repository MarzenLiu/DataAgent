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

import com.alibaba.cloud.ai.dataagent.agentscope.config.LangfuseProperties;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LangfuseTelemetry implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(LangfuseTelemetry.class);

	private static final String INSTRUMENTATION_NAME = "com.alibaba.cloud.ai.dataagent.agentscope";

	private final boolean enabled;

	private final OpenTelemetry openTelemetry;

	private final SdkTracerProvider tracerProvider;

	private final OtelTracingMiddleware agentScopeTracingMiddleware;

	public LangfuseTelemetry(LangfuseProperties properties) {
		this.enabled = properties.isEnabled();
		if (!enabled) {
			this.openTelemetry = OpenTelemetry.noop();
			this.tracerProvider = null;
			this.agentScopeTracingMiddleware = null;
			return;
		}
		validate(properties);
		String auth = properties.getPublicKey() + ":" + properties.getSecretKey();
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
		OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
			.setEndpoint(traceEndpoint(properties.getHost()))
			.addHeader("Authorization", "Basic " + encodedAuth)
			.addHeader("x-langfuse-ingestion-version", "4")
			.setTimeout(Duration.ofSeconds(10))
			.build();
		Resource resource = Resource.getDefault()
			.merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), properties.getServiceName(),
					AttributeKey.stringKey("deployment.environment.name"), properties.getEnvironment())));
		this.tracerProvider = SdkTracerProvider.builder()
			.addSpanProcessor(new LangfuseBaggageSpanProcessor())
			.addSpanProcessor(BatchSpanProcessor.builder(new AgentScopeSpanExporter(exporter))
				.setScheduleDelay(Duration.ofSeconds(1))
				.build())
			.setResource(resource)
			.build();
		OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
		try {
			GlobalOpenTelemetry.set(sdk);
		}
		catch (IllegalStateException ex) {
			tracerProvider.close();
			throw new IllegalStateException(
					"Langfuse is enabled, but a global OpenTelemetry instance has already been registered", ex);
		}
		this.openTelemetry = sdk;
		this.agentScopeTracingMiddleware = new OtelTracingMiddleware();
		LOGGER.info("AgentScope OpenTelemetry initialized with Langfuse endpoint {}",
				traceEndpoint(properties.getHost()));
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Tracer getTracer() {
		return openTelemetry.getTracer(INSTRUMENTATION_NAME);
	}

	public OtelTracingMiddleware getAgentScopeTracingMiddleware() {
		if (agentScopeTracingMiddleware == null) {
			throw new IllegalStateException("AgentScope tracing middleware requested while Langfuse is disabled");
		}
		return agentScopeTracingMiddleware;
	}

	@Override
	public void close() {
		if (tracerProvider != null) {
			tracerProvider.close();
		}
	}

	static String traceEndpoint(String host) {
		String normalized = host.trim().replaceAll("/+$", "");
		if (normalized.endsWith("/api/public/otel/v1/traces")) {
			return normalized;
		}
		if (normalized.endsWith("/api/public/otel")) {
			return normalized + "/v1/traces";
		}
		return normalized + "/api/public/otel/v1/traces";
	}

	private void validate(LangfuseProperties properties) {
		boolean missingCredentials = !StringUtils.hasText(properties.getHost())
				|| !StringUtils.hasText(properties.getPublicKey()) || !StringUtils.hasText(properties.getSecretKey());
		if (missingCredentials) {
			throw new IllegalStateException(
					"LANGFUSE_HOST, LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY are required when Langfuse is enabled");
		}
		if (!StringUtils.hasText(properties.getServiceName()) || !StringUtils.hasText(properties.getEnvironment())) {
			throw new IllegalStateException("Langfuse service-name and environment must not be blank");
		}
	}

}
