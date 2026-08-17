package com.alibaba.cloud.ai.dataagent.mcp.rag;

import com.alibaba.cloud.ai.dataagent.mcp.config.McpToolProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${data-agent.mcp.rag.enabled:true}' == 'true' && '${data-agent.mcp.rag.store-type:milvus}' == 'simple'")
public class SimpleVectorKnowledgeStore implements KnowledgeVectorStore {
	private static final TypeReference<Map<String, VectorDocument>> DOCUMENTS = new TypeReference<>() { };
	private final ObjectMapper objectMapper;
	private final McpToolProperties.Rag properties;
	private volatile Snapshot snapshot = new Snapshot(FileTime.fromMillis(-1), List.of());

	public SimpleVectorKnowledgeStore(ObjectMapper objectMapper, McpToolProperties properties) {
		this.objectMapper = objectMapper;
		this.properties = properties.getRag();
	}

	@Override
	public List<Match> search(long agentId, Set<Integer> recalledKnowledgeIds, double[] queryVector) {
		if (recalledKnowledgeIds.isEmpty() || queryVector.length == 0) return List.of();
		List<Match> matches = new ArrayList<>();
		for (VectorDocument document : documents()) {
			Map<String, Object> metadata = document.metadata() == null ? Map.of() : document.metadata();
			if (!"agentKnowledge".equals(String.valueOf(metadata.get("vectorType")))
					|| !String.valueOf(agentId).equals(String.valueOf(metadata.get("agentId")))) continue;
			Integer knowledgeId = integer(metadata.get("agentKnowledgeId"));
			if (knowledgeId == null || !recalledKnowledgeIds.contains(knowledgeId) || document.embedding() == null
					|| document.embedding().length != queryVector.length) continue;
			double score = cosine(queryVector, document.embedding());
			if (score >= properties.getSimilarityThreshold()) matches.add(new Match(knowledgeId, document.text(), metadata, score));
		}
		return matches.stream().sorted(Comparator.comparingDouble(Match::score).reversed())
				.limit(Math.max(1, properties.getTopK())).toList();
	}

	private List<VectorDocument> documents() {
		Path path = properties.getVectorStorePath().toAbsolutePath().normalize();
		if (!Files.isRegularFile(path)) return List.of();
		try {
			FileTime modified = Files.getLastModifiedTime(path);
			Snapshot current = snapshot;
			if (modified.equals(current.modified())) return current.documents();
			synchronized (this) {
				current = snapshot;
				if (!modified.equals(current.modified())) {
					Map<String, VectorDocument> loaded = objectMapper.readValue(path.toFile(), DOCUMENTS);
					current = new Snapshot(modified, List.copyOf(loaded.values()));
					snapshot = current;
				}
			}
			return current.documents();
		}
		catch (IOException ex) { throw new IllegalStateException("Unable to read vector store " + path, ex); }
	}
	private Integer integer(Object value) {
		if (value instanceof Number number) return number.intValue();
		try { return value == null ? null : Integer.valueOf(value.toString()); }
		catch (NumberFormatException ignored) { return null; }
	}
	private double cosine(double[] left, double[] right) {
		double dot = 0, leftNorm = 0, rightNorm = 0;
		for (int i = 0; i < left.length; i++) {
			dot += left[i] * right[i]; leftNorm += left[i] * left[i]; rightNorm += right[i] * right[i];
		}
		return leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
	}
	private record VectorDocument(String text, String id, Map<String, Object> metadata, double[] embedding) { }
	private record Snapshot(FileTime modified, List<VectorDocument> documents) { }
}
