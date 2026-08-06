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
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.HumanFeedbackDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.node.HumanFeedbackNode;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.FINISH;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.PLANNING_AGENT;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

public final class HumanFeedbackAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(HUMAN_FEEDBACK_NODE,
			"Processes the human plan approval or rejection boundary.",
			"execution is paused for explicit human review or review feedback has been supplied.");

	public HumanFeedbackAgent(NodeBeanUtil nodeBeanUtil, KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("human_review_agent_graph", keyStrategyFactory)
			.addNode(HUMAN_FEEDBACK_NODE, nodeBeanUtil.getNodeBeanAsync(HumanFeedbackNode.class))
			.addNode(HANDOFF_PLANNING, handoff(HUMAN_FEEDBACK_NODE, PLANNING_AGENT, PLAN_MODE))
			.addNode(HANDOFF_PLANNING_EXECUTE,
					handoff(HUMAN_FEEDBACK_NODE, PLANNING_AGENT, EXECUTE_MODE))
			.addNode(HANDOFF_FINISH, handoff(HUMAN_FEEDBACK_NODE, FINISH, null))
			.addEdge(START, HUMAN_FEEDBACK_NODE)
			.addConditionalEdges(HUMAN_FEEDBACK_NODE, edge_async(new HumanFeedbackDispatcher()), Map.of(
					PLANNER_NODE, HANDOFF_PLANNING,
					PLAN_EXECUTOR_NODE, HANDOFF_PLANNING_EXECUTE,
					HUMAN_FEEDBACK_NODE, HUMAN_FEEDBACK_NODE,
					END, HANDOFF_FINISH))
			.addEdge(HANDOFF_PLANNING, END)
			.addEdge(HANDOFF_PLANNING_EXECUTE, END)
			.addEdge(HANDOFF_FINISH, END));
	}

}
