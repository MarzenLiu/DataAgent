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
package com.alibaba.cloud.ai.dataagent.agentscope.agent;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Immutable permission-context transformations used by the DataAgent session policy. */
public final class PermissionContextUpdates {

	public static final String SESSION_RULE_SOURCE = "session";

	private PermissionContextUpdates() {
	}

	public static PermissionContextState.Builder sessionStateBuilder(PermissionContextState current,
			PermissionMode mode) {
		PermissionContextState.Builder builder = PermissionContextState.builder().mode(mode);
		current.getWorkingDirectories().forEach(builder::addWorkingDirectory);
		copyRules(current.getAllowRules(), PermissionContextUpdates::isSessionRule, builder::addAllowRule);
		copyRules(current.getDenyRules(), PermissionContextUpdates::isSessionRule, builder::addDenyRule);
		copyRules(current.getAskRules(), PermissionContextUpdates::isSessionRule, builder::addAskRule);
		return builder;
	}

	public static PermissionContextState allowToolsForSession(PermissionContextState current,
			Collection<String> toolNames) {
		Set<String> approvedTools = new HashSet<>(toolNames);
		PermissionContextState.Builder builder = PermissionContextState.builder().mode(current.getMode());
		current.getWorkingDirectories().forEach(builder::addWorkingDirectory);
		copyRules(current.getAllowRules(), rule -> true, builder::addAllowRule);
		copyRules(current.getDenyRules(), rule -> true, builder::addDenyRule);
		current.getAskRules().forEach((toolName, rules) -> {
			if (!approvedTools.contains(toolName)) {
				rules.forEach(rule -> builder.addAskRule(toolName, rule));
			}
		});
		approvedTools.forEach(toolName -> builder.addAllowRule(toolName,
				new PermissionRule(toolName, null, PermissionBehavior.ALLOW, SESSION_RULE_SOURCE)));
		return builder.build();
	}

	public static Set<String> sessionRuleTools(Map<String, List<PermissionRule>> rules) {
		return rules.entrySet()
			.stream()
			.filter(entry -> entry.getValue().stream().anyMatch(PermissionContextUpdates::isSessionRule))
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	private static boolean isSessionRule(PermissionRule rule) {
		return SESSION_RULE_SOURCE.equals(rule.source());
	}

	private static void copyRules(Map<String, List<PermissionRule>> rules, Predicate<PermissionRule> filter,
			BiConsumer<String, PermissionRule> consumer) {
		rules.forEach((toolName, values) -> values.stream()
			.filter(filter)
			.forEach(rule -> consumer.accept(toolName, rule)));
	}

}
