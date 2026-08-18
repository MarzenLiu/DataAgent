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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.dataagent.agentscope.entity.DatabaseSkillDefinition;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.DatabaseSkillResource;
import io.agentscope.core.skill.AgentSkill;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseSkillRepositoryTest {

	@Test
	void loadsSkillContentAndResourcesFromDatabase() {
		DatabaseSkillMapper mapper = mock(DatabaseSkillMapper.class);
		DatabaseSkillDefinition row = new DatabaseSkillDefinition(9L, "result-validation-sop", "Validate query results",
				"Check totals and null values before answering.");
		when(mapper.findEnabledByName("result-validation-sop")).thenReturn(row);
		when(mapper.findResources(9L))
			.thenReturn(List.of(new DatabaseSkillResource("references/checklist.md", "# Validation checklist")));
		DatabaseSkillRepository repository = new DatabaseSkillRepository(mapper);

		AgentSkill skill = repository.getSkill("result-validation-sop");

		assertThat(skill.getName()).isEqualTo("result-validation-sop");
		assertThat(skill.getSource()).isEqualTo("database");
		assertThat(skill.getResource("references/checklist.md")).isEqualTo("# Validation checklist");
		assertThat(repository.isWriteable()).isFalse();
	}

}
