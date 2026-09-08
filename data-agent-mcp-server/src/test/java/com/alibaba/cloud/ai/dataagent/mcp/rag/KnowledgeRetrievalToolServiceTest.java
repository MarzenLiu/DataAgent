package com.alibaba.cloud.ai.dataagent.mcp.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository;
import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository.KnowledgeRecord;
import com.alibaba.cloud.ai.dataagent.mcp.rag.KnowledgeVectorStore.Match;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalToolServiceTest {
	@Test void returnsKnowledgeWithSource() {
		ToolDataRepository repository = mock(ToolDataRepository.class);
		when(repository.findRecalledKnowledge(9L)).thenReturn(Map.of(3,
				new KnowledgeRecord(3, "退款规则", "FAQ", "如何退款", "七日内可退款", "rules.md", "text/markdown")));
		KnowledgeVectorStore store = (agentId, ids, vector) ->
				List.of(new Match(3, "七日内可退款", Map.of("pageNumber", 2), 0.91));
		KnowledgeRetrievalToolService service = new KnowledgeRetrievalToolService(repository, text -> new double[] { 1, 0 },
				store, new ObjectMapper());
		assertThat(service.search(9L, "退款")).contains("退款规则-rules.md，第2页", "七日内可退款");
	}

	@Test void returnsStructuredCitationForPdfDocument() throws Exception {
		ToolDataRepository repository = mock(ToolDataRepository.class);
		when(repository.findRecalledKnowledge(9L)).thenReturn(Map.of(7,
				new KnowledgeRecord(7, "水工规范", "DOCUMENT", null, null, "SLT191-2025.pdf", "application/pdf")));
		KnowledgeVectorStore store = (agentId, ids, vector) ->
				List.of(new Match(7, "混凝土结构条文", Map.of("pageNumber", 18), 0.93));
		KnowledgeRetrievalToolService service = new KnowledgeRetrievalToolService(repository, text -> new double[] { 1, 0 },
				store, new ObjectMapper());

		String result = service.search(9L, "混凝土结构");

		var match = new ObjectMapper().readTree(result).at("/matches/0");
		assertThat(match.path("citationId").asText()).matches("kb-7-[0-9a-f-]{36}");
		assertThat(match.path("citation").path("citationId").asText()).isEqualTo(match.path("citationId").asText());
		assertThat(match.path("citation").path("url").asText())
			.isEqualTo("/api/agent-knowledge/7/review/source#page=18");
		assertThat(new ObjectMapper().readTree(result).path("instruction").asText())
			.contains("[[cite:实际citationId值]]", "不要标记未使用的匹配");
	}

	@Test void includesPdfHighlightLocationInCitation() throws Exception {
		ToolDataRepository repository = mock(ToolDataRepository.class);
		when(repository.findRecalledKnowledge(9L)).thenReturn(Map.of(7,
				new KnowledgeRecord(7, "水工规范", "DOCUMENT", null, null, "SLT191-2025.pdf", "application/pdf")));
		KnowledgeVectorStore store = (agentId, ids, vector) -> List.of(new Match(7, "混凝土结构条文",
				Map.of("pageNumber", 18, "boundingBox", "84.5,278.0,510.0,220.0", "coordinateOrigin",
						"BOTTOMLEFT", "pageWidth", 595.0, "pageHeight", 842.0),
				0.93));
		KnowledgeRetrievalToolService service = new KnowledgeRetrievalToolService(repository, text -> new double[] { 1, 0 },
				store, new ObjectMapper());

		String result = service.search(9L, "混凝土结构");

		assertThat(new ObjectMapper().readTree(result).at("/matches/0/citation/url").asText())
			.isEqualTo("/api/agent-knowledge/7/review/source#page=18&bbox=84.5%2C278.0%2C510.0%2C220.0"
					+ "&origin=BOTTOMLEFT&pageWidth=595.0&pageHeight=842.0");
	}
}
