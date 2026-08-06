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
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.IntentRecognitionDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.QueryEnhanceDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.node.EvidenceRecallNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.IntentRecognitionNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.QueryEnhanceNode;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

public final class RequestUnderstandingAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(REQUEST_UNDERSTANDING_AGENT,
			"Recognizes intent, recalls evidence and normalizes the request.",
			"a new user request has not yet been understood or normalized.");

	public RequestUnderstandingAgent(NodeBeanUtil nodeBeanUtil, KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("request_understanding_agent_graph", keyStrategyFactory)
			.addNode(INTENT_RECOGNITION_NODE, nodeBeanUtil.getNodeBeanAsync(IntentRecognitionNode.class))
			.addNode(EVIDENCE_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(EvidenceRecallNode.class))
			.addNode(QUERY_ENHANCE_NODE, nodeBeanUtil.getNodeBeanAsync(QueryEnhanceNode.class))
			.addNode("handoff_data_preparation",
					handoff(REQUEST_UNDERSTANDING_AGENT, DATA_PREPARATION_AGENT, null))
			.addNode(HANDOFF_FINISH, handoff(REQUEST_UNDERSTANDING_AGENT, FINISH, null))
			.addEdge(START, INTENT_RECOGNITION_NODE)
			.addConditionalEdges(INTENT_RECOGNITION_NODE, edge_async(new IntentRecognitionDispatcher()),
					Map.of(EVIDENCE_RECALL_NODE, EVIDENCE_RECALL_NODE, END, HANDOFF_FINISH))
			.addEdge(EVIDENCE_RECALL_NODE, QUERY_ENHANCE_NODE)
			.addConditionalEdges(QUERY_ENHANCE_NODE, edge_async(new QueryEnhanceDispatcher()),
					Map.of(SCHEMA_RECALL_NODE, "handoff_data_preparation", END, HANDOFF_FINISH))
			.addEdge("handoff_data_preparation", END)
			.addEdge(HANDOFF_FINISH, END));
	}

}
