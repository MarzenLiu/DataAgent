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
package com.alibaba.cloud.ai.dataagent.workflow.agent;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.util.NodeBeanUtil;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.AgentDescriptor;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.WorkflowCapabilityAgent;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.SqlGenerateDispatcher;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.agent.Agent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataAnalysisAgentFactoryTest {

	@Test
	void groupsWorkflowNodesByBusinessCapability() {
		NodeBeanUtil nodeBeanUtil = mock(NodeBeanUtil.class);
		AsyncNodeAction noOp = node_async(state -> Map.of());
		when(nodeBeanUtil.getNodeBeanAsync(any())).thenReturn(noOp);
		when(nodeBeanUtil.getEdgeBeanAsync(SqlGenerateDispatcher.class))
			.thenReturn(edge_async(state -> END));
		KeyStrategyFactory keyStrategyFactory = () -> Map.of("messages", KeyStrategy.APPEND);

		List<Agent> agents = new DataAnalysisAgentFactory(nodeBeanUtil,
				new CodeExecutorProperties(), keyStrategyFactory).createAgents();

		assertEquals(9, agents.size());
		assertTrue(agents.stream().allMatch(AgentDescriptor.class::isInstance));
		assertCapabilityContains(agents, REQUEST_UNDERSTANDING_AGENT, INTENT_RECOGNITION_NODE,
				EVIDENCE_RECALL_NODE, QUERY_ENHANCE_NODE);
		assertCapabilityExcludes(agents, REQUEST_UNDERSTANDING_AGENT, USER_PROFILE_NODE);
		assertCapabilityContains(agents, DATA_DISCOVERY_AGENT, SCHEMA_RECALL_NODE,
				BUSINESS_DATA_DISCOVERY_NODE);
		assertCapabilityContains(agents, DATA_PREPARATION_AGENT, SCHEMA_RECALL_NODE,
				TABLE_RELATION_NODE, FEASIBILITY_ASSESSMENT_NODE);
		assertCapabilityContains(agents, PLANNING_AGENT, PLANNER_NODE, PLAN_EXECUTOR_NODE);
		assertCapabilityContains(agents, SQL_AGENT, SQL_GENERATE_NODE,
				SEMANTIC_CONSISTENCY_NODE, SQL_EXECUTE_NODE);
		assertCapabilityContains(agents, PYTHON_AGENT, PYTHON_GENERATE_NODE,
				PYTHON_EXECUTE_NODE, PYTHON_ANALYZE_NODE);
		assertCapabilityContains(agents, REPORT_AGENT, REPORT_GENERATOR_NODE);
		assertCapabilityContains(agents, REPORT_REVISION_AGENT, REPORT_REVISION_NODE);
		assertCapabilityContains(agents, HUMAN_FEEDBACK_NODE, HUMAN_FEEDBACK_NODE);
	}

	private void assertCapabilityContains(List<Agent> agents, String agentName, String... nodeNames) {
		Agent agent = agents.stream().filter(candidate -> agentName.equals(candidate.name())).findFirst().orElseThrow();
		assertInstanceOf(WorkflowCapabilityAgent.class, agent);
		String graph = agent.getGraph().getGraph(GraphRepresentation.Type.PLANTUML, agentName).content();
		for (String nodeName : nodeNames) {
			assertTrue(graph.contains(nodeName), () -> agentName + " should contain " + nodeName);
		}
	}

	private void assertCapabilityExcludes(List<Agent> agents, String agentName, String nodeName) {
		Agent agent = agents.stream().filter(candidate -> agentName.equals(candidate.name())).findFirst().orElseThrow();
		String graph = agent.getGraph().getGraph(GraphRepresentation.Type.PLANTUML, agentName).content();
		assertFalse(graph.contains(nodeName), () -> agentName + " should not contain disabled node " + nodeName);
	}

}
