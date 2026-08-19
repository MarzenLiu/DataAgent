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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** Calls model-only operations owned by the AgentScope runtime. */
@Component
public class AgentScopeModelClient {

	private final RestClient restClient;

	public AgentScopeModelClient(@Value("${data-agent.agentscope.base-url:http://127.0.0.1:8066}") String baseUrl) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	public void testChatModel(ModelConfigDTO config) {
		Map<String, Object> response = restClient.post().uri("/api/internal/model/test")
			.body(Map.of("provider", value(config.getProvider()), "baseUrl", value(config.getBaseUrl()), "apiKey",
					value(config.getApiKey()), "modelName", value(config.getModelName()), "temperature",
					config.getTemperature(), "maxTokens", config.getMaxTokens(), "endpointPath",
					value(config.getCompletionsPath())))
			.retrieve().body(Map.class);
		if (response == null || !StringUtils.hasText(String.valueOf(response.get("text")))) {
			throw new IllegalStateException("AgentScope model test returned empty content");
		}
	}

	public String generateTitle(String message) {
		Map<String, Object> response = restClient.post().uri("/api/internal/model/title")
			.body(Map.of("message", message)).retrieve().body(Map.class);
		return response == null ? null : String.valueOf(response.get("text"));
	}

	public void deleteSessionState(Integer agentId, String sessionId) {
		restClient.delete().uri("/api/internal/model/state/{agentId}/{sessionId}", agentId, sessionId).retrieve()
			.toBodilessEntity();
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

}
