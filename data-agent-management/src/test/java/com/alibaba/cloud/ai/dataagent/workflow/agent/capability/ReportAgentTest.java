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
package com.alibaba.cloud.ai.dataagent.workflow.agent.capability;

import com.alibaba.cloud.ai.dataagent.util.NodeBeanUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.ReportGeneratorNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.ReportRevisionNode;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportAgentTest {

	@Test
	void reportAgentRunsGeneratorNode() throws Exception {
		NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
		when(nodeBeanUtil.getNodeBeanAsync(ReportGeneratorNode.class))
			.thenReturn(node_async(state -> Map.of(RESULT, "generated")));
		ReportAgent agent = new ReportAgent(nodeBeanUtil, keyStrategyFactory());

		OverAllState state = agent.invoke(Map.of("messages", List.of(new UserMessage("generate report"))))
			.orElseThrow();

		assertEquals("generated", state.value(RESULT).orElseThrow());
	}

	@Test
	void reportRevisionAgentRunsRevisionNode() throws Exception {
		NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
		when(nodeBeanUtil.getNodeBeanAsync(ReportRevisionNode.class))
			.thenReturn(node_async(state -> Map.of(RESULT, "revised")));
		ReportRevisionAgent agent = new ReportRevisionAgent(nodeBeanUtil, keyStrategyFactory());

		OverAllState state = agent.invoke(Map.of("messages", List.of(new UserMessage("精简报告"))))
			.orElseThrow();

		assertEquals("revised", state.value(RESULT).orElseThrow());
	}

	private KeyStrategyFactory keyStrategyFactory() {
		return () -> {
			Map<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put(RESULT, KeyStrategy.REPLACE);
			strategies.put("messages", KeyStrategy.APPEND);
			strategies.put(MULTI_AGENT_NEXT, KeyStrategy.REPLACE);
			return strategies;
		};
	}

}
