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

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.util.NodeBeanUtil;
import com.alibaba.cloud.ai.dataagent.workflow.dispatcher.PythonExecutorDispatcher;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonAnalyzeNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonExecuteNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonGenerateNode;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

public final class PythonAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(PYTHON_AGENT,
			"Generates and executes Python analysis for one plan step, including retries.",
			"the current plan step requires computation or analysis beyond SQL.");

	public PythonAgent(NodeBeanUtil nodeBeanUtil, CodeExecutorProperties properties,
			KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("python_agent_graph", keyStrategyFactory)
			.addNode(PYTHON_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonGenerateNode.class))
			.addNode(PYTHON_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonExecuteNode.class))
			.addNode(PYTHON_ANALYZE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonAnalyzeNode.class))
			.addNode(HANDOFF_PLANNING_EXECUTE, handoff(PYTHON_AGENT, PLANNING_AGENT, EXECUTE_MODE))
			.addNode(HANDOFF_FINISH, handoff(PYTHON_AGENT, FINISH, null))
			.addEdge(START, PYTHON_GENERATE_NODE)
			.addEdge(PYTHON_GENERATE_NODE, PYTHON_EXECUTE_NODE)
			.addConditionalEdges(PYTHON_EXECUTE_NODE, edge_async(new PythonExecutorDispatcher(properties)),
					Map.of(PYTHON_ANALYZE_NODE, PYTHON_ANALYZE_NODE, PYTHON_GENERATE_NODE,
							PYTHON_GENERATE_NODE, END, HANDOFF_FINISH))
			.addEdge(PYTHON_ANALYZE_NODE, HANDOFF_PLANNING_EXECUTE)
			.addEdge(HANDOFF_PLANNING_EXECUTE, END)
			.addEdge(HANDOFF_FINISH, END));
	}

}
