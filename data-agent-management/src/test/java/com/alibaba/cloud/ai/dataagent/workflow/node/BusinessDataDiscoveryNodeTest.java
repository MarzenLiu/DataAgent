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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessDataDiscoveryNodeTest {

	@Test
	void apply_withoutSchema_doesNotAskModelToInventCapabilities() throws Exception {
		LlmService llmService = mock(LlmService.class);
		BusinessDataDiscoveryNode node = new BusinessDataDiscoveryNode(llmService);
		OverAllState state = new OverAllState();

		Map<String, Object> result = node.apply(state);

		assertTrue(result.containsKey(BUSINESS_DATA_DISCOVERY_NODE_OUTPUT));
		verifyNoInteractions(llmService);
	}

	@Test
	void apply_buildsBusinessCapabilityPromptFromRecalledSchema() throws Exception {
		LlmService llmService = mock(LlmService.class);
		when(llmService.callUserObservedWithMaxTokens(eq("business-data-discovery.summarize"), anyString(), eq(8000)))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("可查询订单金额指标")));
		BusinessDataDiscoveryNode node = new BusinessDataDiscoveryNode(llmService);
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, new ReplaceStrategy());
		state.updateState(Map.of(
				TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT,
				List.of(new Document("订单表", Map.of("name", "orders", "description", "业务订单"))),
				COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT,
				List.of(new Document("订单金额", Map.of("name", "amount", "tableName", "orders")))));

		Map<String, Object> result = node.apply(state);

		assertTrue(result.containsKey(BUSINESS_DATA_DISCOVERY_NODE_OUTPUT));
		verify(llmService).callUserObservedWithMaxTokens(eq("business-data-discovery.summarize"),
				argThat(prompt -> prompt.contains("orders") && prompt.contains("amount")
						&& prompt.contains("核心指标") && prompt.contains("禁止虚构业务口径")
						&& prompt.contains("1500 个中文字符以内")),
				eq(8000));
	}

}
