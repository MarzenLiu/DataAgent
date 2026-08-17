package com.alibaba.cloud.ai.dataagent.mcp.tool;

import java.util.Locale;
import java.util.regex.Pattern;

final class SqlSafety {
	private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|--[^\\r\\n]*");
	private static final Pattern FORBIDDEN = Pattern.compile(
			"(?i)\\b(insert|update|delete|merge|upsert|replace|drop|alter|truncate|create|grant|revoke|call|execute|exec|copy|load|outfile|dumpfile|lock|unlock|set)\\b");
	private SqlSafety() { }

	static String requireReadOnly(String sql) {
		if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL must not be blank");
		String normalized = COMMENTS.matcher(sql).replaceAll(" ").trim();
		if (normalized.endsWith(";")) normalized = normalized.substring(0, normalized.length() - 1).trim();
		if (normalized.contains(";")) throw new IllegalArgumentException("Only one SQL statement is allowed");
		String lower = normalized.toLowerCase(Locale.ROOT);
		if (!(lower.startsWith("select ") || lower.startsWith("select\n") || lower.startsWith("with ")
				|| lower.startsWith("with\n") || lower.startsWith("explain "))) {
			throw new IllegalArgumentException("Only SELECT, WITH, or EXPLAIN statements are allowed");
		}
		if (FORBIDDEN.matcher(normalized).find()) throw new IllegalArgumentException("Potentially mutating SQL is not allowed");
		return normalized;
	}
}
