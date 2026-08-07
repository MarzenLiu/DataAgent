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
import com.alibaba.cloud.ai.graph.agent.flow.node.MainAgentNodeAction;
import com.alibaba.cloud.ai.graph.agent.flow.node.MainAgentToSupervisorEdgeAction;
import com.alibaba.cloud.ai.graph.agent.flow.node.SupervisorNodeFromState;
import com.alibaba.cloud.ai.graph.agent.flow.strategy.FlowGraphBuildingStrategy;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.MULTI_AGENT_NEXT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SUPERVISOR_NEXT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_INTERRUPT_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_NODE;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncMultiCommandAction.node_async;

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

	public static final String REPORT_REVISION_AGENT = "report_revision_agent";

	public static final String FINISH = "FINISH";

	private static final String ROUTE_ENTRY = "data_analysis_supervisor_route_entry";

	private final KeyStrategyFactory keyStrategyFactory;

	private final ReactAgent routerAgent;

	private final List<Agent> capabilityAgents;

	public DataAnalysisSupervisorAgent(ReactAgent routerAgent, List<Agent> capabilityAgents,
			KeyStrategyFactory keyStrategyFactory) {
		super(SupervisorAgent.builder()
			.name(NAME)
			.description("Supervises the specialized agents used for data analysis")
			.mainAgent(Objects.requireNonNull(routerAgent, "routerAgent cannot be null"))
			.subAgents(List.copyOf(capabilityAgents)));
		this.keyStrategyFactory = Objects.requireNonNull(keyStrategyFactory, "keyStrategyFactory cannot be null");
		this.routerAgent = routerAgent;
		this.capabilityAgents = List.copyOf(capabilityAgents);
	}

	@Override
	protected StateGraph buildSpecificGraph(FlowGraphBuilder.FlowGraphConfig config) throws GraphStateException {
		StateGraph graph = config.getStateSerializer() == null ? new StateGraph(NAME, keyStrategyFactory)
				: new StateGraph(NAME, keyStrategyFactory, config.getStateSerializer());
		String supervisorNode = NAME + "_supervisor";

		graph.addNode(ROUTE_ENTRY, com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> Map.of()));
		graph.addNode(HUMAN_FEEDBACK_INTERRUPT_NODE,
				com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> Map.of()));
		graph.addNode(NAME,
				com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig
					.node_async(new MainAgentNodeAction(routerAgent, capabilityAgents)));
		graph.addNode(supervisorNode,
				com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async(state -> Map.of()));

		Map<String, String> routeEntryEdges = new HashMap<>();
		routeEntryEdges.put(NAME, NAME);
		routeEntryEdges.put(END, END);
		Map<String, String> agentEdges = new HashMap<>();
		for (Agent agent : capabilityAgents) {
			FlowGraphBuildingStrategy.addSubAgentNode(agent, graph);
			routeEntryEdges.put(agent.name(), HUMAN_FEEDBACK_NODE.equals(agent.name())
					? HUMAN_FEEDBACK_INTERRUPT_NODE : agent.name());
			agentEdges.put(agent.name(), agent.name());
			graph.addEdge(agent.name(), ROUTE_ENTRY);
		}
		if (agentEdges.containsKey(HUMAN_FEEDBACK_NODE)) {
			graph.addEdge(HUMAN_FEEDBACK_INTERRUPT_NODE, HUMAN_FEEDBACK_NODE);
		}

		graph.addEdge(START, ROUTE_ENTRY);
		graph.addConditionalEdges(ROUTE_ENTRY, state -> {
			String nextAgent = state.value(MULTI_AGENT_NEXT, "");
			if (FINISH.equals(nextAgent)) {
				return java.util.concurrent.CompletableFuture.completedFuture(END);
			}
			if (agentEdges.containsKey(nextAgent)) {
				return java.util.concurrent.CompletableFuture.completedFuture(nextAgent);
			}
			return java.util.concurrent.CompletableFuture.completedFuture(NAME);
		}, routeEntryEdges);

		graph.addConditionalEdges(NAME, new MainAgentToSupervisorEdgeAction(SUPERVISOR_NEXT, supervisorNode),
				Map.of(END, END, supervisorNode, supervisorNode));
		graph.addParallelConditionalEdges(supervisorNode,
				node_async(new SupervisorNodeFromState(SUPERVISOR_NEXT, capabilityAgents, ROUTE_ENTRY)), agentEdges);
		return graph;
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
				+ ". Route directly to next_hint when it names a capability agent.";
	}

}
