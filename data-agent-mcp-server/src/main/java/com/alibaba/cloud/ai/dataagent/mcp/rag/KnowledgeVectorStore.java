package com.alibaba.cloud.ai.dataagent.mcp.rag;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface KnowledgeVectorStore {
	List<Match> search(long agentId, Set<Integer> recalledKnowledgeIds, double[] queryVector);
	record Match(int knowledgeId, String text, Map<String, Object> metadata, double score) { }
}
