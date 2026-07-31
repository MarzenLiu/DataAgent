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
package com.alibaba.cloud.ai.dataagent.service.memory;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.entity.UserProfile;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.UserProfileMapper;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserProfileMemoryService {

	private final UserProfileMapper userProfileMapper;

	private final ChatSessionMapper chatSessionMapper;

	public UserProfile find(String conversationId) {
		return userProfileMapper.selectByMemoryKey(resolveIdentity(conversationId).memoryKey());
	}

	public UserProfile save(String conversationId, String nickname, String position, String preferences) {
		Identity identity = resolveIdentity(conversationId);
		UserProfile profile = UserProfile.builder()
			.memoryKey(identity.memoryKey())
			.userId(identity.userId())
			.conversationId(conversationId)
			.nickname(StringUtils.trim(nickname))
			.position(StringUtils.trim(position))
			.preferences(StringUtils.trim(preferences))
			.build();
		if (userProfileMapper.selectByMemoryKey(identity.memoryKey()) == null) {
			userProfileMapper.insert(profile);
		}
		else {
			userProfileMapper.update(profile);
		}
		return profile;
	}

	private Identity resolveIdentity(String conversationId) {
		ChatSession session = StringUtils.isBlank(conversationId) ? null
				: chatSessionMapper.selectBySessionId(conversationId);
		if (session != null && session.getUserId() != null) {
			return new Identity("user:" + session.getUserId(), session.getUserId());
		}
		return new Identity("conversation:" + StringUtils.defaultString(conversationId), null);
	}

	private record Identity(String memoryKey, Long userId) {
	}

}
