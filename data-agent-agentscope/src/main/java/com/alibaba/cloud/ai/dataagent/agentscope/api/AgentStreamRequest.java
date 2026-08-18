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
package com.alibaba.cloud.ai.dataagent.agentscope.api;

/**
 * HTTP request body for starting or resuming an AgentScope SSE run.
 *
 * <p>
 * This transport object is not mapped to a database table. Conversation ownership is
 * validated separately against {@code chat_session}.
 *
 * @param agentId target agent identifier
 * @param conversationId conversation identifier, generated when absent
 * @param runId existing run identifier required for confirmation responses
 * @param query user query
 * @param hitl whether human-in-the-loop confirmation is requested
 * @param confirmation optional decision used to resume a paused tool call
 * @param nl2sqlOnly whether execution is restricted to NL2SQL-only mode
 */
public record AgentStreamRequest(String agentId, String conversationId, String runId, String query, boolean hitl,
		ConfirmationDecision confirmation, boolean nl2sqlOnly) {
}
