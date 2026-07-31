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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.dto.memory.UserProfileExtractionDTO;
import com.alibaba.cloud.ai.dataagent.entity.UserProfile;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.memory.UserProfileMemoryService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.util.MarkdownParserUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * Collects a user profile once and persists it as cross-session long-term memory.
 */
@Component
@AllArgsConstructor
@Slf4j
public class UserProfileNode implements NodeAction {

	private static final BeanOutputConverter<UserProfileExtractionDTO> OUTPUT_CONVERTER = new BeanOutputConverter<>(
			UserProfileExtractionDTO.class);

	private static final String GUIDE = """
			为了生成更贴合你的分析报告，请先告诉我你的昵称和职位（会保存为长期记忆，后续会话无需重复填写）。

			你可以自然地介绍，例如：“我叫小林，是一名数据分析师，报告请结论优先。”

			偏好是可选的，也可以之后再补充。""";

	private final UserProfileMemoryService memoryService;

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	@Override
	public Map<String, Object> apply(OverAllState state) {
		String conversationId = StateUtil.getStringValue(state, CONVERSATION_ID);
		if (StringUtils.isBlank(conversationId)) {
			return Map.of(USER_PROFILE_STATUS, "complete");
		}

		UserProfileExtractionDTO extracted = extractProfile(StateUtil.getStringValue(state, INPUT_KEY));
		if (extracted != null && extracted.isContainsProfile()
				&& !StringUtils.isAnyBlank(extracted.getNickname(), extracted.getPosition())) {
			UserProfile saved = memoryService.save(conversationId, extracted.getNickname(), extracted.getPosition(),
					StringUtils.defaultString(extracted.getPreferences()));
			return Map.of(USER_PROFILE_STATUS, "saved", USER_PROFILE, saved, FINAL_ANSWER,
					"已记住你的资料：昵称「" + extracted.getNickname() + "」、职位「" + extracted.getPosition()
							+ "」。偏好会应用到后续报告中，请发送你的数据分析问题。");
		}

		UserProfile existing = memoryService.find(conversationId);
		if (isComplete(existing)) {
			return Map.of(USER_PROFILE_STATUS, "complete", USER_PROFILE, existing);
		}

		return Map.of(USER_PROFILE_STATUS, "waiting", FINAL_ANSWER, GUIDE);
	}

	private UserProfileExtractionDTO extractProfile(String input) {
		String prompt = """
				请判断下面的输入是否在介绍或更新用户自己的个人资料，并结构化提取昵称、职位和偏好。
				昵称和职位是用户画像的必填信息，偏好是可选信息。
				不要把数据分析问题中的人物、客户、员工或职位误认为当前用户。
				未明确提及的字段必须返回空字符串，禁止猜测。

				# 输出规则
				- 只允许输出一个合法 JSON 对象。
				- 禁止输出 Markdown 代码块、解释、前缀、后缀或任何 JSON 之外的文字。
				- 必须包含 contains_profile、nickname、position、preferences 四个字段，不得遗漏。
				- contains_profile 必须为 JSON 布尔值，其他三个字段必须为 JSON 字符串。

				# JSON Schema
				%s

				# 输出示例
				{"contains_profile":true,"nickname":"小林","position":"数据分析师","preferences":"结论优先"}

				# 用户输入
				<user_input>
				%s
				</user_input>
				""".formatted(OUTPUT_CONVERTER.getFormat(), StringUtils.defaultString(input));
		try {
			List<String> chunks = llmService.callUser(prompt)
				.map(ChatResponseUtil::getText)
				.collectList()
				.block();
			String rawOutput = String.join("", chunks == null ? List.of() : chunks);
			String json = MarkdownParserUtil.extractRawText(rawOutput.trim());
			return jsonParseUtil.tryConvertToObject(json, UserProfileExtractionDTO.class);
		}
		catch (RuntimeException ex) {
			log.warn("Failed to extract user profile from input; falling back to stored profile lookup", ex);
			return null;
		}
	}

	private boolean isComplete(UserProfile profile) {
		return profile != null && !StringUtils.isAnyBlank(profile.getNickname(), profile.getPosition());
	}

}
