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
package com.alibaba.cloud.ai.dataagent.agentscope.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataAgentRegistryMapperTest {

	private DataSource dataSource;

	private SqlSessionFactory sessionFactory;

	@BeforeEach
	void setUp() throws Exception {
		dataSource = new PooledDataSource("org.h2.Driver",
				"jdbc:h2:mem:registry;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DROP ALL OBJECTS");
			statement.execute(
					"CREATE TABLE agent (id BIGINT PRIMARY KEY, name VARCHAR, description VARCHAR, prompt VARCHAR)");
			statement.execute("""
					CREATE TABLE agent_tool (
					  id BIGINT PRIMARY KEY, agent_id BIGINT, tool_name VARCHAR, approval_mode VARCHAR,
					  inject_agent_id BOOLEAN, available_in_nl2sql_only BOOLEAN, is_enabled BOOLEAN, sort_order INT)
					""");
			statement.execute("""
					CREATE TABLE harness_skill (
					  id BIGINT PRIMARY KEY, skill_name VARCHAR, description VARCHAR, content VARCHAR,
					  is_enabled BOOLEAN, update_time TIMESTAMP)
					""");
			statement.execute("""
					CREATE TABLE harness_skill_resource (
					  id BIGINT PRIMARY KEY, skill_id BIGINT, resource_path VARCHAR, content VARCHAR)
					""");
			statement.execute("""
					CREATE TABLE agent_skill (
					  id BIGINT PRIMARY KEY, agent_id BIGINT, skill_name VARCHAR, is_enabled BOOLEAN, sort_order INT)
					""");
			statement.execute("CREATE TABLE chat_session (id VARCHAR PRIMARY KEY, agent_id BIGINT)");
			statement.execute("""
					CREATE TABLE model_config (
					  id BIGINT PRIMARY KEY, provider VARCHAR, base_url VARCHAR, api_key VARCHAR,
					  model_name VARCHAR, temperature DOUBLE, max_tokens INT, completions_path VARCHAR,
					  model_type VARCHAR, is_active BOOLEAN, is_deleted BOOLEAN)
					""");
			statement.execute("""
					CREATE TABLE report_artifact (
					  conversation_id VARCHAR, agent_id BIGINT, source_query VARCHAR, content VARCHAR,
					  create_time TIMESTAMP)
					""");
			statement.execute("INSERT INTO agent VALUES (7, 'agent', 'description', 'prompt')");
			statement
				.execute("INSERT INTO agent_tool VALUES (1, 7, 'inspect_data_source', 'ALLOW', TRUE, TRUE, TRUE, 10)");
			statement.execute("""
					INSERT INTO harness_skill VALUES
					  (1, 'result-validation-sop', 'Validate results', 'Check totals.', TRUE, CURRENT_TIMESTAMP)
					""");
			statement
				.execute("INSERT INTO harness_skill_resource VALUES (1, 1, 'references/checklist.md', 'Checklist')");
			statement.execute("INSERT INTO agent_skill VALUES (1, 7, 'result-validation-sop', TRUE, 10)");
			statement.execute("INSERT INTO chat_session VALUES ('conversation-1', 7)");
			statement.execute("""
					INSERT INTO model_config VALUES
					  (1, 'openai', 'http://model', 'secret', 'model', 0.1, 2048, '/chat', 'CHAT', TRUE, FALSE)
					""");
		}
		Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
		Configuration configuration = new Configuration(environment);
		configuration.setMapUnderscoreToCamelCase(true);
		configuration.addMapper(DataAgentRegistryMapper.class);
		configuration.addMapper(DatabaseSkillMapper.class);
		sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
	}

	@Test
	void executesAllRegistryStatements() {
		try (SqlSession session = sessionFactory.openSession(true)) {
			DataAgentRegistryMapper mapper = session.getMapper(DataAgentRegistryMapper.class);

			assertThat(mapper.findAgentById(7L).name()).isEqualTo("agent");
			assertThat(mapper.findEnabledTools(7L)).singleElement().satisfies(tool -> {
				assertThat(tool.toolName()).isEqualTo("inspect_data_source");
				assertThat(tool.injectAgentId()).isTrue();
			});
			assertThat(mapper.findEnabledSkills(7L)).singleElement().satisfies(skill -> {
				assertThat(skill.skillName()).isEqualTo("result-validation-sop");
				assertThat(skill.databaseUpdatedAt()).isNotNull();
			});
			assertThat(mapper.countAlwaysAskTools(7L)).isZero();
			assertThat(mapper.findConversationAgentId("conversation-1")).isEqualTo(7L);
			assertThat(mapper.findActiveChatModel().modelName()).isEqualTo("model");
			assertThat(mapper.insertReport("conversation-1", 7L, "query", "report")).isEqualTo(1);

			DatabaseSkillMapper skillMapper = session.getMapper(DatabaseSkillMapper.class);
			assertThat(skillMapper.findAllEnabledNames()).containsExactly("result-validation-sop");
			assertThat(skillMapper.findAllEnabled()).singleElement().satisfies(skill -> {
				assertThat(skill.skillName()).isEqualTo("result-validation-sop");
				assertThat(skill.content()).isEqualTo("Check totals.");
			});
			assertThat(skillMapper.findResources(1L)).singleElement()
				.satisfies(resource -> assertThat(resource.resourcePath()).isEqualTo("references/checklist.md"));
		}
	}

}
