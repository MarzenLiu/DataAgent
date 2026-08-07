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
import com.alibaba.cloud.ai.dataagent.workflow.node.ReportRevisionNode;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.REPORT_REVISION_NODE;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.FINISH;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.REPORT_REVISION_AGENT;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.HANDOFF_FINISH;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.capability.CapabilityAgentSupport.handoff;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/** Capability dedicated to presentation-only changes to the latest report. */
public final class ReportRevisionAgent extends WorkflowCapabilityAgent {

	public static final AgentBasicInfo INFO = new AgentBasicInfo(REPORT_REVISION_AGENT,
			"Revises the latest report without querying or recalculating data.",
			"a latest report exists and the current request only changes its presentation, wording, structure, tone, translation, length or emphasis.");

	public ReportRevisionAgent(NodeBeanUtil nodeBeanUtil, KeyStrategyFactory keyStrategyFactory) {
		super(INFO, () -> new StateGraph("report_revision_agent_graph", keyStrategyFactory)
			.addNode(REPORT_REVISION_NODE, nodeBeanUtil.getNodeBeanAsync(ReportRevisionNode.class))
			.addNode(HANDOFF_FINISH, handoff(REPORT_REVISION_AGENT, FINISH, null))
			.addEdge(START, REPORT_REVISION_NODE)
			.addEdge(REPORT_REVISION_NODE, HANDOFF_FINISH)
			.addEdge(HANDOFF_FINISH, END));
	}

}
