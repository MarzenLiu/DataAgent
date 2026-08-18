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
package com.alibaba.cloud.ai.dataagent.agentscope.entity;

import java.util.List;

/**
 * Complete runtime configuration used to construct one AgentScope agent.
 *
 * <p>
 * Aggregate database sources: {@code agent}, {@code agent_tool}, {@code agent_skill}, and
 * optionally {@code harness_skill}. This is a runtime aggregate rather than a direct
 * one-table row mapping.
 *
 * @param name agent display name from {@code agent.name}
 * @param description agent description from {@code agent.description}
 * @param prompt system prompt from {@code agent.prompt}
 * @param tools enabled tool configurations from {@code agent_tool}
 * @param skills enabled skill configurations from {@code agent_skill}
 */
public record AgentConfiguration(String name, String description, String prompt, List<ToolConfiguration> tools,
		List<SkillConfiguration> skills) {
}
