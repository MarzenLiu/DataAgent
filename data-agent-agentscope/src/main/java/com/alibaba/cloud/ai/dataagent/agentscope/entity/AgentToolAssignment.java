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

/**
 * Enabled tool assignment read from an agent's tool configuration.
 *
 * <p>
 * Database table: {@code agent_tool}. Columns: {@code tool_name}, {@code approval_mode},
 * {@code inject_agent_id}, and {@code available_in_nl2sql_only}. Rows are selected by
 * {@code agent_id} and {@code is_enabled}.
 *
 * @param toolName MCP tool name
 * @param approvalMode persisted approval policy name
 * @param injectAgentId whether the server injects the current agent ID
 * @param availableInNl2sqlOnly whether the tool is available in NL2SQL-only mode
 */
public record AgentToolAssignment(String toolName, String approvalMode, boolean injectAgentId,
		boolean availableInNl2sqlOnly) {
}
