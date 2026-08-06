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
import com.alibaba.cloud.ai.dataagent.workflow.node.BusinessDataDiscoveryNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.SchemaRecallNode;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.DATA_DISCOVERY_AGENT;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.FINISH;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.HANDOFF_FINISH;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.handoff;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

public final class DataDiscoveryAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(DATA_DISCOVERY_AGENT,
			"Explains which business data, dimensions and metrics the connected datasource can support.",
			"the user asks what data, indicators, metrics, reports or business questions are available.");

	public DataDiscoveryAgent(NodeBeanUtil nodeBeanUtil, KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("data_discovery_agent_graph", keyStrategyFactory)
			.addNode("data_discovery_entry", node_async(state -> Map.of(SCHEMA_DISCOVERY_MODE, true)))
			.addNode(SCHEMA_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(SchemaRecallNode.class))
			.addNode(BUSINESS_DATA_DISCOVERY_NODE,
					nodeBeanUtil.getNodeBeanAsync(BusinessDataDiscoveryNode.class))
			.addNode(HANDOFF_FINISH, handoff(DATA_DISCOVERY_AGENT, FINISH, null))
			.addEdge(START, "data_discovery_entry")
			.addEdge("data_discovery_entry", SCHEMA_RECALL_NODE)
			.addEdge(SCHEMA_RECALL_NODE, BUSINESS_DATA_DISCOVERY_NODE)
			.addEdge(BUSINESS_DATA_DISCOVERY_NODE, HANDOFF_FINISH)
			.addEdge(HANDOFF_FINISH, END));
	}

}
