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
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.PlanExecutorDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.node.PlanExecutorNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.PlannerNode;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

public final class PlanningAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(PLANNING_AGENT,
			"Creates, repairs and advances the execution plan.",
			"data preparation is complete or an executed step must advance the existing plan.");

	public PlanningAgent(NodeBeanUtil nodeBeanUtil, KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("planning_agent_graph", keyStrategyFactory)
			.addNode("planning_entry", node_async(state -> Map.of()))
			.addNode(PLANNER_NODE, nodeBeanUtil.getNodeBeanAsync(PlannerNode.class))
			.addNode(PLAN_EXECUTOR_NODE, nodeBeanUtil.getNodeBeanAsync(PlanExecutorNode.class))
			.addNode("handoff_sql", handoff(PLANNING_AGENT, SQL_AGENT, null))
			.addNode("handoff_python", handoff(PLANNING_AGENT, PYTHON_AGENT, null))
			.addNode("handoff_report", handoff(PLANNING_AGENT, REPORT_AGENT, null))
			.addNode("handoff_human_review", handoff(PLANNING_AGENT, HUMAN_FEEDBACK_NODE, null))
			.addNode(HANDOFF_FINISH, handoff(PLANNING_AGENT, FINISH, null))
			.addEdge(START, "planning_entry")
			.addConditionalEdges("planning_entry", edge_async(state -> {
				String mode = state.value(MULTI_AGENT_PLANNING_MODE, PLAN_MODE);
				return EXECUTE_MODE.equals(mode) ? PLAN_EXECUTOR_NODE : PLANNER_NODE;
			}), Map.of(PLANNER_NODE, PLANNER_NODE, PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE))
			.addEdge(PLANNER_NODE, PLAN_EXECUTOR_NODE)
			.addConditionalEdges(PLAN_EXECUTOR_NODE, edge_async(new PlanExecutorDispatcher()), Map.of(
					PLANNER_NODE, PLANNER_NODE,
					SQL_GENERATE_NODE, "handoff_sql",
					PYTHON_GENERATE_NODE, "handoff_python",
					REPORT_GENERATOR_NODE, "handoff_report",
					HUMAN_FEEDBACK_NODE, "handoff_human_review",
					END, HANDOFF_FINISH))
			.addEdge("handoff_sql", END)
			.addEdge("handoff_python", END)
			.addEdge("handoff_report", END)
			.addEdge("handoff_human_review", END)
			.addEdge(HANDOFF_FINISH, END));
	}

}
