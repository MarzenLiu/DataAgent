/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export enum ConfirmationDecision {
	APPROVE_ONCE = 'APPROVE_ONCE',
	APPROVE_TOOL_FOR_SESSION = 'APPROVE_TOOL_FOR_SESSION',
	APPROVE_ALL_FOR_SESSION = 'APPROVE_ALL_FOR_SESSION',
	REJECT = 'REJECT',
}

export interface AgentStreamRequest {
	agentId: string;
	conversationId: string;
	runId?: string;
	query: string;
	hitl: boolean;
	confirmation?: ConfirmationDecision;
	nl2sqlOnly: boolean;
}

export interface AgentToolCall {
	id: string;
	name: string;
	input: Record<string, unknown>;
	state?: string;
}

export interface AgentResultMessage {
	content?: Array<{
		type: string;
		text?: string;
	}>;
}

export interface AgentToolActivity {
	id: string;
	name: string;
	label: string;
	status: 'running' | 'completed' | 'failed' | 'waiting';
}

/** AgentScope 原生事件的前端可消费子集；未知事件会被安全忽略。 */
export interface AgentStreamEvent {
	type: string;
	id: string;
	createdAt: string;
	replyId?: string;
	blockId?: string;
	delta?: string;
	toolCallId?: string;
	toolCallName?: string;
	toolCalls?: AgentToolCall[];
	state?: string;
	result?: AgentResultMessage;
	name?: string;
	value?: Record<string, unknown>;
}

const API_BASE_URL = '/api';

class GraphService {
	async streamSearch(
		request: AgentStreamRequest,
		onEvent: (event: AgentStreamEvent) => Promise<void>,
		onError?: (error: Error) => Promise<void>,
		onComplete?: () => Promise<void>,
	): Promise<(cancelRun?: boolean) => Promise<void>> {
		const params = new URLSearchParams({
			agentId: request.agentId,
			conversationId: request.conversationId,
			query: request.query,
			hitl: request.hitl.toString(),
			nl2sqlOnly: request.nl2sqlOnly.toString(),
		});
		if (request.runId) params.append('runId', request.runId);
		if (request.confirmation)
			params.append('confirmation', request.confirmation);

		const eventSource = new EventSource(
			`${API_BASE_URL}/stream/search?${params.toString()}`,
		);
		let finished = false;
		let stopRequested = false;

		async function fail(message: string) {
			if (finished) return;
			finished = true;
			eventSource.close();
			if (onError) await onError(new Error(message));
		}

		eventSource.onmessage = async (message) => {
			try {
				const event = JSON.parse(message.data) as AgentStreamEvent;
				if (event.type === 'CUSTOM' && event.name === 'stream_error') {
					await fail(
						String(event.value?.message || 'Stream processing failed'),
					);
					return;
				}
				if (event.type === 'CUSTOM' && event.name === 'stream_completed') {
					if (finished) return;
					finished = true;
					eventSource.close();
					if (!stopRequested && onComplete) await onComplete();
					return;
				}
				if (stopRequested) return;
				await onEvent(event);
			} catch (error) {
				await fail(
					error instanceof Error
						? error.message
						: 'Failed to parse server response',
				);
			}
		};

		eventSource.onerror = async () => {
			if (stopRequested) {
				finished = true;
				eventSource.close();
				return;
			}
			if (!finished) await fail('Stream connection failed');
		};

		return async (cancelRun = false) => {
			if (!cancelRun) {
				finished = true;
				eventSource.close();
				return;
			}
			stopRequested = true;
			const stopParams = new URLSearchParams({
				conversationId: request.conversationId,
			});
			const response = await fetch(
				`${API_BASE_URL}/stream/stop?${stopParams.toString()}`,
				{ method: 'POST', keepalive: true },
			);
			if (!response.ok) {
				stopRequested = false;
				throw new Error(`Failed to stop agent run: HTTP ${response.status}`);
			}
		};
	}
}

export default new GraphService();
