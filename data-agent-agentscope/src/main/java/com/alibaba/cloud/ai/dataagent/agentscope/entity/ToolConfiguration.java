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
 * Normalized runtime policy for one enabled MCP tool.
 *
 * <p>
 * Database source: {@code agent_tool}. The persisted {@code approval_mode} string is
 * converted to {@link ApprovalMode} before this entity is created.
 *
 * @param toolName MCP tool name
 * @param approvalMode normalized approval policy
 * @param injectAgentId whether the server injects the current agent ID
 * @param availableInNl2sqlOnly whether the tool is allowed in NL2SQL-only mode
 */
public record ToolConfiguration(String toolName, ApprovalMode approvalMode, boolean injectAgentId,
		boolean availableInNl2sqlOnly) {
}
