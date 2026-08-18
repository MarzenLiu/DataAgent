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
 * Tool approval policy persisted in {@code agent_tool.approval_mode}.
 */
public enum ApprovalMode {

	/** Execute without requesting user confirmation. */
	ALLOW,

	/** Request confirmation only when the current request enables HITL. */
	ASK_WHEN_HITL,

	/** Always request user confirmation before execution. */
	ALWAYS_ASK;

	/**
	 * Converts the database value to its normalized enum form.
	 * @param value value read from {@code agent_tool.approval_mode}
	 * @return normalized approval mode
	 * @throws IllegalStateException when the persisted value is unsupported
	 */
	public static ApprovalMode from(String value) {
		try {
			return ApprovalMode.valueOf(value == null ? "" : value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("Unsupported agent_tool approval_mode: " + value, ex);
		}
	}

}
