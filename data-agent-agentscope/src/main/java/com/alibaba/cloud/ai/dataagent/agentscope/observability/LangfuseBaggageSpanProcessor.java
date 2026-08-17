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

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import java.util.List;

final class LangfuseBaggageSpanProcessor implements SpanProcessor {

	static final String USER_ID = "langfuse.user.id";

	static final String SESSION_ID = "langfuse.session.id";

	static final String TRACE_NAME = "langfuse.trace.name";

	static final String AGENT_ID = "langfuse.trace.metadata.agent_id";

	static final String THREAD_ID = "langfuse.trace.metadata.thread_id";

	private static final List<String> KEYS = List.of(USER_ID, SESSION_ID, TRACE_NAME, AGENT_ID, THREAD_ID);

	@Override
	public void onStart(Context parentContext, ReadWriteSpan span) {
		Baggage baggage = Baggage.fromContext(parentContext);
		for (String key : KEYS) {
			String value = baggage.getEntryValue(key);
			if (value != null) {
				span.setAttribute(AttributeKey.stringKey(key), value);
			}
		}
	}

	@Override
	public boolean isStartRequired() {
		return true;
	}

	@Override
	public void onEnd(ReadableSpan span) {
	}

	@Override
	public boolean isEndRequired() {
		return false;
	}

}
