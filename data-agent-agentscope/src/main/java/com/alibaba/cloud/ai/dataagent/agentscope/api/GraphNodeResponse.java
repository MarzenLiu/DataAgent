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

public record GraphNodeResponse(String agentId, String threadId, GraphEventType eventType, String stepId,
		Integer attempt, String nodeName, TextType textType, String text, boolean error, boolean complete) {

	public static GraphNodeResponse output(String agentId, String threadId, String stepId, String nodeName,
			TextType textType, String text) {
		return new GraphNodeResponse(agentId, threadId, GraphEventType.NODE_OUTPUT, stepId, 1, nodeName, textType,
				text, false, false);
	}

	public static GraphNodeResponse finalAnswer(String agentId, String threadId, String text) {
		return new GraphNodeResponse(agentId, threadId, GraphEventType.FINAL_ANSWER, null, null, null, TextType.TEXT,
				text, false, false);
	}

	public static GraphNodeResponse humanFeedbackRequired(String agentId, String threadId) {
		return humanFeedbackRequired(agentId, threadId, null);
	}

	public static GraphNodeResponse humanFeedbackRequired(String agentId, String threadId, String text) {
		return new GraphNodeResponse(agentId, threadId, GraphEventType.HUMAN_FEEDBACK_REQUIRED, null, null, null,
				TextType.TEXT, text, false, false);
	}

	public static GraphNodeResponse error(String agentId, String threadId, String text) {
		return new GraphNodeResponse(agentId, threadId, GraphEventType.NODE_OUTPUT, null, null, null, TextType.TEXT,
				text, true, false);
	}

	public static GraphNodeResponse complete(String agentId, String threadId) {
		return new GraphNodeResponse(agentId, threadId, GraphEventType.NODE_OUTPUT, null, null, null, TextType.TEXT,
				null, false, true);
	}

}
