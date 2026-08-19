package com.alibaba.cloud.ai.dataagent.mcp.rag;

import com.alibaba.cloud.ai.dataagent.mcp.config.McpToolProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Reads the framework-neutral ingestion collection schema shared by management. */
@Component
@ConditionalOnExpression("'${data-agent.mcp.rag.enabled:true}' == 'true' && '${data-agent.mcp.rag.store-type:milvus}' == 'milvus'")
public class MilvusKnowledgeStore implements KnowledgeVectorStore {
	private static final Logger log = LoggerFactory.getLogger(MilvusKnowledgeStore.class);
	private static final Gson GSON = new Gson();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
	private static final String ID_FIELD = "doc_id";
	private static final String CONTENT_FIELD = "content";
	private static final String METADATA_FIELD = "metadata";
	private static final String EMBEDDING_FIELD = "embedding";
	private final McpToolProperties.Rag properties;
	private final ObjectMapper objectMapper;
	private final MilvusClientV2 client;

	public MilvusKnowledgeStore(McpToolProperties properties, ObjectMapper objectMapper) {
		this.properties = properties.getRag();
		this.objectMapper = objectMapper;
		ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder().uri(this.properties.getMilvusUri()).connectTimeoutMs(30_000L);
		if (StringUtils.hasText(this.properties.getMilvusToken())) {
			builder.token(this.properties.getMilvusToken());
		}
		this.client = new MilvusClientV2(builder.build());
		ensureCollection();
	}

	@Override
	public List<Match> search(long agentId, Set<Integer> recalledKnowledgeIds, double[] queryVector) {
		if (recalledKnowledgeIds.isEmpty() || queryVector.length == 0) {
			return List.of();
		}
		if (queryVector.length != properties.getEmbeddingDimension()) {
			throw new IllegalStateException("Embedding dimension mismatch: expected %d, got %d"
					.formatted(properties.getEmbeddingDimension(), queryVector.length));
		}
		String ids = recalledKnowledgeIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
		String filter = "metadata[\"vectorType\"] == \"agentKnowledge\" and metadata[\"agentId\"] == \"%d\" and metadata[\"agentKnowledgeId\"] in [%s]"
				.formatted(agentId, ids);
		SearchResp response = client.search(SearchReq.builder()
				.databaseName(properties.getMilvusDatabase()).collectionName(properties.getMilvusCollection())
				.annsField(EMBEDDING_FIELD).metricType(IndexParam.MetricType.COSINE)
				.data(List.of(new FloatVec(toFloat(queryVector)))).filter(filter)
				.outputFields(List.of(ID_FIELD, CONTENT_FIELD, METADATA_FIELD)).limit(Math.max(1, properties.getTopK()))
				.consistencyLevel(ConsistencyLevel.STRONG).build());
		List<Match> matches = new ArrayList<>();
		if (response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
			return List.of();
		}
		for (SearchResp.SearchResult result : response.getSearchResults().get(0)) {
			Map<String, Object> metadata = metadata(result.getEntity().get(METADATA_FIELD));
			Integer knowledgeId = integer(metadata.get("agentKnowledgeId"));
			boolean accepted = knowledgeId != null && recalledKnowledgeIds.contains(knowledgeId)
					&& result.getScore() >= properties.getSimilarityThreshold();
			if (accepted) {
				matches.add(new Match(knowledgeId, String.valueOf(result.getEntity().get(CONTENT_FIELD)), metadata, result.getScore()));
			}
		}
		return matches.stream().sorted(Comparator.comparingDouble(Match::score).reversed()).toList();
	}

	private void ensureCollection() {
		boolean exists = client.hasCollection(HasCollectionReq.builder().databaseName(properties.getMilvusDatabase())
				.collectionName(properties.getMilvusCollection()).build());
		if (exists) {
			return;
		}
		CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
		schema.setEnableDynamicField(false);
		schema.addField(AddFieldReq.builder().fieldName(ID_FIELD).dataType(DataType.VarChar).maxLength(36).isPrimaryKey(true).autoID(false).build());
		schema.addField(AddFieldReq.builder().fieldName(CONTENT_FIELD).dataType(DataType.VarChar).maxLength(65_535).build());
		schema.addField(AddFieldReq.builder().fieldName(METADATA_FIELD).dataType(DataType.JSON).build());
		schema.addField(AddFieldReq.builder().fieldName(EMBEDDING_FIELD).dataType(DataType.FloatVector)
				.dimension(properties.getEmbeddingDimension()).build());
		IndexParam index = IndexParam.builder().fieldName(EMBEDDING_FIELD).indexType(IndexParam.IndexType.AUTOINDEX)
				.metricType(IndexParam.MetricType.COSINE).build();
		client.createCollection(CreateCollectionReq.builder().databaseName(properties.getMilvusDatabase())
				.collectionName(properties.getMilvusCollection()).collectionSchema(schema).indexParams(List.of(index))
				.consistencyLevel(ConsistencyLevel.BOUNDED).build());
		log.info("Created Milvus collection {}", properties.getMilvusCollection());
	}
	private Map<String, Object> metadata(Object raw) {
		if (raw == null) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(raw instanceof String string ? string : GSON.toJson(raw), MAP_TYPE);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to decode Milvus document metadata", ex);
		}
	}
	private float[] toFloat(double[] values) {
		float[] result = new float[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (float) values[i];
		}
		return result;
	}
	private Integer integer(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return value == null ? null : Integer.valueOf(value.toString());
		}
		catch (NumberFormatException ignored) {
			return null;
		}
	}
	@PreDestroy void close() { client.close(); }
}
