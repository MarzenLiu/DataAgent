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
				new KnowledgeRecord(3, "退款规则", "FAQ", "如何退款", "七日内可退款", "rules.md")));
		KnowledgeVectorStore store = (agentId, ids, vector) ->
				List.of(new Match(3, "七日内可退款", Map.of("pageNumber", 2), 0.91));
		KnowledgeRetrievalToolService service = new KnowledgeRetrievalToolService(repository, text -> new double[] { 1, 0 },
				store, new ObjectMapper());
		assertThat(service.search(9L, "退款")).contains("退款规则-rules.md，第2页", "七日内可退款");
	}
}
