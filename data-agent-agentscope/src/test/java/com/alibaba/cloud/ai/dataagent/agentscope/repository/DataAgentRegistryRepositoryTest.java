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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentProfile;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentSkillAssignment;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentToolAssignment;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DataAgentRegistryRepositoryTest {

	@Test
	void mapsAgentConfigurationFromMyBatisRows() {
		DataAgentRegistryMapper mapper = mock(DataAgentRegistryMapper.class);
		LocalDateTime revision = LocalDateTime.of(2026, 8, 17, 10, 0);
		when(mapper.findAgentById(7L)).thenReturn(new AgentProfile("agent", "description", "prompt"));
		when(mapper.findEnabledTools(7L))
			.thenReturn(List.of(new AgentToolAssignment("inspect_data_source", "ALLOW", true, true)));
		when(mapper.findEnabledSkills(7L))
			.thenReturn(List.of(new AgentSkillAssignment("result-validation-sop", revision)));
		DataAgentRegistryRepository repository = new DataAgentRegistryRepository(mapper);

		var configuration = repository.findAgentConfiguration(7L).orElseThrow();

		assertThat(configuration.name()).isEqualTo("agent");
		assertThat(configuration.tools()).singleElement().satisfies(tool -> {
			assertThat(tool.toolName()).isEqualTo("inspect_data_source");
			assertThat(tool.injectAgentId()).isTrue();
		});
		assertThat(configuration.skills()).singleElement().satisfies(skill -> {
			assertThat(skill.skillName()).isEqualTo("result-validation-sop");
			assertThat(skill.databaseUpdatedAt()).isEqualTo(revision);
		});
	}

	@Test
	void propagatesConfigurationStorageFailuresInsteadOfReportingMissingAgent() {
		DataAgentRegistryMapper mapper = mock(DataAgentRegistryMapper.class);
		when(mapper.findAgentById(7L)).thenReturn(new AgentProfile("agent", "description", "prompt"));
		when(mapper.findEnabledTools(7L)).thenReturn(List.of());
		when(mapper.findEnabledSkills(7L))
			.thenThrow(new DataAccessResourceFailureException("agent_skill table is unavailable"));
		DataAgentRegistryRepository repository = new DataAgentRegistryRepository(mapper);

		assertThatThrownBy(() -> repository.findAgentConfiguration(7L))
			.isInstanceOf(DataAccessResourceFailureException.class)
			.hasMessageContaining("agent_skill table is unavailable");
	}

}
