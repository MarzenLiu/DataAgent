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

import type { AgentStreamEvent } from '~/services/graph/index';

const VISIBLE_TOOL_LABELS: Record<string, string> = {
	execute_read_only_sql: '正在执行只读查询',
};

export function visibleToolLabel(toolName: string): string | undefined {
	return VISIBLE_TOOL_LABELS[toolName];
}

export function describeConfirmation(event: AgentStreamEvent): string {
	const calls = event.toolCalls || [];
	if (!calls.length) return 'Agent 请求执行受控工具，请确认是否继续。';
	return calls
		.map((tool) => {
			if (tool.name === 'execute_read_only_sql') {
				const sql = String(tool.input?.sql || '').trim();
				return sql ? `只读查询：\n${sql}` : '执行只读查询';
			}
			return `工具调用：${tool.name}`;
		})
		.join('\n\n');
}

export function agentResultText(event: AgentStreamEvent): string {
	return (event.result?.content || [])
		.filter((block) => block.type === 'text' && block.text)
		.map((block) => block.text)
		.join('');
}
