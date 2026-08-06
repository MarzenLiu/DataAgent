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
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.FeasibilityAssessmentDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.SchemaRecallDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.TableRelationDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.node.FeasibilityAssessmentNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.SchemaRecallNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.TableRelationNode;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

public final class DataPreparationAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(DATA_PREPARATION_AGENT,
			"Recalls schema, resolves table relationships and assesses feasibility.",
			"the request is understood but its database schema and feasibility are not prepared.");

	public DataPreparationAgent(NodeBeanUtil nodeBeanUtil, KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("data_preparation_agent_graph", keyStrategyFactory)
			.addNode(SCHEMA_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(SchemaRecallNode.class))
			.addNode(TABLE_RELATION_NODE, nodeBeanUtil.getNodeBeanAsync(TableRelationNode.class))
			.addNode(FEASIBILITY_ASSESSMENT_NODE,
					nodeBeanUtil.getNodeBeanAsync(FeasibilityAssessmentNode.class))
			.addNode(HANDOFF_PLANNING, handoff(DATA_PREPARATION_AGENT, PLANNING_AGENT, PLAN_MODE))
			.addNode(HANDOFF_FINISH, handoff(DATA_PREPARATION_AGENT, FINISH, null))
			.addEdge(START, SCHEMA_RECALL_NODE)
			.addConditionalEdges(SCHEMA_RECALL_NODE, edge_async(new SchemaRecallDispatcher()),
					Map.of(TABLE_RELATION_NODE, TABLE_RELATION_NODE, END, HANDOFF_FINISH))
			.addConditionalEdges(TABLE_RELATION_NODE, edge_async(new TableRelationDispatcher()),
					Map.of(FEASIBILITY_ASSESSMENT_NODE, FEASIBILITY_ASSESSMENT_NODE, TABLE_RELATION_NODE,
							TABLE_RELATION_NODE, END, HANDOFF_FINISH))
			.addConditionalEdges(FEASIBILITY_ASSESSMENT_NODE,
					edge_async(new FeasibilityAssessmentDispatcher()),
					Map.of(PLANNER_NODE, HANDOFF_PLANNING, END, HANDOFF_FINISH))
			.addEdge(HANDOFF_PLANNING, END)
			.addEdge(HANDOFF_FINISH, END));
	}

}
