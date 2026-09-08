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
	formatSql,
	looksLikeSql,
	presentToolConfirmation,
	visibleToolLabel,
} from './agentEvents';

describe('AgentScope event presentation', () => {
	it('only exposes the explicitly mapped read-only query activity', () => {
		expect(visibleToolLabel('execute_read_only_sql')).toBe('正在执行只读查询');
		expect(visibleToolLabel('load_skill_through_path')).toBeUndefined();
		expect(visibleToolLabel('future_tool')).toBeUndefined();
	});

	it('renders read-only SQL without changing the generic tool-call shape', () => {
		const presentation = presentToolConfirmation({
			id: 'tool-1',
			name: 'execute_read_only_sql',
			input: { sql: 'select count(*) from orders', timeout: 30 },
		});

		expect(presentation.title).toBe('只读查询');
		expect(presentation.details).toBe('SELECT count(*)\nFROM orders');
		expect(presentation.id).toBe('tool-1');
		expect(presentation.language).toBe('sql');
	});

	it('detects and formats SQL while preserving quoted text', () => {
		const sql =
			"select status, count(*) from orders where note = 'from users where active = 1' group by status order by status";

		expect(looksLikeSql(sql)).toBe(true);
		expect(formatSql(sql)).toBe(
			"SELECT status, count(*)\nFROM orders\nWHERE note = 'from users where active = 1'\nGROUP BY status\nORDER BY status",
		);
	});

	it('uses SQL rendering for a non-SQL tool when an argument is SQL', () => {
		const presentation = presentToolConfirmation({
			id: 'tool-3',
			name: 'database_operation',
			input: { statement: 'WITH recent AS (SELECT 1) SELECT * FROM recent' },
		});

		expect(presentation.title).toBe('SQL：database_operation');
		expect(presentation.language).toBe('sql');
		expect(presentation.details).toContain('\nSELECT *');
	});

	it('renders arbitrary tool properties as generic JSON', () => {
		const presentation = presentToolConfirmation({
			id: 'tool-2',
			name: 'send_report',
			input: {
				recipients: ['ops@example.com'],
				options: { format: 'csv' },
			},
		});

		expect(presentation.title).toBe('工具调用：send_report');
		expect(presentation.details).toContain('"recipients"');
		expect(presentation.details).toContain('"format": "csv"');
		expect(presentation.language).toBe('json');
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
