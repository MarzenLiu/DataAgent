package com.alibaba.cloud.ai.dataagent.agentscope.agent;

import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ApprovalMode;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ToolConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Applies request-scoped modes to one persistent AgentScope conversation session. */
@Component
public class AgentRuntimePolicy {

	private static final String TOOL_CONFIG_RULE_SOURCE = "agent-tool-config";

	private static final String REQUEST_MODE_RULE_SOURCE = "request-mode";

	private final DataAgentRegistryRepository repository;

	public AgentRuntimePolicy(DataAgentRegistryRepository repository) {
		this.repository = repository;
	}

	public void apply(long agentId, HarnessAgent agent, RuntimeContext runtime, boolean hitl, boolean nl2sqlOnly) {
		AgentConfiguration configuration = repository.findAgentConfiguration(agentId)
			.orElseThrow(() -> new IllegalStateException("Agent configuration was not found for agent " + agentId));
		AgentState state = agent.getDelegate().getAgentState(runtime);
		PermissionContextState current = state.getPermissionContext();
		SessionRules sessionRules = SessionRules.from(current);
		PermissionMode mode = !nl2sqlOnly && current.getMode() == PermissionMode.BYPASS ? PermissionMode.BYPASS
				: PermissionMode.DEFAULT;
		PermissionContextState.Builder builder = PermissionContextUpdates.sessionStateBuilder(current, mode);
		if (mode != PermissionMode.BYPASS) {
			for (ToolConfiguration tool : configuration.tools()) {
				PermissionBehavior behavior = resolveBehavior(tool, sessionRules, hitl, nl2sqlOnly);
				if (behavior != null) {
					addRule(builder, tool.toolName(), behavior);
				}
			}
		}
		agent.getDelegate().replacePermissionContext(runtime.getUserId(), runtime.getSessionId(), builder.build());
	}

	private PermissionBehavior resolveBehavior(ToolConfiguration tool, SessionRules sessionRules, boolean hitl,
			boolean nl2sqlOnly) {
		if (nl2sqlOnly && !tool.availableInNl2sqlOnly()) {
			return PermissionBehavior.DENY;
		}
		if (sessionRules.hasDecision(tool.toolName())) {
			return null;
		}
		if (tool.approvalMode() == ApprovalMode.ALLOW
				|| tool.approvalMode() == ApprovalMode.ASK_WHEN_HITL && !hitl) {
			return PermissionBehavior.ALLOW;
		}
		return PermissionBehavior.ASK;
	}

	private void addRule(PermissionContextState.Builder builder, String toolName, PermissionBehavior behavior) {
		String source = behavior == PermissionBehavior.DENY ? REQUEST_MODE_RULE_SOURCE : TOOL_CONFIG_RULE_SOURCE;
		PermissionRule rule = new PermissionRule(toolName, null, behavior, source);
		switch (behavior) {
			case ALLOW -> builder.addAllowRule(toolName, rule);
			case DENY -> builder.addDenyRule(toolName, rule);
			case ASK -> builder.addAskRule(toolName, rule);
			case PASSTHROUGH -> throw new IllegalArgumentException("PASSTHROUGH cannot be stored as a permission rule");
		}
	}

	private record SessionRules(Set<String> allowedTools, Set<String> deniedTools) {

		static SessionRules from(PermissionContextState context) {
			return new SessionRules(PermissionContextUpdates.sessionRuleTools(context.getAllowRules()),
					PermissionContextUpdates.sessionRuleTools(context.getDenyRules()));
		}

		boolean hasDecision(String toolName) {
			return allowedTools.contains(toolName) || deniedTools.contains(toolName);
		}

	}

}
