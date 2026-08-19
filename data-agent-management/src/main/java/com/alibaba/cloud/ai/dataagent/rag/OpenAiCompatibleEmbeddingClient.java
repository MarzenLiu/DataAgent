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
package com.alibaba.cloud.ai.dataagent.rag;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelConfigDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** Calls the configured OpenAI-compatible embedding endpoint without a model framework. */
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

	private final ModelConfigDataService modelConfigDataService;

	private final ObjectMapper objectMapper;

	public OpenAiCompatibleEmbeddingClient(ModelConfigDataService modelConfigDataService, ObjectMapper objectMapper) {
		this.modelConfigDataService = modelConfigDataService;
		this.objectMapper = objectMapper;
	}

	@Override
	public double[] embed(String text) {
		ModelConfigDTO settings = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
		if (settings == null) {
			throw new IllegalStateException("No active EMBEDDING model is configured");
		}
		return embed(settings, text);
	}

	public double[] embed(ModelConfigDTO settings, String text) {
		if (!StringUtils.hasText(settings.getBaseUrl()) || !StringUtils.hasText(settings.getModelName())) {
			throw new IllegalStateException("The active EMBEDDING model configuration is incomplete");
		}
		RestClient.Builder builder = RestClient.builder().baseUrl(trimTrailingSlash(settings.getBaseUrl()));
		if (StringUtils.hasText(settings.getApiKey())) {
			builder.defaultHeader("Authorization", "Bearer " + settings.getApiKey());
		}
		String endpoint = StringUtils.hasText(settings.getEmbeddingsPath()) ? settings.getEmbeddingsPath()
				: "/v1/embeddings";
		if (!endpoint.startsWith("/")) {
			endpoint = "/" + endpoint;
		}
		String response = builder.build()
			.post()
			.uri(endpoint)
			.body(Map.of("model", settings.getModelName(), "input", text))
			.retrieve()
			.body(String.class);
		return parseVector(response);
	}

	private double[] parseVector(String response) {
		try {
			JsonNode embedding = objectMapper.readTree(response).path("data").path(0).path("embedding");
			if (!embedding.isArray() || embedding.isEmpty()) {
				throw new IllegalStateException("Embedding endpoint returned no vector data");
			}
			double[] vector = new double[embedding.size()];
			for (int index = 0; index < embedding.size(); index++) {
				vector[index] = embedding.get(index).asDouble();
			}
			return vector;
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Embedding endpoint returned invalid JSON", ex);
		}
	}

	private String trimTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

}
