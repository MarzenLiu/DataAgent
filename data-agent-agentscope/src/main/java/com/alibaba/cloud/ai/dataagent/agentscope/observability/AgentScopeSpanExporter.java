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

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;

final class AgentScopeSpanExporter implements SpanExporter {

	private static final String DATA_AGENT_SCOPE = "com.alibaba.cloud.ai.dataagent.agentscope";

	private static final String AGENTSCOPE_SCOPE = "io.agentscope";

	private final SpanExporter delegate;

	AgentScopeSpanExporter(SpanExporter delegate) {
		this.delegate = delegate;
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		List<SpanData> relevant = spans.stream().filter(this::isRelevant).toList();
		return relevant.isEmpty() ? CompletableResultCode.ofSuccess() : delegate.export(relevant);
	}

	@Override
	public CompletableResultCode flush() {
		return delegate.flush();
	}

	@Override
	public CompletableResultCode shutdown() {
		return delegate.shutdown();
	}

	private boolean isRelevant(SpanData span) {
		String name = span.getInstrumentationScopeInfo().getName();
		return DATA_AGENT_SCOPE.equals(name) || AGENTSCOPE_SCOPE.equals(name);
	}

}
