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
package com.alibaba.cloud.ai.dataagent.agentscope.config;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class AgentScopePersistenceConfiguration {

	private static final String STATE_TABLE = "agentscope_sessions";

	@Bean
	AgentStateStore agentStateStore(DataSource dataSource) {
		return new MysqlAgentStateStore(dataSource, databaseName(dataSource), STATE_TABLE, true);
	}

	private String databaseName(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection()) {
			String catalog = connection.getCatalog();
			if (!StringUtils.hasText(catalog)) {
				throw new IllegalStateException("AgentScope state storage requires a named MySQL database");
			}
			return catalog;
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Unable to resolve the AgentScope state database", ex);
		}
	}

}
