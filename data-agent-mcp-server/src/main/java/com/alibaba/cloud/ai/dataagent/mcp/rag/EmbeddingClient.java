package com.alibaba.cloud.ai.dataagent.mcp.rag;

public interface EmbeddingClient {
	double[] embed(String text);
}
