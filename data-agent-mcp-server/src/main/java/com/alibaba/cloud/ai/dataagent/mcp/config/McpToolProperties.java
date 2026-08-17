package com.alibaba.cloud.ai.dataagent.mcp.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("data-agent.mcp")
public class McpToolProperties {

	private String endpoint = "/mcp";
	private int maxQueryRows = 200;
	private final Rag rag = new Rag();

	public String getEndpoint() { return endpoint; }
	public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
	public int getMaxQueryRows() { return maxQueryRows; }
	public void setMaxQueryRows(int maxQueryRows) { this.maxQueryRows = maxQueryRows; }
	public Rag getRag() { return rag; }

	public static class Rag {
		private boolean enabled = true;
		private String storeType = "milvus";
		private Path vectorStorePath = Path.of("vectorstore", "vectorstore.json");
		private String milvusUri = "http://127.0.0.1:19530";
		private String milvusDatabase = "default";
		private String milvusCollection = "vector_store";
		private String milvusToken;
		private int embeddingDimension = 1024;
		private int topK = 8;
		private double similarityThreshold = 0.4;

		public boolean isEnabled() { return enabled; }
		public void setEnabled(boolean enabled) { this.enabled = enabled; }
		public String getStoreType() { return storeType; }
		public void setStoreType(String storeType) { this.storeType = storeType; }
		public Path getVectorStorePath() { return vectorStorePath; }
		public void setVectorStorePath(Path vectorStorePath) { this.vectorStorePath = vectorStorePath; }
		public String getMilvusUri() { return milvusUri; }
		public void setMilvusUri(String milvusUri) { this.milvusUri = milvusUri; }
		public String getMilvusDatabase() { return milvusDatabase; }
		public void setMilvusDatabase(String milvusDatabase) { this.milvusDatabase = milvusDatabase; }
		public String getMilvusCollection() { return milvusCollection; }
		public void setMilvusCollection(String milvusCollection) { this.milvusCollection = milvusCollection; }
		public String getMilvusToken() { return milvusToken; }
		public void setMilvusToken(String milvusToken) { this.milvusToken = milvusToken; }
		public int getEmbeddingDimension() { return embeddingDimension; }
		public void setEmbeddingDimension(int embeddingDimension) { this.embeddingDimension = embeddingDimension; }
		public int getTopK() { return topK; }
		public void setTopK(int topK) { this.topK = topK; }
		public double getSimilarityThreshold() { return similarityThreshold; }
		public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
	}
}
