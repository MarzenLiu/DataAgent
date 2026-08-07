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
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.DataPreparationAgent;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.DataDiscoveryAgent;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.HumanFeedbackAgent;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.PlanningAgent;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.PythonAgent;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.ReportAgent;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.ReportRevisionAgent;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.RequestUnderstandingAgent;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.SqlAgent;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.agent.Agent;

import java.util.List;

/**
 * Assembles the independently configured capability agents used by the supervisor.
 */
public final class DataAnalysisAgentFactory {

	private final NodeBeanUtil nodeBeanUtil;

	private final CodeExecutorProperties codeExecutorProperties;

	private final KeyStrategyFactory keyStrategyFactory;

	public DataAnalysisAgentFactory(NodeBeanUtil nodeBeanUtil, CodeExecutorProperties codeExecutorProperties,
			KeyStrategyFactory keyStrategyFactory) {
		this.nodeBeanUtil = nodeBeanUtil;
		this.codeExecutorProperties = codeExecutorProperties;
		this.keyStrategyFactory = keyStrategyFactory;
	}

	public List<Agent> createAgents() {
		return List.of(
				new RequestUnderstandingAgent(nodeBeanUtil, keyStrategyFactory),
				new DataDiscoveryAgent(nodeBeanUtil, keyStrategyFactory),
				new DataPreparationAgent(nodeBeanUtil, keyStrategyFactory),
				new PlanningAgent(nodeBeanUtil, keyStrategyFactory),
				new SqlAgent(nodeBeanUtil, keyStrategyFactory),
				new PythonAgent(nodeBeanUtil, codeExecutorProperties, keyStrategyFactory),
				new ReportAgent(nodeBeanUtil, keyStrategyFactory),
				new ReportRevisionAgent(nodeBeanUtil, keyStrategyFactory),
				new HumanFeedbackAgent(nodeBeanUtil, keyStrategyFactory));
	}

}
