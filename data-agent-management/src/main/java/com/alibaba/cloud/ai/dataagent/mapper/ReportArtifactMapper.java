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
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportArtifactMapper {

	@Select("""
			SELECT * FROM report_artifact
			WHERE conversation_id = #{conversationId} AND agent_id = #{agentId}
			ORDER BY id DESC
			LIMIT 1
			""")
	ReportArtifact selectLatest(@Param("conversationId") String conversationId, @Param("agentId") Long agentId);

	@Insert("""
			INSERT INTO report_artifact (conversation_id, agent_id, source_query, content, create_time)
			VALUES (#{conversationId}, #{agentId}, #{sourceQuery}, #{content}, NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(ReportArtifact artifact);

}
