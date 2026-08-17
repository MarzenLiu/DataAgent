package com.alibaba.cloud.ai.dataagent.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class SqlSafetyTest {
	@Test void acceptsReadOnlyStatement() { assertThat(SqlSafety.requireReadOnly("SELECT 1;")).isEqualTo("SELECT 1"); }
	@Test void rejectsMutationAndMultipleStatements() {
		assertThatThrownBy(() -> SqlSafety.requireReadOnly("DELETE FROM t")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SqlSafety.requireReadOnly("SELECT 1; SELECT 2")).isInstanceOf(IllegalArgumentException.class);
	}
}
