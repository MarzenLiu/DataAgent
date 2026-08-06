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
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.SQLExecutorDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.SemanticConsistenceDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.SqlGenerateDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.node.SemanticConsistencyNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlExecuteNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.SqlGenerateNode;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

public final class SqlAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(SQL_AGENT,
			"Generates, validates and executes one SQL plan step, including retries.",
			"the current plan step requires a database query.");

	public SqlAgent(NodeBeanUtil nodeBeanUtil, KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("sql_agent_graph", keyStrategyFactory)
			.addNode(SQL_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlGenerateNode.class))
			.addNode(SEMANTIC_CONSISTENCY_NODE,
					nodeBeanUtil.getNodeBeanAsync(SemanticConsistencyNode.class))
			.addNode(SQL_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlExecuteNode.class))
			.addNode(HANDOFF_PLANNING_EXECUTE, handoff(SQL_AGENT, PLANNING_AGENT, EXECUTE_MODE))
			.addNode(HANDOFF_FINISH, handoff(SQL_AGENT, FINISH, null))
			.addEdge(START, SQL_GENERATE_NODE)
			.addConditionalEdges(SQL_GENERATE_NODE, nodeBeanUtil.getEdgeBeanAsync(SqlGenerateDispatcher.class),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, SEMANTIC_CONSISTENCY_NODE,
							SEMANTIC_CONSISTENCY_NODE, END, HANDOFF_FINISH))
			.addConditionalEdges(SEMANTIC_CONSISTENCY_NODE,
					edge_async(new SemanticConsistenceDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, SQL_EXECUTE_NODE, SQL_EXECUTE_NODE))
			.addConditionalEdges(SQL_EXECUTE_NODE, edge_async(new SQLExecutorDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, PLAN_EXECUTOR_NODE, HANDOFF_PLANNING_EXECUTE))
			.addEdge(HANDOFF_PLANNING_EXECUTE, END)
			.addEdge(HANDOFF_FINISH, END));
	}

}
