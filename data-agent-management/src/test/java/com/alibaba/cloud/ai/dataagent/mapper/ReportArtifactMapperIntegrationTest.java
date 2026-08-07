/*
 * Copyright 2024-2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.ReportArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
class ReportArtifactMapperIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ReportArtifactMapper mapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS report_artifact");
		jdbcTemplate.execute("""
				CREATE TABLE report_artifact (
				  id BIGINT AUTO_INCREMENT PRIMARY KEY,
				  conversation_id VARCHAR(36) NOT NULL,
				  agent_id BIGINT NOT NULL,
				  source_query TEXT,
				  content TEXT NOT NULL,
				  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
				)
				""");
	}

	@Test
	void returnsLatestVersionForConversationAndAgent() {
		mapper.insert(ReportArtifact.builder()
			.conversationId("conversation-1")
			.agentId(7L)
			.sourceQuery("生成报告")
			.content("first")
			.build());
		mapper.insert(ReportArtifact.builder()
			.conversationId("conversation-1")
			.agentId(7L)
			.sourceQuery("精简报告")
			.content("second")
			.build());
		mapper.insert(ReportArtifact.builder()
			.conversationId("conversation-1")
			.agentId(8L)
			.sourceQuery("other agent")
			.content("other")
			.build());

		ReportArtifact latest = mapper.selectLatest("conversation-1", 7L);

		assertThat(latest.getContent()).isEqualTo("second");
		assertThat(latest.getSourceQuery()).isEqualTo("精简报告");
		assertThat(latest.getId()).isNotNull();
	}

}
