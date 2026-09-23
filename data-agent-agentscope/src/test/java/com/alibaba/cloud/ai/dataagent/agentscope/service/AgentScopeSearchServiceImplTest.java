/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.agentscope.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentRuntimePolicy;
import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentScopeAgentFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.api.AgentStreamRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.api.ConfirmationDecision;
import com.alibaba.cloud.ai.dataagent.agentscope.observability.LangfuseTraceService;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

class AgentScopeSearchServiceImplTest {

	private final DataAgentRegistryRepository repository = mock(DataAgentRegistryRepository.class);

	private final AgentScopeSearchServiceImpl service = new AgentScopeSearchServiceImpl(
			mock(AgentScopeAgentFactory.class), mock(AgentRuntimePolicy.class), repository,
			mock(LangfuseTraceService.class));

	@Test
	void filtersThinkingDeltasFromTheClientEventStream() {
		assertThat(AgentScopeSearchServiceImpl
			.isClientVisibleEvent(new ThinkingBlockDeltaEvent("reply-1", "thinking-1", "internal")))
			.isFalse();
		assertThat(AgentScopeSearchServiceImpl
			.isClientVisibleEvent(new TextBlockDeltaEvent("reply-1", "text-1", "visible")))
			.isTrue();
	}

	@Test
	void rejectsNonPositiveAgentIdWithNativeCustomEvent() {
		ServerSentEvent<AgentEvent> event = service
			.streamSearch(new AgentStreamRequest("0", "conversation-1", null, "查询销售额", false, null, false))
			.blockFirst();

		assertThat(event).isNotNull();
		assertThat(event.data()).isInstanceOf(CustomEvent.class);
		CustomEvent error = (CustomEvent) event.data();
		assertThat(error.getName()).isEqualTo(AgentScopeSearchServiceImpl.STREAM_ERROR);
		assertThat(error.getValue()).containsEntry("message", "agentId must be positive");
	}

	@Test
	void rejectsConversationOwnedByAnotherAgent() {
		when(repository.findConversationAgentId("conversation-1")).thenReturn(Optional.of(8L));

		ServerSentEvent<AgentEvent> event = service
			.streamSearch(new AgentStreamRequest("7", "conversation-1", null, "查询销售额", false, null, false))
			.blockFirst();

		CustomEvent error = (CustomEvent) event.data();
		assertThat(error.getValue()).containsEntry("message", "conversationId does not belong to agentId");
	}

	@Test
	void confirmationRequiresRunId() {
		ServerSentEvent<AgentEvent> event = service
			.streamSearch(new AgentStreamRequest("7", "conversation-1", null, "查询销售额", true,
					ConfirmationDecision.REJECT, false))
			.blockFirst();

		CustomEvent error = (CustomEvent) event.data();
		assertThat(error.getValue()).containsEntry("message", "runId is required when submitting a confirmation");
	}

	@Test
	void stopInterruptsTheAgentScopeSessionOwnedByConversation() {
		AgentScopeAgentFactory agentFactory = mock(AgentScopeAgentFactory.class);
		HarnessAgent agent = mock(HarnessAgent.class);
		ReActAgent delegate = mock(ReActAgent.class);
		when(repository.findConversationAgentId("conversation-1")).thenReturn(Optional.of(7L));
		when(agentFactory.get(7L)).thenReturn(agent);
		when(agent.getDelegate()).thenReturn(delegate);
		AgentScopeSearchServiceImpl sessionService = new AgentScopeSearchServiceImpl(agentFactory,
				mock(AgentRuntimePolicy.class), repository, mock(LangfuseTraceService.class));

		sessionService.stop("conversation-1");

		ArgumentCaptor<RuntimeContext> contextCaptor = ArgumentCaptor.forClass(RuntimeContext.class);
		verify(delegate).interrupt(contextCaptor.capture());
		assertThat(contextCaptor.getValue().getUserId()).isEqualTo("1");
		assertThat(contextCaptor.getValue().getSessionId()).isEqualTo("conversation-1");
	}

	@Test
	void interruptedResultCompletesWithoutSavingAReport() {
		AgentScopeAgentFactory agentFactory = mock(AgentScopeAgentFactory.class);
		LangfuseTraceService traceService = mock(LangfuseTraceService.class);
		HarnessAgent agent = mock(HarnessAgent.class);
		Msg interrupted = Msg.builder()
			.role(MsgRole.ASSISTANT)
			.textContent("I noticed that you have interrupted me.")
			.generateReason(GenerateReason.INTERRUPTED)
			.build();
		when(agentFactory.get(7L)).thenReturn(agent);
		when(agent.streamEvents(any(Msg.class), any(RuntimeContext.class)))
			.thenReturn(Flux.just(new AgentResultEvent(interrupted)));
		when(traceService.trace(any(AgentStreamRequest.class), anyString(), anyString(), anyString(), anyBoolean(),
				any()))
			.thenAnswer(invocation -> invocation.getArgument(5));
		AgentScopeSearchServiceImpl interruptedService = new AgentScopeSearchServiceImpl(agentFactory,
				mock(AgentRuntimePolicy.class), repository, traceService);

		List<ServerSentEvent<AgentEvent>> events = interruptedService
			.streamSearch(new AgentStreamRequest("7", "conversation-1", null, "查询销售额", false, null, false))
			.collectList()
			.block();

		verify(repository, never()).saveReport(anyString(), eq(7L), anyString(), anyString());
		CustomEvent completed = (CustomEvent) events.get(events.size() - 1).data();
		assertThat(completed.getValue()).containsEntry("interrupted", true);
	}

	@Test
	void appendsOnlyPdfCitationsExplicitlyUsedByFinalAnswer() {
		AgentScopeSearchServiceImpl.ExecutionState state = new AgentScopeSearchServiceImpl.ExecutionState();
		String toolResult = """
				{"matches":[
				  {"citation":{"citationId":"kb-21-used","knowledgeId":21,"filename":"SLT191-2025.pdf","pageNumber":18,"url":"/data-agent-management/api/agent-knowledge/21/review/source#page=18"}},
				  {"citation":{"citationId":"kb-22-unused","knowledgeId":22,"filename":"无关资料.pdf","pageNumber":9,"url":"/data-agent-management/api/agent-knowledge/22/review/source#page=9"}}
				]}
				""";
		state.observe(new ToolResultTextDeltaEvent("reply-1", "tool-1", "search_knowledge_base", toolResult));
		Msg answer = Msg.builder()
			.role(MsgRole.ASSISTANT)
			.textContent("混凝土结构应满足相关规定。 [[cite:kb-21-used]]")
			.build();

		AgentEvent decorated = state.observe(new AgentResultEvent(answer));

		assertThat(decorated).isInstanceOfSatisfying(AgentResultEvent.class,
				event -> assertThat(event.getResult().getTextContent())
					.isEqualTo("混凝土结构应满足相关规定。\n\n### 参考文档\n"
							+ "- [SLT191-2025.pdf · 第18页](/data-agent-management/api/agent-knowledge/21/review/source#page=18)"));
		assertThat(state.finalResult()).isEqualTo(((AgentResultEvent) decorated).getResult().getTextContent());
		assertThat(state.finalResult()).doesNotContain("无关资料.pdf", "[[cite:");
	}

	@Test
	void omitsReferencesWhenAnswerUsesNoValidCitation() {
		AgentScopeSearchServiceImpl.ExecutionState state = new AgentScopeSearchServiceImpl.ExecutionState();
		String toolResult = """
				{"matches":[{"citation":{"citationId":"kb-21-candidate","knowledgeId":21,"filename":"候选资料.pdf","pageNumber":18,"url":"/data-agent-management/api/agent-knowledge/21/review/source#page=18"}}]}
				""";
		state.observe(new ToolResultTextDeltaEvent("reply-1", "tool-1", "search_knowledge_base", toolResult));
		Msg answer = Msg.builder()
			.role(MsgRole.ASSISTANT)
			.textContent("这是未使用知识库内容的回答。 [[cite:kb-fabricated]]")
			.build();

		AgentEvent decorated = state.observe(new AgentResultEvent(answer));

		assertThat(decorated).isInstanceOfSatisfying(AgentResultEvent.class,
				event -> assertThat(event.getResult().getTextContent()).isEqualTo("这是未使用知识库内容的回答。"));
		assertThat(state.finalResult()).doesNotContain("参考文档", "候选资料.pdf", "kb-fabricated");
	}

	@Test
	void rejectionResumesAgentScopeOnceWithDeniedConfirmResult() {
		AgentScopeAgentFactory agentFactory = mock(AgentScopeAgentFactory.class);
		LangfuseTraceService traceService = mock(LangfuseTraceService.class);
		HarnessAgent agent = mock(HarnessAgent.class);
		ReActAgent delegate = mock(ReActAgent.class);
		ToolUseBlock pendingTool = ToolUseBlock.builder()
			.id("tool-1")
			.name("execute_read_only_sql")
			.input(Map.of("sql", "select 1"))
			.state(ToolCallState.ASKING)
			.build();
		Msg pendingMessage = Msg.builder().role(MsgRole.ASSISTANT).content(pendingTool).build();
		AgentState agentState = AgentState.builder()
			.sessionId("conversation-1")
			.userId("7")
			.context(List.of(pendingMessage))
			.build();
		when(agentFactory.get(7L)).thenReturn(agent);
		when(agent.getDelegate()).thenReturn(delegate);
		when(delegate.getAgentState(any(RuntimeContext.class))).thenReturn(agentState);
		when(agent.streamEvents(any(Msg.class), any(RuntimeContext.class))).thenReturn(Flux.empty());
		when(traceService.trace(any(AgentStreamRequest.class), anyString(), anyString(), anyString(), anyBoolean(),
				any()))
			.thenAnswer(invocation -> invocation.getArgument(5));
		AgentScopeSearchServiceImpl rejectionService = new AgentScopeSearchServiceImpl(agentFactory,
				mock(AgentRuntimePolicy.class), repository, traceService);

		rejectionService
			.streamSearch(new AgentStreamRequest("7", "conversation-1", "run-1", "查询销售额", true,
					ConfirmationDecision.REJECT, false))
			.collectList()
			.block();

		ArgumentCaptor<Msg> inputCaptor = ArgumentCaptor.forClass(Msg.class);
		verify(agent, times(1)).streamEvents(inputCaptor.capture(), any(RuntimeContext.class));
		Object metadata = inputCaptor.getValue().getMetadata().get(Msg.METADATA_CONFIRM_RESULTS);
		assertThat(metadata).isInstanceOf(List.class);
		ConfirmResult result = (ConfirmResult) ((List<?>) metadata).get(0);
		assertThat(result.isConfirmed()).isFalse();
		assertThat(result.getToolCall().getId()).isEqualTo("tool-1");
	}

	@Test
	void approveAllForSessionInstallsCleanBypassContext() {
		ApprovalFixture fixture = approvalFixture();

		fixture.service()
			.streamSearch(new AgentStreamRequest("7", "conversation-1", "run-1", "查询销售额", true,
					ConfirmationDecision.APPROVE_ALL_FOR_SESSION, false))
			.collectList()
			.block();

		ArgumentCaptor<PermissionContextState> contextCaptor = ArgumentCaptor.forClass(PermissionContextState.class);
		verify(fixture.delegate()).replacePermissionContext(eq("1"), eq("conversation-1"), contextCaptor.capture());
		assertThat(contextCaptor.getValue().getMode()).isEqualTo(PermissionMode.BYPASS);
		assertThat(contextCaptor.getValue().getAskRules()).isEmpty();
	}

	@Test
	void approveToolForSessionReplacesAskRuleWithPersistedAllowRule() {
		ApprovalFixture fixture = approvalFixture();

		fixture.service()
			.streamSearch(new AgentStreamRequest("7", "conversation-1", "run-1", "查询销售额", true,
					ConfirmationDecision.APPROVE_TOOL_FOR_SESSION, false))
			.collectList()
			.block();

		ArgumentCaptor<PermissionContextState> contextCaptor = ArgumentCaptor.forClass(PermissionContextState.class);
		verify(fixture.delegate()).replacePermissionContext(eq("1"), eq("conversation-1"), contextCaptor.capture());
		PermissionContextState updated = contextCaptor.getValue();
		assertThat(updated.getAskRules()).doesNotContainKey("execute_read_only_sql");
		assertThat(updated.getAllowRules().get("execute_read_only_sql"))
			.anySatisfy(rule -> assertThat(rule.source()).isEqualTo("session"));
	}

	private ApprovalFixture approvalFixture() {
		AgentScopeAgentFactory agentFactory = mock(AgentScopeAgentFactory.class);
		LangfuseTraceService traceService = mock(LangfuseTraceService.class);
		HarnessAgent agent = mock(HarnessAgent.class);
		ReActAgent delegate = mock(ReActAgent.class);
		ToolUseBlock pendingTool = ToolUseBlock.builder()
			.id("tool-1")
			.name("execute_read_only_sql")
			.input(Map.of("sql", "select 1"))
			.state(ToolCallState.ASKING)
			.build();
		Msg pendingMessage = Msg.builder().role(MsgRole.ASSISTANT).content(pendingTool).build();
		PermissionRule askRule = new PermissionRule("execute_read_only_sql", null, PermissionBehavior.ASK,
				"agent-tool-config");
		PermissionContextState permissionContext = PermissionContextState.builder()
			.addAskRule("execute_read_only_sql", askRule)
			.build();
		AgentState agentState = AgentState.builder()
			.sessionId("conversation-1")
			.userId("7")
			.context(List.of(pendingMessage))
			.permissionContext(permissionContext)
			.build();
		when(agentFactory.get(7L)).thenReturn(agent);
		when(agent.getDelegate()).thenReturn(delegate);
		when(delegate.getAgentState(any(RuntimeContext.class))).thenReturn(agentState);
		when(agent.streamEvents(any(Msg.class), any(RuntimeContext.class))).thenReturn(Flux.empty());
		when(traceService.trace(any(AgentStreamRequest.class), anyString(), anyString(), anyString(), anyBoolean(),
				any()))
			.thenAnswer(invocation -> invocation.getArgument(5));
		AgentScopeSearchServiceImpl approvalService = new AgentScopeSearchServiceImpl(agentFactory,
				mock(AgentRuntimePolicy.class), repository, traceService);
		return new ApprovalFixture(approvalService, delegate);
	}

	private record ApprovalFixture(AgentScopeSearchServiceImpl service, ReActAgent delegate) {
	}

}
