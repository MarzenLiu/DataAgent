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

import com.alibaba.cloud.ai.dataagent.agentscope.entity.DatabaseSkillDefinition;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.DatabaseSkillResource;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** Read-only Harness skill repository backed by the application database. */
@Repository
public class DatabaseSkillRepository implements AgentSkillRepository {

	private static final String SOURCE = "database";

	private final DatabaseSkillMapper mapper;

	public DatabaseSkillRepository(DatabaseSkillMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public AgentSkill getSkill(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		DatabaseSkillDefinition row = mapper.findEnabledByName(name);
		return row == null ? null : toSkill(row);
	}

	@Override
	public List<String> getAllSkillNames() {
		return mapper.findAllEnabledNames();
	}

	@Override
	public List<AgentSkill> getAllSkills() {
		return mapper.findAllEnabled().stream().map(this::toSkill).toList();
	}

	@Override
	public boolean save(List<AgentSkill> skills, boolean force) {
		return false;
	}

	@Override
	public boolean delete(String skillName) {
		return false;
	}

	@Override
	public boolean skillExists(String skillName) {
		return getSkill(skillName) != null;
	}

	@Override
	public AgentSkillRepositoryInfo getRepositoryInfo() {
		return new AgentSkillRepositoryInfo(SOURCE, "harness_skill", false);
	}

	@Override
	public String getSource() {
		return SOURCE;
	}

	@Override
	public void setWriteable(boolean writeable) {
		if (writeable) {
			throw new IllegalArgumentException("Database skills are read-only");
		}
	}

	@Override
	public boolean isWriteable() {
		return false;
	}

	private AgentSkill toSkill(DatabaseSkillDefinition row) {
		Map<String, String> resources = new LinkedHashMap<>();
		for (DatabaseSkillResource resource : mapper.findResources(row.id())) {
			resources.put(resource.resourcePath(), resource.content());
		}
		return new AgentSkill(row.skillName(), row.description(), row.content(), resources, SOURCE);
	}

}
