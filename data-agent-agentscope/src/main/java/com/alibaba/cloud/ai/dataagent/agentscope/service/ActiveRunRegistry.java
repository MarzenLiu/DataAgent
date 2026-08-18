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
package com.alibaba.cloud.ai.dataagent.agentscope.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
class ActiveRunRegistry {

	private final Map<String, RunHandle> runs = new ConcurrentHashMap<>();

	Mono<Void> register(String runId, String conversationId) {
		RunHandle handle = new RunHandle(conversationId, Sinks.one());
		RunHandle previous = runs.put(runId, handle);
		if (previous != null) {
			previous.cancel().tryEmitEmpty();
		}
		return handle.cancel().asMono();
	}

	void remove(String runId) {
		runs.remove(runId);
	}

	void stop(String conversationId, String runId) {
		if (runId != null) {
			RunHandle handle = runs.remove(runId);
			if (handle != null) {
				handle.cancel().tryEmitEmpty();
			}
			return;
		}
		runs.entrySet().removeIf(entry -> {
			if (!entry.getValue().conversationId().equals(conversationId)) {
				return false;
			}
			entry.getValue().cancel().tryEmitEmpty();
			return true;
		});
	}

}
