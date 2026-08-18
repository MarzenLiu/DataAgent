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
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DatabaseSkillMapper {

	@Select("""
			SELECT id, skill_name, description, content
			FROM harness_skill
			WHERE is_enabled = 1
			ORDER BY id
			""")
	List<DatabaseSkillDefinition> findAllEnabled();

	@Select("""
			SELECT skill_name
			FROM harness_skill
			WHERE is_enabled = 1
			ORDER BY id
			""")
	List<String> findAllEnabledNames();

	@Select("""
			SELECT id, skill_name, description, content
			FROM harness_skill
			WHERE skill_name = #{skillName} AND is_enabled = 1
			""")
	DatabaseSkillDefinition findEnabledByName(@Param("skillName") String skillName);

	@Select("""
			SELECT resource_path, content
			FROM harness_skill_resource
			WHERE skill_id = #{skillId}
			ORDER BY id
			""")
	List<DatabaseSkillResource> findResources(@Param("skillId") long skillId);

}
