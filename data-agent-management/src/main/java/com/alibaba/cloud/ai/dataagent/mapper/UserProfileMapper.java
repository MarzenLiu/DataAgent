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

import com.alibaba.cloud.ai.dataagent.entity.UserProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper {

	@Select("SELECT * FROM user_profile WHERE memory_key = #{memoryKey}")
	UserProfile selectByMemoryKey(@Param("memoryKey") String memoryKey);

	@Insert("""
			INSERT INTO user_profile
				(memory_key, user_id, conversation_id, nickname, position, preferences, create_time, update_time)
			VALUES
				(#{memoryKey}, #{userId}, #{conversationId}, #{nickname}, #{position}, #{preferences}, NOW(), NOW())
			""")
	int insert(UserProfile profile);

	@Update("""
			UPDATE user_profile
			SET nickname = #{nickname}, position = #{position}, preferences = #{preferences}, update_time = NOW()
			WHERE memory_key = #{memoryKey}
			""")
	int update(UserProfile profile);

}
