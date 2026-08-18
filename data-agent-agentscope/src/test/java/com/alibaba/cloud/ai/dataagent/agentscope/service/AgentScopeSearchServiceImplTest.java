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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentRuntimePolicy;
import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentScopeAgentFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.api.AgentStreamRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.api.ConfirmationDecision;
import com.alibaba.cloud.ai.dataagent.agentscope.observability.LangfuseTraceService;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AgentScopeSearchServiceImplTest {

	private final DataAgentRegistryRepository repository = mock(DataAgentRegistryRepository.class);

	private final AgentScopeSearchServiceImpl service = new AgentScopeSearchServiceImpl(
			mock(AgentScopeAgentFactory.class), mock(AgentRuntimePolicy.class), repository,
			mock(ActiveRunRegistry.class), mock(LangfuseTraceService.class));

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
	void rejectionResumesAgentScopeOnceWithDeniedConfirmResult() {
		AgentScopeAgentFactory agentFactory = mock(AgentScopeAgentFactory.class);
		ActiveRunRegistry runRegistry = mock(ActiveRunRegistry.class);
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
		when(runRegistry.register("run-1", "conversation-1")).thenReturn(Mono.never());
		when(traceService.trace(any(AgentStreamRequest.class), anyString(), anyString(), anyString(), anyBoolean(),
				any()))
			.thenAnswer(invocation -> invocation.getArgument(5));
		AgentScopeSearchServiceImpl rejectionService = new AgentScopeSearchServiceImpl(agentFactory,
				mock(AgentRuntimePolicy.class), repository, runRegistry, traceService);

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

}
