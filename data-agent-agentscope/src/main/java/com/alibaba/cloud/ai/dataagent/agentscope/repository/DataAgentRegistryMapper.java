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

import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentProfile;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentSkillAssignment;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentToolAssignment;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ChatModelConfiguration;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DataAgentRegistryMapper {

	@Select("SELECT name, description, prompt FROM agent WHERE id = #{agentId}")
	AgentProfile findAgentById(@Param("agentId") long agentId);

	@Select("""
			SELECT tool_name, approval_mode, inject_agent_id, available_in_nl2sql_only
			FROM agent_tool
			WHERE agent_id = #{agentId} AND is_enabled = 1
			ORDER BY sort_order, id
			""")
	List<AgentToolAssignment> findEnabledTools(@Param("agentId") long agentId);

	@Select("""
			SELECT configured.skill_name, persisted_skill.update_time AS database_updated_at
			FROM agent_skill configured
			LEFT JOIN harness_skill persisted_skill
			  ON persisted_skill.skill_name = configured.skill_name AND persisted_skill.is_enabled = 1
			WHERE configured.agent_id = #{agentId} AND configured.is_enabled = 1
			ORDER BY configured.sort_order, configured.id
			""")
	List<AgentSkillAssignment> findEnabledSkills(@Param("agentId") long agentId);

	@Select("""
			SELECT COUNT(*)
			FROM agent_tool
			WHERE agent_id = #{agentId} AND is_enabled = 1 AND approval_mode = 'ALWAYS_ASK'
			""")
	int countAlwaysAskTools(@Param("agentId") long agentId);

	@Select("SELECT agent_id FROM chat_session WHERE id = #{conversationId}")
	Long findConversationAgentId(@Param("conversationId") String conversationId);

	@Select("""
			SELECT provider, base_url, api_key, model_name, temperature, max_tokens, completions_path
			FROM model_config
			WHERE model_type = 'CHAT' AND is_active = 1 AND is_deleted = 0
			ORDER BY id DESC
			LIMIT 1
			""")
	ChatModelConfiguration findActiveChatModel();

	@Insert("""
			INSERT INTO report_artifact (conversation_id, agent_id, source_query, content, create_time)
			VALUES (#{conversationId}, #{agentId}, #{sourceQuery}, #{content}, CURRENT_TIMESTAMP)
			""")
	int insertReport(@Param("conversationId") String conversationId, @Param("agentId") long agentId,
			@Param("sourceQuery") String sourceQuery, @Param("content") String content);

}
