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

import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentScopeAgentFactory;
import com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentRuntimePolicy;
import static com.alibaba.cloud.ai.dataagent.agentscope.agent.AgentRuntimePolicy.SESSION_BYPASS_MARKER;
import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.api.TextType;
import com.alibaba.cloud.ai.dataagent.agentscope.observability.LangfuseTraceService;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AgentScopeSearchServiceImpl implements AgentScopeSearchService {

	static final String APPROVE_ONCE = "HITL_APPROVE_ONCE";

	static final String APPROVE_TOOL_FOR_SESSION = "HITL_APPROVE_TOOL_FOR_SESSION";

	static final String APPROVE_ALL_FOR_SESSION = "HITL_APPROVE_ALL_FOR_SESSION";

	private final AgentScopeAgentFactory agentFactory;
	private final AgentRuntimePolicy runtimePolicy;

	private final DataAgentRegistryRepository repository;

	private final ActiveRunRegistry runRegistry;

	private final LangfuseTraceService langfuseTraceService;

	public AgentScopeSearchServiceImpl(AgentScopeAgentFactory agentFactory, AgentRuntimePolicy runtimePolicy,
			DataAgentRegistryRepository repository, ActiveRunRegistry runRegistry,
			LangfuseTraceService langfuseTraceService) {
		this.agentFactory = agentFactory;
		this.runtimePolicy = runtimePolicy;
		this.repository = repository;
		this.runRegistry = runRegistry;
		this.langfuseTraceService = langfuseTraceService;
	}

	@Override
	public Flux<ServerSentEvent<GraphNodeResponse>> streamSearch(GraphRequest request) {
		RequestContext context;
		try {
			context = normalize(request);
		}
		catch (RuntimeException ex) {
			return errorFlux(request.agentId(), request.threadId(), ex);
		}
		Mono<Void> cancelSignal = runRegistry.register(context.threadId(), context.conversationId());
		Flux<ServerSentEvent<GraphNodeResponse>> stream = Flux.defer(() -> executionStream(context))
			.subscribeOn(Schedulers.boundedElastic())
			.onErrorResume(ex -> errorFlux(context.agentIdText(), context.threadId(), ex));
		Flux<ServerSentEvent<GraphNodeResponse>> cancellable = stream.takeUntilOther(cancelSignal)
			.doFinally(signal -> runRegistry.remove(context.threadId()));
		return langfuseTraceService.trace(request, context.agentIdText(), context.conversationId(), context.threadId(),
				context.hitl(), cancellable);
	}

	@Override
	public void stop(String conversationId, String threadId) {
		runRegistry.stop(conversationId, threadId);
	}

	private Flux<ServerSentEvent<GraphNodeResponse>> executionStream(RequestContext context) {
		HarnessAgent agent = agentFactory.get(context.agentId());
		AtomicReference<String> result = new AtomicReference<>("");
		AtomicBoolean paused = new AtomicBoolean();
		RuntimeContext runtime = runtimeContext(context);
		if (!context.resuming()) {
			runtimePolicy.apply(context.agentId(), agent, runtime, context.hitl(), context.nl2sqlOnly());
		}
		Msg input = context.resuming() ? resumeMessage(agent, runtime, context)
				: new UserMessage(executionInput(context));
		boolean emitConfirmationResult = !context.resuming() || !context.rejectedPlan();
		Flux<ServerSentEvent<GraphNodeResponse>> events = streamAgent(agent, input, runtime, context, result, paused,
				emitConfirmationResult);
		if (context.resuming() && context.rejectedPlan()) {
			events = events.concatWith(Flux.defer(() -> {
				result.set("");
				return streamAgent(agent, new UserMessage(revisionInput(context)), runtime, context, result, paused, true);
			}));
		}
		return events.concatWith(Flux.defer(() -> {
			if (!paused.get() && !context.nl2sqlOnly() && StringUtils.hasText(result.get())) {
				repository.saveReport(context.conversationId(), context.agentId(), context.query(), result.get());
			}
			return Flux.just(completeEvent(context));
		})).onErrorResume(ex -> errorFlux(context.agentIdText(), context.threadId(), ex));
	}

	private Flux<ServerSentEvent<GraphNodeResponse>> streamAgent(HarnessAgent agent, Msg input,
			RuntimeContext runtime, RequestContext context, AtomicReference<String> result, AtomicBoolean paused,
			boolean emitFinalAnswer) {
		return agent.streamEvents(input, runtime)
			.handle((event, sink) -> mapEvent(context, event, result, paused, emitFinalAnswer).ifPresent(sink::next));
	}

	private java.util.Optional<ServerSentEvent<GraphNodeResponse>> mapEvent(RequestContext context, AgentEvent event,
			AtomicReference<String> finalResult, AtomicBoolean paused, boolean emitFinalAnswer) {
		if (event instanceof TextBlockDeltaEvent delta && StringUtils.hasText(delta.getDelta())) {
			String node = context.nl2sqlOnly() ? "SqlGenerateNode" : "ReportGeneratorNode";
			TextType type = context.nl2sqlOnly() ? TextType.SQL : TextType.MARK_DOWN;
			return java.util.Optional.of(ServerSentEvent.builder(GraphNodeResponse.output(context.agentIdText(),
					context.threadId(), stepId(context, node), node, type, delta.getDelta())).build());
		}
		if (event instanceof ToolCallStartEvent toolCall) {
			String node = toolNode(toolCall.getToolCallName());
			String text = switch (toolCall.getToolCallName()) {
				case "search_knowledge_base" -> "正在检索知识库…";
				case "inspect_data_source" -> "正在检查数据源结构…";
				case "search_products" -> "正在查询商品…";
				case "place_order" -> context.hitl() ? "下单操作正在等待确认…" : "正在提交订单…";
				default -> context.hitl() ? "已生成只读查询，正在检查执行权限…" : "正在执行只读查询…";
			};
			return java.util.Optional.of(ServerSentEvent.builder(GraphNodeResponse.output(context.agentIdText(),
					context.threadId(), stepId(context, node), node, TextType.TEXT, text)).build());
		}
		if (event instanceof RequireUserConfirmEvent confirmation) {
			paused.set(true);
			return Optional.of(ServerSentEvent.builder(GraphNodeResponse.humanFeedbackRequired(
					context.agentIdText(), context.threadId(), approvalText(confirmation.getToolCalls()))).build());
		}
		if (event instanceof AgentResultEvent agentResult) {
			String text = agentResult.getResult() == null ? "" : agentResult.getResult().getTextContent();
			if (emitFinalAnswer) {
				finalResult.set(text == null ? "" : text);
			}
			if (emitFinalAnswer && StringUtils.hasText(text)) {
				return java.util.Optional.of(ServerSentEvent.builder(
						GraphNodeResponse.finalAnswer(context.agentIdText(), context.threadId(), text)).build());
			}
		}
		return java.util.Optional.empty();
	}

	private RuntimeContext runtimeContext(RequestContext context) {
		return RuntimeContext.builder()
			.sessionId(context.conversationId())
			.userId(context.agentIdText())
			.build();
	}

	private RequestContext normalize(GraphRequest request) {
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
		boolean resuming = StringUtils.hasText(request.humanFeedbackContent());
		if (resuming && !StringUtils.hasText(request.threadId())) {
			throw new IllegalArgumentException("threadId is required when submitting human feedback");
		}
		String conversationId = StringUtils.hasText(request.conversationId()) ? request.conversationId()
				: StringUtils.hasText(request.threadId()) ? request.threadId() : UUID.randomUUID().toString();
		String threadId = resuming ? request.threadId() : UUID.randomUUID().toString();
		boolean hitl = request.humanFeedback() || resuming || repository.requiresHumanApproval(agentId);
		ApprovalScope approvalScope = ApprovalScope.from(request.humanFeedbackContent());
		return new RequestContext(request.agentId(), agentId, conversationId, threadId, request.query().trim(), hitl,
				resuming, request.humanFeedbackContent(), request.rejectedPlan(), request.nl2sqlOnly(), approvalScope);
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
		boolean approved = !context.rejectedPlan();
		if (approved && context.approvalScope() != ApprovalScope.ONCE) {
			applySessionApproval(agent, runtime, pendingTools, context.approvalScope());
		}
		List<ConfirmResult> results = pendingTools.stream()
			.map(tool -> confirmResult(approved, tool, context.approvalScope()))
			.toList();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(Msg.METADATA_CONFIRM_RESULTS, results);
		return Msg.builder()
			.name("user")
			.role(MsgRole.USER)
			.textContent(approved ? "批准执行" : "拒绝执行")
			.metadata(metadata)
			.build();
	}

	private ConfirmResult confirmResult(boolean approved, ToolUseBlock tool, ApprovalScope approvalScope) {
		if (!approved || approvalScope != ApprovalScope.TOOL_FOR_SESSION) {
			return new ConfirmResult(approved, tool);
		}
		PermissionRule sessionRule = new PermissionRule(tool.getName(), null, PermissionBehavior.ALLOW, "session");
		return new ConfirmResult(true, tool, List.of(sessionRule));
	}

	private void applySessionApproval(HarnessAgent agent, RuntimeContext runtime, List<ToolUseBlock> pendingTools,
			ApprovalScope approvalScope) {
		AgentState state = agent.getDelegate().getAgentState(runtime);
		PermissionContextState current = state.getPermissionContext();
		PermissionContextState.Builder builder = PermissionContextState.builder()
			.mode(approvalScope == ApprovalScope.ALL_FOR_SESSION ? PermissionMode.BYPASS : current.getMode());
		current.getWorkingDirectories().forEach(builder::addWorkingDirectory);
		current.getAllowRules().forEach((toolName, rules) -> rules.forEach(rule -> builder.addAllowRule(toolName, rule)));
		current.getDenyRules().forEach((toolName, rules) -> rules.forEach(rule -> builder.addDenyRule(toolName, rule)));
		if (approvalScope == ApprovalScope.TOOL_FOR_SESSION) {
			List<String> approvedTools = pendingTools.stream().map(ToolUseBlock::getName).distinct().toList();
			current.getAskRules().forEach((toolName, rules) -> {
				if (!approvedTools.contains(toolName)) {
					rules.forEach(rule -> builder.addAskRule(toolName, rule));
				}
			});
			for (String toolName : approvedTools) {
				builder.addAllowRule(toolName,
						new PermissionRule(toolName, null, PermissionBehavior.ALLOW, "session"));
			}
		}
		if (approvalScope == ApprovalScope.ALL_FOR_SESSION) {
			builder.addAllowRule(SESSION_BYPASS_MARKER, new PermissionRule(SESSION_BYPASS_MARKER, null,
					PermissionBehavior.ALLOW, "session"));
		}
		agent.getDelegate()
			.replacePermissionContext(runtime.getUserId(), runtime.getSessionId(), builder.build());
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

	private String revisionInput(RequestContext context) {
		String feedback = StringUtils.hasText(context.feedback()) ? context.feedback().trim()
				: "请重新规划，不要执行刚才被拒绝的操作。";
		return "用户拒绝了刚才的工具调用。请根据以下审核意见修订方案；不要重复原操作，必要时生成新的查询并再次申请确认：\n"
				+ feedback;
	}

	private String approvalText(List<ToolUseBlock> tools) {
		StringBuilder text = new StringBuilder("以下操作尚未执行，需要人工确认：");
		for (ToolUseBlock tool : tools) {
			text.append("\n\n工具：").append(tool.getName());
			Object sql = tool.getInput().get("sql");
			if (sql != null) {
				text.append("\nSQL：\n").append(sql);
			}
			else {
				text.append("\n参数：").append(tool.getInput());
			}
		}
		text.append("\n\n请选择仅本次批准、会话内允许此类工具，或会话内默认允许。");
		return text.toString();
	}

	private String toolNode(String toolName) {
		return switch (toolName) {
			case "search_knowledge_base" -> "EvidenceRecallNode";
			case "inspect_data_source" -> "SchemaRecallNode";
			default -> "SqlExecuteNode";
		};
	}

	private String stepId(RequestContext context, String node) {
		return context.threadId() + ":" + node + ":1";
	}

	private ServerSentEvent<GraphNodeResponse> completeEvent(RequestContext context) {
		return ServerSentEvent.builder(GraphNodeResponse.complete(context.agentIdText(), context.threadId()))
			.event("complete")
			.build();
	}

	private Flux<ServerSentEvent<GraphNodeResponse>> errorFlux(String agentId, String threadId, Throwable error) {
		String safeThreadId = StringUtils.hasText(threadId) ? threadId : UUID.randomUUID().toString();
		String message = rootMessage(error);
		return Flux.just(ServerSentEvent.builder(GraphNodeResponse.error(agentId, safeThreadId, message)).event("error")
			.build());
	}

	private String rootMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return StringUtils.hasText(current.getMessage()) ? current.getMessage() : current.getClass().getSimpleName();
	}

	private record RequestContext(String agentIdText, long agentId, String conversationId, String threadId,
			String query, boolean hitl, boolean resuming, String feedback, boolean rejectedPlan, boolean nl2sqlOnly,
			ApprovalScope approvalScope) {
	}

	private enum ApprovalScope {

		ONCE,

		TOOL_FOR_SESSION,

		ALL_FOR_SESSION;

		private static ApprovalScope from(String feedback) {
			if (APPROVE_TOOL_FOR_SESSION.equals(feedback)) {
				return TOOL_FOR_SESSION;
			}
			if (APPROVE_ALL_FOR_SESSION.equals(feedback)) {
				return ALL_FOR_SESSION;
			}
			return ONCE;
		}
	}

}
