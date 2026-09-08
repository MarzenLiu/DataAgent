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

import type { AgentStreamEvent, AgentToolCall } from '~/services/graph/index';

const VISIBLE_TOOL_LABELS: Record<string, string> = {
	execute_read_only_sql: '正在执行只读查询',
};

export function visibleToolLabel(toolName: string): string | undefined {
	return VISIBLE_TOOL_LABELS[toolName];
}

export interface ToolConfirmationPresentation {
	id: string;
	name: string;
	title: string;
	details: string;
	language: 'sql' | 'json' | 'text';
}

function formatToolInput(input: Record<string, unknown>): string {
	try {
		return JSON.stringify(input, null, 2);
	} catch {
		return String(input);
	}
}

export function looksLikeSql(value: string): boolean {
	return /^\s*(?:--[^\n]*\n\s*|\/\*[\s\S]*?\*\/\s*)*(?:SELECT|WITH|INSERT|UPDATE|DELETE|MERGE|EXPLAIN|SHOW|DESCRIBE|PRAGMA|CALL|CREATE|ALTER|DROP)\b/i.test(
		value,
	);
}

export function formatSql(value: string): string {
	const protectedParts: string[] = [];
	const protectedSql = value.replace(
		/('(?:''|\\.|[^'])*'|"(?:""|\\.|[^"])*"|`(?:``|[^`])*`|--[^\r\n]*|\/\*[\s\S]*?\*\/)/g,
		(part) => {
			const token = `\uE000${protectedParts.length}\uE001`;
			protectedParts.push(part);
			return token;
		},
	);
	const formatted = protectedSql
		.replace(/\s+/g, ' ')
		.replace(
			/\s*\b(UNION\s+ALL|UNION|LEFT\s+JOIN|RIGHT\s+JOIN|FULL\s+JOIN|INNER\s+JOIN|CROSS\s+JOIN|GROUP\s+BY|ORDER\s+BY|SELECT|FROM|WHERE|HAVING|LIMIT|OFFSET|VALUES|SET|RETURNING)\b\s*/gi,
			(_, clause: string) => `\n${clause.replace(/\s+/g, ' ').toUpperCase()} `,
		)
		.trim();
	return formatted.replace(/\uE000(\d+)\uE001/g, (_, index: string) => {
		return protectedParts[Number(index)] || '';
	});
}

function findSql(tool: AgentToolCall): string | undefined {
	const explicitSql = tool.input?.sql;
	if (
		typeof explicitSql === 'string' &&
		(tool.name === 'execute_read_only_sql' || looksLikeSql(explicitSql))
	) {
		return explicitSql;
	}
	return Object.values(tool.input || {}).find(
		(value): value is string =>
			typeof value === 'string' && looksLikeSql(value),
	);
}

/** Keeps the approval payload generic while allowing tool-specific presentation. */
export function presentToolConfirmation(
	tool: AgentToolCall,
): ToolConfirmationPresentation {
	const sql = findSql(tool);
	if (sql) {
		return {
			id: tool.id,
			name: tool.name,
			title:
				tool.name === 'execute_read_only_sql'
					? '只读查询'
					: `SQL：${tool.name}`,
			details: formatSql(sql),
			language: 'sql',
		};
	}
	return {
		id: tool.id,
		name: tool.name,
		title: `工具调用：${tool.name}`,
		details: formatToolInput(tool.input || {}),
		language: 'json',
	};
}

export function agentResultText(event: AgentStreamEvent): string {
	return (event.result?.content || [])
		.filter((block) => block.type === 'text' && block.text)
		.map((block) => block.text)
		.join('');
}
