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
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.REPORT_GENERATOR_NODE;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.FINISH;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.REPORT_AGENT;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.HANDOFF_FINISH;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.handoff;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

public final class ReportAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(REPORT_AGENT,
			"Composes the final Markdown analysis report.",
			"all required plan steps are complete and their results are ready for presentation.");

	public ReportAgent(NodeBeanUtil nodeBeanUtil, KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("report_agent_graph", keyStrategyFactory)
			.addNode(REPORT_GENERATOR_NODE, nodeBeanUtil.getNodeBeanAsync(ReportGeneratorNode.class))
			.addNode(HANDOFF_FINISH, handoff(REPORT_AGENT, FINISH, null))
			.addEdge(START, REPORT_GENERATOR_NODE)
			.addEdge(REPORT_GENERATOR_NODE, HANDOFF_FINISH)
			.addEdge(HANDOFF_FINISH, END));
	}

}
