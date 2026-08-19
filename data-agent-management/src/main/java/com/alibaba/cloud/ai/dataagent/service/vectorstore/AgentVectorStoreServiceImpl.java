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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import com.alibaba.cloud.ai.dataagent.rag.EmbeddingClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Framework-neutral vector ingestion. Retrieval is performed by the standalone MCP
 * service and exposed to AgentScope as tools.
 */
@Service
public class AgentVectorStoreServiceImpl implements AgentVectorStoreService {

	private static final TypeReference<Map<String, StoredDocument>> DOCUMENTS = new TypeReference<>() { };
	private static final Gson GSON = new Gson();
	private final DataAgentProperties.VectorStoreProperties properties;
	private final EmbeddingClient embeddingClient;
	private final ObjectMapper objectMapper;
	private MilvusClientV2 milvusClient;

	public AgentVectorStoreServiceImpl(DataAgentProperties dataAgentProperties, EmbeddingClient embeddingClient,
			ObjectMapper objectMapper) {
		this.properties = dataAgentProperties.getVectorStore();
		this.embeddingClient = embeddingClient;
		this.objectMapper = objectMapper;
		if (isMilvus()) {
			this.milvusClient = createMilvusClient();
			ensureCollection();
		}
	}

	@Override
	public Boolean deleteDocumentsByVectorType(String agentId, String vectorType) {
		return deleteDocumentsByMetadata(agentId,
				Map.of(DocumentMetadataConstant.VECTOR_TYPE, vectorType));
	}

	@Override
	public Boolean deleteDocumentsByMetadata(String agentId, Map<String, Object> metadata) {
		Map<String, Object> scoped = new LinkedHashMap<>(metadata);
		scoped.put(Constant.AGENT_ID, agentId);
		return deleteDocumentsByMetadata(scoped);
	}

	@Override
	public synchronized Boolean deleteDocumentsByMetadata(Map<String, Object> metadata) {
		Assert.notEmpty(metadata, "Metadata cannot be empty");
		if (isMilvus()) {
			milvusClient.delete(DeleteReq.builder().databaseName(properties.getMilvusDatabase())
				.collectionName(properties.getMilvusCollection()).filter(milvusFilter(metadata)).build());
		}
		else {
			Map<String, StoredDocument> documents = readSimpleStore();
			documents.entrySet().removeIf(entry -> matches(entry.getValue().metadata(), metadata));
			writeSimpleStore(documents);
		}
		return true;
	}

	@Override
	public boolean hasTableDocuments(Integer datasourceId, List<String> tableNames) {
		if (tableNames == null || tableNames.isEmpty()) {
			return false;
		}
		Map<String, Object> metadata = Map.of(Constant.DATASOURCE_ID, datasourceId.toString(),
				DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE);
		if (isMilvus()) {
			QueryResp result = milvusClient.query(QueryReq.builder().databaseName(properties.getMilvusDatabase())
				.collectionName(properties.getMilvusCollection()).filter(milvusFilter(metadata))
				.outputFields(List.of("metadata")).limit(properties.getBatchDelTopkLimit())
				.consistencyLevel(ConsistencyLevel.STRONG).build());
			return tableNames.stream().allMatch(name -> result.getQueryResults().stream()
				.anyMatch(row -> String.valueOf(row.getEntity().get("metadata")).contains(name)));
		}
		return tableNames.stream().allMatch(name -> readSimpleStore().values().stream()
			.filter(document -> matches(document.metadata(), metadata))
			.anyMatch(document -> name.equals(String.valueOf(document.metadata().get(DocumentMetadataConstant.TABLE_NAME)))));
	}

	@Override
	public synchronized void replaceDocumentsByMetadata(Map<String, Object> metadata, List<Document> documents) {
		deleteDocumentsByMetadata(metadata);
		addDocuments(resolveOwner(metadata), documents);
	}

	@Override
	public synchronized void addDocuments(String ownerId, List<Document> documents) {
		Assert.hasText(ownerId, "Owner ID cannot be empty");
		Assert.notEmpty(documents, "Documents cannot be empty");
		List<StoredDocument> stored = documents.stream().map(this::embed).toList();
		if (isMilvus()) {
			List<JsonObject> rows = stored.stream().map(this::milvusRow).toList();
			milvusClient.insert(InsertReq.builder().databaseName(properties.getMilvusDatabase())
				.collectionName(properties.getMilvusCollection()).data(rows).build());
		}
		else {
			Map<String, StoredDocument> existing = readSimpleStore();
			stored.forEach(document -> existing.put(document.id(), document));
			writeSimpleStore(existing);
		}
	}

	private StoredDocument embed(Document document) {
		String id = StringUtils.hasText(document.getId()) ? document.getId() : UUID.randomUUID().toString();
		return new StoredDocument(document.getText(), id, Map.copyOf(document.getMetadata()),
				embeddingClient.embed(document.getText()));
	}

	private JsonObject milvusRow(StoredDocument document) {
		JsonObject row = new JsonObject();
		row.addProperty("doc_id", document.id());
		row.addProperty("content", document.text());
		row.add("metadata", GSON.toJsonTree(document.metadata()));
		row.add("embedding", GSON.toJsonTree(toFloat(document.embedding())));
		return row;
	}

	private Map<String, StoredDocument> readSimpleStore() {
		Path path = Path.of(properties.getFilePath()).toAbsolutePath().normalize();
		if (!Files.isRegularFile(path)) {
			return new LinkedHashMap<>();
		}
		try {
			return new LinkedHashMap<>(objectMapper.readValue(path.toFile(), DOCUMENTS));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to read vector store " + path, ex);
		}
	}

	private void writeSimpleStore(Map<String, StoredDocument> documents) {
		Path path = Path.of(properties.getFilePath()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(path.getParent());
			Path temporary = Files.createTempFile(path.getParent(), "vector-store-", ".json");
			objectMapper.writeValue(temporary.toFile(), documents);
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to write vector store " + path, ex);
		}
	}

	private boolean matches(Map<String, Object> actual, Map<String, Object> expected) {
		return expected.entrySet().stream().allMatch(entry -> Objects.equals(String.valueOf(actual.get(entry.getKey())),
				String.valueOf(entry.getValue())));
	}

	private String milvusFilter(Map<String, Object> metadata) {
		return metadata.entrySet().stream()
			.map(entry -> "metadata[\"" + entry.getKey() + "\"] == " + filterValue(entry.getValue()))
			.reduce((left, right) -> left + " and " + right).orElseThrow();
	}

	private String filterValue(Object value) {
		if (value instanceof Number || value instanceof Boolean) {
			return String.valueOf(value);
		}
		return "\"" + escape(value) + "\"";
	}

	private String escape(Object value) {
		return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String resolveOwner(Map<String, Object> metadata) {
		Object owner = metadata.getOrDefault(Constant.AGENT_ID, metadata.get(Constant.DATASOURCE_ID));
		return String.valueOf(owner);
	}

	private boolean isMilvus() {
		return "milvus".equalsIgnoreCase(properties.getType());
	}

	private MilvusClientV2 createMilvusClient() {
		ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder().uri(properties.getMilvusUri())
			.connectTimeoutMs(30_000L);
		if (StringUtils.hasText(properties.getMilvusToken())) {
			builder.token(properties.getMilvusToken());
		}
		return new MilvusClientV2(builder.build());
	}

	private void ensureCollection() {
		boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
			.databaseName(properties.getMilvusDatabase()).collectionName(properties.getMilvusCollection()).build());
		if (exists) {
			return;
		}
		CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
		schema.setEnableDynamicField(false);
		schema.addField(AddFieldReq.builder().fieldName("doc_id").dataType(DataType.VarChar).maxLength(36)
			.isPrimaryKey(true).autoID(false).build());
		schema.addField(AddFieldReq.builder().fieldName("content").dataType(DataType.VarChar).maxLength(65_535).build());
		schema.addField(AddFieldReq.builder().fieldName("metadata").dataType(DataType.JSON).build());
		schema.addField(AddFieldReq.builder().fieldName("embedding").dataType(DataType.FloatVector)
			.dimension(properties.getEmbeddingDimension()).build());
		IndexParam index = IndexParam.builder().fieldName("embedding").indexType(IndexParam.IndexType.AUTOINDEX)
			.metricType(IndexParam.MetricType.COSINE).build();
		milvusClient.createCollection(CreateCollectionReq.builder().databaseName(properties.getMilvusDatabase())
			.collectionName(properties.getMilvusCollection()).collectionSchema(schema).indexParams(List.of(index))
			.consistencyLevel(ConsistencyLevel.BOUNDED).build());
	}

	private List<Float> toFloat(double[] values) {
		List<Float> result = new ArrayList<>(values.length);
		for (double value : values) {
			result.add((float) value);
		}
		return result;
	}

	@PreDestroy
	void close() {
		if (milvusClient != null) {
			milvusClient.close();
		}
	}

	private record StoredDocument(String text, String id, Map<String, Object> metadata, double[] embedding) { }

}
