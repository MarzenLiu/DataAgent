package com.alibaba.cloud.ai.dataagent.mcp.rag;

import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository;
import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository.EmbeddingSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
	private final ToolDataRepository repository;
	private final ObjectMapper objectMapper;

	public OpenAiCompatibleEmbeddingClient(ToolDataRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	@Override
	public double[] embed(String text) {
		EmbeddingSettings settings = repository.findActiveEmbeddingModel()
				.orElseThrow(() -> new IllegalStateException("No active EMBEDDING model is configured"));
		if (!StringUtils.hasText(settings.baseUrl()) || !StringUtils.hasText(settings.modelName())) {
			throw new IllegalStateException("The active EMBEDDING model is incomplete");
		}
		RestClient.Builder builder = RestClient.builder().baseUrl(trimSlash(settings.baseUrl()));
		if (StringUtils.hasText(settings.apiKey())) builder.defaultHeader("Authorization", "Bearer " + settings.apiKey());
		String endpoint = StringUtils.hasText(settings.embeddingsPath()) ? settings.embeddingsPath() : "/v1/embeddings";
		if (!endpoint.startsWith("/")) endpoint = "/" + endpoint;
		String body = builder.build().post().uri(endpoint)
				.body(Map.of("model", settings.modelName(), "input", text)).retrieve().body(String.class);
		return vector(body);
	}

	private double[] vector(String body) {
		try {
			JsonNode embedding = objectMapper.readTree(body).path("data").path(0).path("embedding");
			if (!embedding.isArray() || embedding.isEmpty()) throw new IllegalStateException("Embedding endpoint returned no vector data");
			double[] values = new double[embedding.size()];
			for (int i = 0; i < embedding.size(); i++) values[i] = embedding.get(i).asDouble();
			return values;
		}
		catch (JsonProcessingException ex) { throw new IllegalStateException("Embedding endpoint returned invalid JSON", ex); }
	}
	private String trimSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
}
