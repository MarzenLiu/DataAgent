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

import { describe, expect, it } from 'vitest';
import {
	agentResultText,
	describeConfirmation,
	visibleToolLabel,
} from './agentEvents';

describe('AgentScope event presentation', () => {
	it('only exposes the explicitly mapped read-only query activity', () => {
		expect(visibleToolLabel('execute_read_only_sql')).toBe('正在执行只读查询');
		expect(visibleToolLabel('load_skill_through_path')).toBeUndefined();
		expect(visibleToolLabel('future_tool')).toBeUndefined();
	});

	it('shows read-only SQL in the confirmation request', () => {
		const text = describeConfirmation({
			type: 'REQUIRE_USER_CONFIRM',
			id: 'event-1',
			createdAt: '2026-08-17T00:00:00Z',
			toolCalls: [
				{
					id: 'tool-1',
					name: 'execute_read_only_sql',
					input: { sql: 'select count(*) from orders' },
				},
			],
		});

		expect(text).toContain('只读查询');
		expect(text).toContain('select count(*) from orders');
	});

	it('extracts final text from the native AgentScope message blocks', () => {
		expect(
			agentResultText({
				type: 'AGENT_RESULT',
				id: 'event-2',
				createdAt: '2026-08-17T00:00:00Z',
				result: {
					content: [
						{ type: 'text', text: '最终' },
						{ type: 'thinking', text: '不展示' },
						{ type: 'text', text: '回答' },
					],
				},
			}),
		).toBe('最终回答');
	});
});
