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

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.agent.flow.builder.FlowGraphBuilder;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Official SAA SupervisorAgent with the DataAgent business-state strategy attached.
 */
public final class DataAnalysisSupervisorAgent extends SupervisorAgent {

	public static final String NAME = "data_analysis_supervisor";

	public static final String ROUTER_AGENT_NAME = "data_analysis_supervisor_router";

	public static final String ROUTER_MODEL_NODE_NAME = "_AGENT_MODEL_";

	public static final String REQUEST_UNDERSTANDING_AGENT = "request_understanding_agent";

	public static final String DATA_PREPARATION_AGENT = "data_preparation_agent";

	public static final String DATA_DISCOVERY_AGENT = "data_discovery_agent";

	public static final String PLANNING_AGENT = "planning_agent";

	public static final String SQL_AGENT = "sql_agent";

	public static final String PYTHON_AGENT = "python_agent";

	public static final String REPORT_AGENT = "report_agent";

	public static final String FINISH = "FINISH";

	private final KeyStrategyFactory keyStrategyFactory;

	public DataAnalysisSupervisorAgent(ReactAgent routerAgent, List<Agent> capabilityAgents,
			KeyStrategyFactory keyStrategyFactory) {
		super(SupervisorAgent.builder()
			.name(NAME)
			.description("Supervises the specialized agents used for data analysis")
			.mainAgent(Objects.requireNonNull(routerAgent, "routerAgent cannot be null"))
			.subAgents(List.copyOf(capabilityAgents)));
		this.keyStrategyFactory = Objects.requireNonNull(keyStrategyFactory, "keyStrategyFactory cannot be null");
	}

	@Override
	protected StateGraph buildSpecificGraph(FlowGraphBuilder.FlowGraphConfig config) throws GraphStateException {
		config.keyStrategyFactory(keyStrategyFactory);
		return super.buildSpecificGraph(config);
	}

	public synchronized void configure(CompileConfig compileConfig) {
		if (compiledGraph != null) {
			throw new IllegalStateException("Supervisor agent has already been compiled");
		}
		this.compileConfig = Objects.requireNonNull(compileConfig, "compileConfig cannot be null");
	}

	public RunnableConfig updateState(RunnableConfig config, Map<String, Object> values) throws Exception {
		return getAndCompileGraph().updateState(config, values);
	}

	public static String capabilityResultMessage(String completedAgent, String nextHint) {
		return "DATA_AGENT_RESULT completed_agent=" + completedAgent + " next_hint=" + nextHint
				+ ". The next_hint is advisory; the supervisor must independently select the next agent.";
	}

}
