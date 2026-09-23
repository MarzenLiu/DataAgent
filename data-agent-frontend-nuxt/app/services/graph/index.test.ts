/*
 * Copyright 2026 the original author or authors.
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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import graphService, { ConfirmationDecision } from './index';

class FakeEventSource {
	static latest: FakeEventSource;

	onmessage: ((event: MessageEvent<string>) => void | Promise<void>) | null =
		null;

	onerror: (() => void | Promise<void>) | null = null;

	closed = false;

	constructor(readonly url: string) {
		FakeEventSource.latest = this;
	}

	close() {
		this.closed = true;
	}

	async emit(data: unknown, lastEventId = 'run-1') {
		await this.onmessage?.({
			data: JSON.stringify(data),
			lastEventId,
		} as MessageEvent<string>);
	}
}

describe('AgentScope stream service', () => {
	beforeEach(() => {
		vi.stubGlobal('EventSource', FakeEventSource);
		vi.stubGlobal('fetch', vi.fn());
	});

	it('requests a native interrupt and keeps SSE subscribed until AgentScope completes', async () => {
		const onEvent = vi.fn(async () => {});
		const onComplete = vi.fn(async () => {});
		vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));
		const closeStream = await graphService.streamSearch(
			{
				agentId: '7',
				conversationId: 'conversation-1',
				query: 'revenue',
				hitl: false,
				nl2sqlOnly: false,
			},
			onEvent,
			undefined,
			onComplete,
		);

		await closeStream(true);

		expect(fetch).toHaveBeenCalledWith(
			'/data-agent-agentscope/api/stream/stop?conversationId=conversation-1',
			{ method: 'POST', keepalive: true },
		);
		expect(FakeEventSource.latest.closed).toBe(false);

		await FakeEventSource.latest.emit({
			type: 'AGENT_RESULT',
			id: 'event-1',
			createdAt: '2026-09-04T00:00:00Z',
		});
		await FakeEventSource.latest.emit({
			type: 'CUSTOM',
			id: 'event-2',
			createdAt: '2026-09-04T00:00:01Z',
			name: 'stream_completed',
			value: { runId: 'run-1' },
		});

		expect(onEvent).not.toHaveBeenCalled();
		expect(onComplete).not.toHaveBeenCalled();
		expect(FakeEventSource.latest.closed).toBe(true);
	});

	it('sends the new confirmation contract without legacy graph parameters', async () => {
		await graphService.streamSearch(
			{
				agentId: '7',
				conversationId: 'conversation-1',
				runId: 'run-1',
				query: 'revenue',
				hitl: true,
				confirmation: ConfirmationDecision.REJECT,
				nl2sqlOnly: false,
			},
			async () => {},
		);

		const url = new URL(FakeEventSource.latest.url, 'http://localhost');
		expect(url.searchParams.get('runId')).toBe('run-1');
		expect(url.searchParams.get('confirmation')).toBe('REJECT');
		expect(url.searchParams.get('hitl')).toBe('true');
		expect(url.searchParams.has('rejectedPlan')).toBe(false);
		expect(url.searchParams.has('humanFeedbackContent')).toBe(false);
	});

	it('forwards native events and completes on the lifecycle custom event', async () => {
		const onEvent = vi.fn(async () => {});
		const onComplete = vi.fn(async () => {});
		await graphService.streamSearch(
			{
				agentId: '7',
				conversationId: 'conversation-1',
				query: 'revenue',
				hitl: false,
				nl2sqlOnly: false,
			},
			onEvent,
			undefined,
			onComplete,
		);

		await FakeEventSource.latest.emit({
			type: 'TEXT_BLOCK_DELTA',
			id: 'event-1',
			createdAt: '2026-08-17T00:00:00Z',
			delta: 'hello',
		});
		await FakeEventSource.latest.emit({
			type: 'CUSTOM',
			id: 'event-2',
			createdAt: '2026-08-17T00:00:01Z',
			name: 'stream_completed',
			value: { runId: 'run-1' },
		});

		expect(onEvent).toHaveBeenCalledOnce();
		expect(onComplete).toHaveBeenCalledOnce();
		expect(FakeEventSource.latest.closed).toBe(true);
	});
});
