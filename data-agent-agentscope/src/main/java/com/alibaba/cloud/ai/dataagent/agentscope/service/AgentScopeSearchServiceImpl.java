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

import static com.alibaba.cloud.ai.dataagent.agentscope.constant.DataAgentRuntimeConstants.GLOBAL_USER_ID;

import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentRuntimePolicy;
import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentScopeAgentFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.agent.PermissionContextUpdates;
import com.alibaba.cloud.ai.dataagent.agentscope.api.AgentStreamRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.api.ConfirmationDecision;
import com.alibaba.cloud.ai.dataagent.agentscope.observability.LangfuseTraceService;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
public class AgentScopeSearchServiceImpl implements AgentScopeSearchService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AgentScopeSearchServiceImpl.class);

	static final String RUN_STARTED = "run_started";

	static final String STREAM_COMPLETED = "stream_completed";

	static final String STREAM_ERROR = "stream_error";

	private final AgentScopeAgentFactory agentFactory;

	private final AgentRuntimePolicy runtimePolicy;

	private final DataAgentRegistryRepository repository;

	private final LangfuseTraceService langfuseTraceService;

	public AgentScopeSearchServiceImpl(AgentScopeAgentFactory agentFactory, AgentRuntimePolicy runtimePolicy,
			DataAgentRegistryRepository repository, LangfuseTraceService langfuseTraceService) {
		this.agentFactory = agentFactory;
		this.runtimePolicy = runtimePolicy;
		this.repository = repository;
		this.langfuseTraceService = langfuseTraceService;
	}

	@Override
	public Flux<ServerSentEvent<AgentEvent>> streamSearch(AgentStreamRequest request) {
		RequestContext context;
		try {
			context = normalize(request);
		}
		catch (RuntimeException ex) {
			String agentId = request == null ? null : request.agentId();
			String runId = request == null ? null : request.runId();
			String conversationId = request == null ? null : request.conversationId();
			LOGGER.warn("Rejected AgentScope stream request, agentId={}, conversationId={}, runId={}", agentId,
					conversationId, runId, ex);
			return errorFlux(agentId, runId, ex);
		}
		Flux<ServerSentEvent<AgentEvent>> stream = Flux.defer(() -> executionStream(context))
			.subscribeOn(Schedulers.boundedElastic())
			.doOnError(ex -> LOGGER.error("AgentScope stream failed, agentId={}, conversationId={}, runId={}",
					context.agentIdText(), context.conversationId(), context.runId(), ex))
			.onErrorResume(ex -> errorFlux(context.agentIdText(), context.runId(), ex));
		return langfuseTraceService.trace(request, context.agentIdText(), context.conversationId(), context.runId(),
				context.hitl(), stream);
	}

	@Override
	public void stop(String conversationId) {
		if (!StringUtils.hasText(conversationId)) {
			return;
		}
		repository.findConversationAgentId(conversationId).ifPresent(agentId -> agentFactory.get(agentId)
			.getDelegate()
			.interrupt(runtimeContext(conversationId)));
	}

	private Flux<ServerSentEvent<AgentEvent>> executionStream(RequestContext context) {
		HarnessAgent agent = agentFactory.get(context.agentId());
		RuntimeContext runtime = runtimeContext(context);
		ExecutionState state = new ExecutionState();
		Msg input;
		if (context.resuming()) {
			input = resumeMessage(agent, runtime, context);
		}
		else {
			runtimePolicy.apply(context.agentId(), agent, runtime, context.hitl(), context.nl2sqlOnly());
			input = new UserMessage(executionInput(context));
		}
		Flux<ServerSentEvent<AgentEvent>> agentEvents = streamAgent(agent, input, runtime, context, state);
		return Flux.concat(Flux.just(sse(context, runStartedEvent(context))), agentEvents,
				Flux.defer(() -> completeExecution(context, state)));
	}

	private Flux<ServerSentEvent<AgentEvent>> streamAgent(HarnessAgent agent, Msg input, RuntimeContext runtime,
			RequestContext context, ExecutionState state) {
		return agent.streamEvents(input, runtime)
			.map(state::observe)
			.filter(AgentScopeSearchServiceImpl::isClientVisibleEvent)
			.map(event -> sse(context, event));
	}

	static boolean isClientVisibleEvent(AgentEvent event) {
		return event.getType() != AgentEventType.THINKING_BLOCK_DELTA;
	}

	private Flux<ServerSentEvent<AgentEvent>> completeExecution(RequestContext context, ExecutionState state) {
		if (!state.paused() && !state.interrupted() && !context.nl2sqlOnly()
				&& StringUtils.hasText(state.finalResult())) {
			repository.saveReport(context.conversationId(), context.agentId(), context.query(), state.finalResult());
		}
		return Flux.just(sse(context,
				new CustomEvent(STREAM_COMPLETED, Map.of("runId", context.runId(), "paused", state.paused(),
						"interrupted", state.interrupted()))));
	}

	private ServerSentEvent<AgentEvent> sse(RequestContext context, AgentEvent event) {
		return ServerSentEvent.builder(event).id(context.runId()).build();
	}

	private CustomEvent runStartedEvent(RequestContext context) {
		return new CustomEvent(RUN_STARTED, Map.of("agentId", context.agentIdText(), "conversationId",
				context.conversationId(), "runId", context.runId()));
	}

	private RuntimeContext runtimeContext(RequestContext context) {
		return runtimeContext(context.conversationId());
	}

	private RuntimeContext runtimeContext(String conversationId) {
		return RuntimeContext.builder().sessionId(conversationId).userId(GLOBAL_USER_ID).build();
	}

	private RequestContext normalize(AgentStreamRequest request) {
		if (request == null || !StringUtils.hasText(request.agentId()) || !StringUtils.hasText(request.query())) {
			throw new IllegalArgumentException("agentId and query are required");
		}
		long agentId;
		try {
			agentId = Long.parseLong(request.agentId());
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException("agentId must be numeric", ex);
		}
		if (agentId <= 0) {
			throw new IllegalArgumentException("agentId must be positive");
		}
		boolean resuming = request.confirmation() != null;
		if (resuming && !StringUtils.hasText(request.runId())) {
			throw new IllegalArgumentException("runId is required when submitting a confirmation");
		}
		String conversationId = StringUtils.hasText(request.conversationId()) ? request.conversationId()
				: StringUtils.hasText(request.runId()) ? request.runId() : UUID.randomUUID().toString();
		repository.findConversationAgentId(conversationId)
			.filter(ownerAgentId -> ownerAgentId != agentId)
			.ifPresent(ownerAgentId -> {
				throw new IllegalArgumentException("conversationId does not belong to agentId");
			});
		String runId = resuming ? request.runId() : UUID.randomUUID().toString();
		boolean hitl = request.hitl() || resuming || repository.requiresHumanApproval(agentId);
		ApprovalScope approvalScope = ApprovalScope.from(request.confirmation());
		return new RequestContext(request.agentId(), agentId, conversationId, runId, request.query().trim(), hitl,
				resuming, request.confirmation(), request.nl2sqlOnly(), approvalScope);
	}

	private String executionInput(RequestContext context) {
		StringBuilder input = new StringBuilder();
		input.append(context.nl2sqlOnly() ? "MODE: NL2SQL_ONLY\n" : "MODE: DATA_ANALYSIS\n");
		input.append("用户请求：\n").append(context.query());
		return input.toString();
	}

	private Msg resumeMessage(HarnessAgent agent, RuntimeContext runtime, RequestContext context) {
		List<ToolUseBlock> pendingTools = pendingTools(agent, runtime);
		if (pendingTools.isEmpty()) {
			throw new IllegalStateException("No pending AgentScope HITL operation was found for this conversation");
		}
		boolean approved = context.confirmation() != ConfirmationDecision.REJECT;
		if (approved) {
			applySessionApproval(agent, runtime, pendingTools, context.approvalScope());
		}
		List<ConfirmResult> results = pendingTools.stream().map(tool -> new ConfirmResult(approved, tool)).toList();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(Msg.METADATA_CONFIRM_RESULTS, results);
		return Msg.builder()
			.name("user")
			.role(MsgRole.USER)
			.textContent(approved ? "批准执行" : "拒绝执行")
			.metadata(metadata)
			.build();
	}

	private void applySessionApproval(HarnessAgent agent, RuntimeContext runtime, List<ToolUseBlock> pendingTools,
			ApprovalScope approvalScope) {
		if (approvalScope == ApprovalScope.ONCE) {
			return;
		}
		if (approvalScope == ApprovalScope.ALL_FOR_SESSION) {
			AgentState state = agent.getDelegate().getAgentState(runtime);
			PermissionContextState bypass = PermissionContextUpdates
				.sessionStateBuilder(state.getPermissionContext(), PermissionMode.BYPASS)
				.build();
			agent.getDelegate()
				.replacePermissionContext(runtime.getUserId(), runtime.getSessionId(), bypass);
			return;
		}
		allowToolsForSession(agent, runtime, pendingTools);
	}

	private void allowToolsForSession(HarnessAgent agent, RuntimeContext runtime, List<ToolUseBlock> pendingTools) {
		AgentState state = agent.getDelegate().getAgentState(runtime);
		List<String> approvedTools = pendingTools.stream().map(ToolUseBlock::getName).distinct().toList();
		PermissionContextState updated = PermissionContextUpdates.allowToolsForSession(state.getPermissionContext(),
				approvedTools);
		agent.getDelegate().replacePermissionContext(runtime.getUserId(), runtime.getSessionId(), updated);
	}

	private List<ToolUseBlock> pendingTools(HarnessAgent agent, RuntimeContext runtime) {
		AgentState state = agent.getDelegate().getAgentState(runtime);
		List<Msg> messages = state.getContext();
		for (int index = messages.size() - 1; index >= 0; index--) {
			List<ToolUseBlock> asking = messages.get(index)
				.getContentBlocks(ToolUseBlock.class)
				.stream()
				.filter(tool -> tool.getState() == ToolCallState.ASKING)
				.toList();
			if (!asking.isEmpty()) {
				return asking;
			}
		}
		return List.of();
	}

	private Flux<ServerSentEvent<AgentEvent>> errorFlux(String agentId, String runId, Throwable error) {
		String safeRunId = StringUtils.hasText(runId) ? runId : UUID.randomUUID().toString();
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("agentId", agentId == null ? "" : agentId);
		value.put("runId", safeRunId);
		value.put("message", rootMessage(error));
		return Flux
			.just(ServerSentEvent.<AgentEvent>builder(new CustomEvent(STREAM_ERROR, value)).id(safeRunId).build());
	}

	private String rootMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return StringUtils.hasText(current.getMessage()) ? current.getMessage() : current.getClass().getSimpleName();
	}

	static final class ExecutionState {

		private static final String KNOWLEDGE_SEARCH_TOOL = "search_knowledge_base";

		private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

		private static final Pattern CITATION_MARKER_PATTERN = Pattern.compile("[ \\t]*\\[\\[cite:([^]\\r\\n]+)]]");

		private String finalResult = "";

		private final Map<String, StringBuilder> knowledgeResults = new LinkedHashMap<>();

		private boolean paused;

		private boolean interrupted;

		AgentEvent observe(AgentEvent event) {
			if (event instanceof RequireUserConfirmEvent) {
				paused = true;
			}
			if (event instanceof ToolResultTextDeltaEvent toolResult
					&& KNOWLEDGE_SEARCH_TOOL.equals(toolResult.getToolCallName())) {
				knowledgeResults.computeIfAbsent(toolResult.getToolCallId(), ignored -> new StringBuilder())
					.append(toolResult.getDelta());
			}
			if (event instanceof AgentResultEvent resultEvent && resultEvent.getResult() != null) {
				String answer = appendCitations(resultEvent.getResult().getTextContent());
				Msg decoratedResult = resultEvent.getResult()
					.withContent(List.of(TextBlock.builder().text(answer).build()));
				AgentResultEvent decoratedEvent = new AgentResultEvent(event.getId(), event.getCreatedAt(), decoratedResult);
				decoratedEvent.withSource(event.getSource()).withMetadata(event.getMetadata());
				finalResult = answer;
				interrupted = resultEvent.getResult().getGenerateReason() == GenerateReason.INTERRUPTED;
				return decoratedEvent;
			}
			return event;
		}

		private String appendCitations(String answer) {
			Map<String, DocumentCitation> candidates = new LinkedHashMap<>();
			for (StringBuilder result : knowledgeResults.values()) {
				for (DocumentCitation citation : parseCitations(result.toString())) {
					candidates.putIfAbsent(citation.citationId(), citation);
				}
			}
			String rawAnswer = answer == null ? "" : answer;
			Matcher markerMatcher = CITATION_MARKER_PATTERN.matcher(rawAnswer);
			Set<DocumentCitation> citations = new LinkedHashSet<>();
			StringBuilder cleanedAnswer = new StringBuilder();
			while (markerMatcher.find()) {
				DocumentCitation citation = candidates.get(markerMatcher.group(1).trim());
				if (citation != null) citations.add(citation);
				markerMatcher.appendReplacement(cleanedAnswer, "");
			}
			markerMatcher.appendTail(cleanedAnswer);
			String cleaned = cleanedAnswer.toString().stripTrailing();
			if (citations.isEmpty()) return cleaned;
			StringBuilder decorated = new StringBuilder(cleaned);
			decorated.append("\n\n### 参考文档\n");
			for (DocumentCitation citation : citations) {
				decorated.append("- [")
					.append(escapeMarkdownLabel(citation.filename()))
					.append(" · 第")
					.append(citation.pageNumber())
					.append("页](")
					.append(citation.url())
					.append(")\n");
			}
			return decorated.toString().stripTrailing();
		}

		private List<DocumentCitation> parseCitations(String toolResult) {
			try {
				JsonNode matches = OBJECT_MAPPER.readTree(toolResult).path("matches");
				if (!matches.isArray()) return List.of();
				List<DocumentCitation> citations = new java.util.ArrayList<>();
				for (JsonNode match : matches) {
					JsonNode citation = match.path("citation");
					String citationId = citation.path("citationId").asText("");
					int knowledgeId = citation.path("knowledgeId").asInt(0);
					int pageNumber = citation.path("pageNumber").asInt(0);
					String filename = citation.path("filename").asText("");
					String url = citation.path("url").asText("");
					if (StringUtils.hasText(citationId) && knowledgeId > 0 && pageNumber > 0 && StringUtils.hasText(filename)
							&& StringUtils.hasText(url)) {
						citations.add(new DocumentCitation(citationId, knowledgeId, filename, pageNumber, url));
					}
				}
				return citations;
			}
			catch (Exception ignored) {
				return List.of();
			}
		}

		private String escapeMarkdownLabel(String value) {
			return value.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
		}

		String finalResult() {
			return finalResult;
		}

		boolean paused() {
			return paused;
		}

		boolean interrupted() {
			return interrupted;
		}

		private record DocumentCitation(String citationId, int knowledgeId, String filename, int pageNumber, String url) { }

	}

}
