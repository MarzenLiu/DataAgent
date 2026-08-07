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

import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.AgentBasicInfo;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.WorkflowCapabilityAgent;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.workflow.agent.DataAnalysisSupervisorAgent.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

class DataAnalysisSupervisorAgentTest {

	@Test
	void supervisorRoutesCapabilityAgentAndFinishes() throws Exception {
		KeyStrategyFactory keyStrategyFactory = keyStrategyFactory();
		AtomicInteger modelCalls = new AtomicInteger();
		List<String> observedPrompts = new ArrayList<>();
		ChatModel model = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				observedPrompts.add(prompt.getContents());
				return response(modelCalls.getAndIncrement());
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				observedPrompts.add(prompt.getContents());
				return Flux.just(response(modelCalls.getAndIncrement()));
			}

			private ChatResponse response(int call) {
				return ChatResponseUtil.createPureResponse(
						call == 0 ? "[\"" + REQUEST_UNDERSTANDING_AGENT + "\"]" : "[\"FINISH\"]");
			}
		};
		ReactAgent router = ReactAgent.builder()
			.name(ROUTER_AGENT_NAME)
			.model(model)
			.systemPrompt("Return the recommended agent as a JSON array")
			.includeContents(false)
			.build();
		List<Agent> agents = capabilityAgents(keyStrategyFactory);
		DataAnalysisSupervisorAgent supervisor = new DataAnalysisSupervisorAgent(router, agents,
				keyStrategyFactory);

		List<Message> messages = List.of(new UserMessage("analyze sales"));
		OverAllState state = supervisor.invoke(Map.of(INPUT_KEY, "analyze sales", "messages", messages))
			.orElseThrow();

		assertEquals("executed", state.value("request_agent_result").orElseThrow());
		assertEquals(FINISH, state.value(MULTI_AGENT_NEXT).orElseThrow());
		assertEquals(1, modelCalls.get());
		assertFalse(observedPrompts.get(0).contains("DATA_AGENT_RESULT"));
		assertEquals(8, supervisor.subAgents().size());
		assertTrue(supervisor instanceof com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent);
	}

	@Test
	void supervisorGraphKeepsHumanReviewAsTopLevelInterruptibleAgent() {
		KeyStrategyFactory keyStrategyFactory = keyStrategyFactory();
		ReactAgent router = ReactAgent.builder()
			.name(ROUTER_AGENT_NAME)
			.model(prompt -> ChatResponseUtil.createPureResponse("[\"FINISH\"]"))
			.build();
		DataAnalysisSupervisorAgent supervisor = new DataAnalysisSupervisorAgent(router,
				capabilityAgents(keyStrategyFactory), keyStrategyFactory);

		String graph = supervisor.asStateGraph()
			.getGraph(com.alibaba.cloud.ai.graph.GraphRepresentation.Type.PLANTUML, "supervisor")
			.content();

		assertTrue(graph.contains(HUMAN_FEEDBACK_NODE));
		assertTrue(graph.contains(REQUEST_UNDERSTANDING_AGENT));
		assertTrue(graph.contains(SQL_AGENT));
		assertFalse(graph.contains(USER_PROFILE_NODE));
	}

	@Test
	void supervisorRouterCanSelectReportRevisionCapabilityFromConversationContext() throws Exception {
		KeyStrategyFactory keyStrategyFactory = keyStrategyFactory();
		AtomicInteger modelCalls = new AtomicInteger();
		List<String> observedPrompts = new ArrayList<>();
		ChatModel model = prompt -> {
			observedPrompts.add(prompt.getContents());
			return ChatResponseUtil.createPureResponse(modelCalls.getAndIncrement() == 0
					? "[\"" + REPORT_REVISION_AGENT + "\"]" : "[\"FINISH\"]");
		};
		Agent revisionAgent = capabilityAgent(REPORT_REVISION_AGENT, keyStrategyFactory, node_async(state -> Map.of(
				"revision_result", "revised",
				MULTI_AGENT_NEXT, FINISH,
				"messages", new UserMessage(capabilityResultMessage(REPORT_REVISION_AGENT, FINISH)))));
		DataAnalysisSupervisorAgent supervisor = new DataAnalysisSupervisorAgent(
				ReactAgent.builder().name(ROUTER_AGENT_NAME).model(model).includeContents(false).build(),
				List.of(revisionAgent), keyStrategyFactory);

		OverAllState state = supervisor.invoke(Map.of("messages",
				List.of(new SystemMessage("DATA_AGENT_CONTEXT kind=LATEST_REPORT"), new UserMessage("精简报告"))))
			.orElseThrow();

		assertEquals("revised", state.value("revision_result").orElseThrow());
		assertTrue(observedPrompts.get(0).contains("DATA_AGENT_CONTEXT kind=LATEST_REPORT"));
		assertTrue(observedPrompts.get(0).contains("精简报告"));
		assertEquals(1, modelCalls.get());
	}

	@Test
	void supervisorPreservesBusinessStateAcrossCapabilityAgents() throws Exception {
		KeyStrategyFactory keyStrategyFactory = keyStrategyFactory();
		AtomicInteger modelCalls = new AtomicInteger();
		ChatModel model = prompt -> ChatResponseUtil.createPureResponse(modelCalls.getAndIncrement() == 0
				? "[\"" + REQUEST_UNDERSTANDING_AGENT + "\"]" : modelCalls.get() == 2
						? "[\"" + DATA_PREPARATION_AGENT + "\"]" : "[\"FINISH\"]");
		Agent producer = capabilityAgent(REQUEST_UNDERSTANDING_AGENT, keyStrategyFactory, node_async(state -> Map.of(
				"business_state", "preserved",
				MULTI_AGENT_NEXT, DATA_PREPARATION_AGENT,
				"messages", new UserMessage(capabilityResultMessage(REQUEST_UNDERSTANDING_AGENT,
						DATA_PREPARATION_AGENT)))));
		Agent consumer = capabilityAgent(DATA_PREPARATION_AGENT, keyStrategyFactory, node_async(state -> Map.of(
				"consumer_result", state.value("business_state", "missing"),
				MULTI_AGENT_NEXT, FINISH,
				"messages", new UserMessage(capabilityResultMessage(DATA_PREPARATION_AGENT, FINISH)))));
		DataAnalysisSupervisorAgent supervisor = new DataAnalysisSupervisorAgent(
				ReactAgent.builder().name(ROUTER_AGENT_NAME).model(model).build(),
				List.of(producer, consumer), keyStrategyFactory);

		OverAllState state = supervisor.invoke(Map.of("messages", List.of(new UserMessage("analyze sales"))))
			.orElseThrow();

		assertEquals("preserved", state.value("consumer_result").orElseThrow());
		assertEquals(1, modelCalls.get());
	}

	@Test
	void humanReviewHandoffInterruptsBeforeSqlAndApprovalResumesExecution() throws Exception {
		KeyStrategyFactory keyStrategyFactory = keyStrategyFactory();
		AtomicInteger modelCalls = new AtomicInteger();
		ChatModel routerModel = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				modelCalls.incrementAndGet();
				return ChatResponseUtil.createPureResponse("[\"" + PLANNING_AGENT + "\"]");
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.just(call(prompt));
			}
		};
		ReactAgent router = ReactAgent.builder().name(ROUTER_AGENT_NAME).model(routerModel).build();
		Agent planning = capabilityAgent(PLANNING_AGENT, keyStrategyFactory, node_async(state -> Map.of(
				PLAN_NEXT_NODE, HUMAN_FEEDBACK_NODE,
				MULTI_AGENT_NEXT, HUMAN_FEEDBACK_NODE)));
		Agent humanReview = capabilityAgent(HUMAN_FEEDBACK_NODE, keyStrategyFactory, node_async(state -> Map.of(
				"human_review_executed", true,
				MULTI_AGENT_NEXT, SQL_AGENT)));
		Agent sql = capabilityAgent(SQL_AGENT, keyStrategyFactory, node_async(state -> Map.of(
				"sql_executed", true,
				MULTI_AGENT_NEXT, FINISH)));
		DataAnalysisSupervisorAgent supervisor = new DataAnalysisSupervisorAgent(router,
				List.of(planning, humanReview, sql), keyStrategyFactory);
		MemorySaver saver = MemorySaver.builder().build();
		supervisor.configure(CompileConfig.builder()
			.saverConfig(SaverConfig.builder().register(saver).build())
			.interruptBefore(HUMAN_FEEDBACK_INTERRUPT_NODE)
			.build());
		RunnableConfig runConfig = RunnableConfig.builder().threadId(UUID.randomUUID().toString()).build();

		List<NodeOutput> interrupted = supervisor
			.stream(Map.of("messages", List.of(new UserMessage("review before executing"))), runConfig)
			.collectList()
			.block();

		assertNotNull(interrupted);
		assertEquals(HUMAN_FEEDBACK_INTERRUPT_NODE, saver.get(runConfig).orElseThrow().getNextNodeId());
		assertTrue(interrupted.stream().noneMatch(output -> output.state().value("sql_executed").isPresent()));
		assertEquals(1, modelCalls.get());

		Map<String, Object> feedback = Map.of("feedback", true, "feedback_content", "Accept");
		RunnableConfig updatedConfig = supervisor.updateState(runConfig, Map.of(HUMAN_FEEDBACK_DATA, feedback));
		List<NodeOutput> resumed = supervisor.stream((Map<String, Object>) null, updatedConfig).collectList().block();

		assertNotNull(resumed);
		OverAllState finalState = resumed.get(resumed.size() - 1).state();
		assertEquals(true, finalState.value("human_review_executed").orElseThrow());
		assertEquals(true, finalState.value("sql_executed").orElseThrow());
		assertEquals(1, modelCalls.get());
	}

	private List<Agent> capabilityAgents(KeyStrategyFactory keyStrategyFactory) {
		return List.of(
				capabilityAgent(REQUEST_UNDERSTANDING_AGENT, keyStrategyFactory, true),
				capabilityAgent(DATA_PREPARATION_AGENT, keyStrategyFactory, false),
				capabilityAgent(PLANNING_AGENT, keyStrategyFactory, false),
				capabilityAgent(SQL_AGENT, keyStrategyFactory, false),
				capabilityAgent(PYTHON_AGENT, keyStrategyFactory, false),
				capabilityAgent(REPORT_AGENT, keyStrategyFactory, false),
				capabilityAgent(REPORT_REVISION_AGENT, keyStrategyFactory, false),
				capabilityAgent(HUMAN_FEEDBACK_NODE, keyStrategyFactory, false));
	}

	private Agent capabilityAgent(String name, KeyStrategyFactory keyStrategyFactory, boolean executable) {
		return new WorkflowCapabilityAgent(new AgentBasicInfo(name, name, "the test selects it"), () -> {
			StateGraph graph = new StateGraph(name + "_graph", keyStrategyFactory);
			if (executable) {
				graph.addNode(name + "_action", node_async(state -> Map.of(
						"request_agent_result", "executed",
						MULTI_AGENT_NEXT, FINISH,
						"messages", new UserMessage(DataAnalysisSupervisorAgent.capabilityResultMessage(name, FINISH)))));
			}
			else {
				graph.addNode(name + "_action", node_async(state -> Map.of()));
			}
			return graph.addEdge(START, name + "_action").addEdge(name + "_action", END);
		});
	}

	private Agent capabilityAgent(String name, KeyStrategyFactory keyStrategyFactory,
			com.alibaba.cloud.ai.graph.action.AsyncNodeAction action) {
		return new WorkflowCapabilityAgent(new AgentBasicInfo(name, name, "the test selects it"),
				() -> new StateGraph(name + "_graph", keyStrategyFactory)
					.addNode(name + "_action", action)
					.addEdge(START, name + "_action")
					.addEdge(name + "_action", END));
	}

	private KeyStrategyFactory keyStrategyFactory() {
		return () -> {
			Map<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put(INPUT_KEY, KeyStrategy.REPLACE);
			strategies.put("messages", KeyStrategy.APPEND);
			strategies.put(MULTI_AGENT_NEXT, KeyStrategy.REPLACE);
			strategies.put(SUPERVISOR_NEXT, KeyStrategy.REPLACE);
			strategies.put("request_agent_result", KeyStrategy.REPLACE);
			strategies.put("business_state", KeyStrategy.REPLACE);
			strategies.put("consumer_result", KeyStrategy.REPLACE);
			strategies.put("revision_result", KeyStrategy.REPLACE);
			strategies.put(PLAN_NEXT_NODE, KeyStrategy.REPLACE);
			strategies.put(HUMAN_FEEDBACK_DATA, KeyStrategy.REPLACE);
			strategies.put("human_review_executed", KeyStrategy.REPLACE);
			strategies.put("sql_executed", KeyStrategy.REPLACE);
			return strategies;
		};
	}

}
