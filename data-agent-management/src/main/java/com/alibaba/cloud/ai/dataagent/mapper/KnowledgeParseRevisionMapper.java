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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.KnowledgeParseRevision;
import com.alibaba.cloud.ai.dataagent.enums.ParseRevisionStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeParseRevisionMapper {

	@Select("SELECT COALESCE(MAX(revision_no), 0) + 1 FROM knowledge_parse_revision WHERE knowledge_id = #{knowledgeId}")
	int nextRevisionNo(@Param("knowledgeId") Integer knowledgeId);

	@Insert("""
			INSERT INTO knowledge_parse_revision
				(knowledge_id, revision_no, status, parser, parser_version, chunk_count, warning_count,
				 review_comment, error_msg, created_time, updated_time)
			VALUES
				(#{knowledgeId}, #{revisionNo}, #{status}, #{parser}, #{parserVersion}, #{chunkCount}, #{warningCount},
				 #{reviewComment}, #{errorMsg}, #{createdTime}, #{updatedTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(KnowledgeParseRevision revision);

	@Select("""
			SELECT * FROM knowledge_parse_revision
			WHERE knowledge_id = #{knowledgeId}
			ORDER BY revision_no DESC
			LIMIT 1
			""")
	KnowledgeParseRevision selectLatest(@Param("knowledgeId") Integer knowledgeId);

	@Select("SELECT * FROM knowledge_parse_revision WHERE id = #{id}")
	KnowledgeParseRevision selectById(@Param("id") Long id);

	@Update("""
			UPDATE knowledge_parse_revision
			SET status = #{status}, review_comment = #{comment}, reviewed_time = #{reviewedTime},
				updated_time = CURRENT_TIMESTAMP
			WHERE id = #{id} AND status = #{expectedStatus}
			""")
	int review(@Param("id") Long id, @Param("expectedStatus") ParseRevisionStatus expectedStatus,
			@Param("status") ParseRevisionStatus status, @Param("comment") String comment,
			@Param("reviewedTime") LocalDateTime reviewedTime);

	@Update("""
			UPDATE knowledge_parse_revision
			SET status = #{status}, parser = COALESCE(#{parser}, parser),
				parser_version = COALESCE(#{parserVersion}, parser_version),
				error_msg = #{errorMsg}, chunk_count = #{chunkCount},
				warning_count = #{warningCount}, updated_time = CURRENT_TIMESTAMP
			WHERE id = #{id}
			""")
	int updateResult(@Param("id") Long id, @Param("status") ParseRevisionStatus status,
			@Param("parser") String parser, @Param("parserVersion") String parserVersion,
			@Param("errorMsg") String errorMsg, @Param("chunkCount") Integer chunkCount,
			@Param("warningCount") Integer warningCount);

}
