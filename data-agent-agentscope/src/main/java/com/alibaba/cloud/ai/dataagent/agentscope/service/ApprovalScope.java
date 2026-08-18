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

import com.alibaba.cloud.ai.dataagent.agentscope.api.ConfirmationDecision;

/** Scope applied to an approved AgentScope tool call for the current session. */
enum ApprovalScope {

	ONCE,

	TOOL_FOR_SESSION,

	ALL_FOR_SESSION;

	static ApprovalScope from(ConfirmationDecision decision) {
		if (decision == ConfirmationDecision.APPROVE_TOOL_FOR_SESSION) {
			return TOOL_FOR_SESSION;
		}
		if (decision == ConfirmationDecision.APPROVE_ALL_FOR_SESSION) {
			return ALL_FOR_SESSION;
		}
		return ONCE;
	}

}
