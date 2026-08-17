package com.alibaba.cloud.ai.dataagent.agentscope.agent;

import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository.AgentConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository.ApprovalMode;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository.ToolConfiguration;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Applies request-scoped modes to one persistent AgentScope conversation session. */
@Component
public class AgentRuntimePolicy {

	public static final String SESSION_BYPASS_MARKER = "__data_agent_session_bypass__";

	private final DataAgentRegistryRepository repository;

	public AgentRuntimePolicy(DataAgentRegistryRepository repository) {
		this.repository = repository;
	}

	public void apply(long agentId, HarnessAgent agent, RuntimeContext runtime, boolean hitl, boolean nl2sqlOnly) {
		AgentConfiguration configuration = repository.findAgentConfiguration(agentId)
			.orElseThrow(() -> new IllegalStateException("Agent configuration was not found for agent " + agentId));
		AgentState state = agent.getDelegate().getAgentState(runtime);
		PermissionContextState current = state.getPermissionContext();
		Set<String> sessionAllowed = sessionRuleTools(current.getAllowRules());
		Set<String> sessionDenied = sessionRuleTools(current.getDenyRules());
		boolean sessionBypass = current.getMode() == PermissionMode.BYPASS
				|| sessionAllowed.contains(SESSION_BYPASS_MARKER);

		PermissionContextState.Builder builder = PermissionContextState.builder()
			.mode(nl2sqlOnly ? PermissionMode.DEFAULT : sessionBypass ? PermissionMode.BYPASS : PermissionMode.DEFAULT);
		current.getWorkingDirectories().forEach(builder::addWorkingDirectory);
		copySessionRules(current.getAllowRules(), builder::addAllowRule);
		copySessionRules(current.getDenyRules(), builder::addDenyRule);
		copySessionRules(current.getAskRules(), builder::addAskRule);
		if (sessionBypass && !sessionAllowed.contains(SESSION_BYPASS_MARKER)) {
			builder.addAllowRule(SESSION_BYPASS_MARKER, new PermissionRule(SESSION_BYPASS_MARKER, null,
					PermissionBehavior.ALLOW, "session"));
		}
		for (ToolConfiguration tool : configuration.tools()) {
			applyToolPolicy(builder, tool, sessionAllowed, sessionDenied, hitl, nl2sqlOnly);
		}
		agent.getDelegate().replacePermissionContext(runtime.getUserId(), runtime.getSessionId(), builder.build());
	}

	private void applyToolPolicy(PermissionContextState.Builder builder, ToolConfiguration tool,
			Set<String> sessionAllowed, Set<String> sessionDenied, boolean hitl, boolean nl2sqlOnly) {
		if (nl2sqlOnly && !tool.availableInNl2sqlOnly()) {
			builder.addDenyRule(tool.toolName(), new PermissionRule(tool.toolName(), null,
					PermissionBehavior.DENY, "request-mode"));
			return;
		}
		if (sessionAllowed.contains(tool.toolName()) || sessionDenied.contains(tool.toolName())) return;
		if (tool.approvalMode() == ApprovalMode.ALLOW
				|| tool.approvalMode() == ApprovalMode.ASK_WHEN_HITL && !hitl) {
			builder.addAllowRule(tool.toolName(), new PermissionRule(tool.toolName(), null,
					PermissionBehavior.ALLOW, "agent-tool-config"));
		}
		else {
			builder.addAskRule(tool.toolName(), new PermissionRule(tool.toolName(), null,
					PermissionBehavior.ASK, "agent-tool-config"));
		}
	}

	private Set<String> sessionRuleTools(Map<String, List<PermissionRule>> rules) {
		return rules.entrySet().stream()
			.filter(entry -> entry.getValue().stream().anyMatch(rule -> "session".equals(rule.source())))
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	private void copySessionRules(Map<String, List<PermissionRule>> rules,
			BiConsumer<String, PermissionRule> consumer) {
		rules.forEach((toolName, values) -> values.stream()
			.filter(rule -> "session".equals(rule.source()))
			.forEach(rule -> consumer.accept(toolName, rule)));
	}
}
