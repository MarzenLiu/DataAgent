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

import com.alibaba.cloud.ai.dataagent.entity.UserProfile;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.memory.UserProfileMemoryService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class UserProfileNodeTest {

	@Mock
	private UserProfileMemoryService memoryService;

	@Mock
	private LlmService llmService;

	private UserProfileNode node;

	private OverAllState state;

	@BeforeEach
	void setUp() {
		node = new UserProfileNode(memoryService, llmService, new JsonParseUtil(llmService));
		state = new OverAllState();
		state.registerKeyAndStrategy(CONVERSATION_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.updateState(Map.of(CONVERSATION_ID, "session-1", INPUT_KEY, "分析本月销售额"));
		mockExtraction(false, "", "", "");
	}

	@Test
	void missingProfileGuidesUserAndEndsTurn() {
		Map<String, Object> result = node.apply(state);

		assertEquals("waiting", result.get(USER_PROFILE_STATUS));
		assertTrue(result.get(FINAL_ANSWER).toString().contains("昵称"));
		verify(memoryService, never()).save(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void naturalLanguageProfileIsSavedAsLongTermMemory() {
		state.updateState(Map.of(INPUT_KEY, "大家叫我小林，我是一名数据分析师，报告最好结论优先"));
		mockExtraction(true, "小林", "数据分析师", "结论优先");
		UserProfile profile = UserProfile.builder()
			.nickname("小林")
			.position("数据分析师")
			.preferences("结论优先")
			.build();
		when(memoryService.save("session-1", "小林", "数据分析师", "结论优先")).thenReturn(profile);

		Map<String, Object> result = node.apply(state);

		assertEquals("saved", result.get(USER_PROFILE_STATUS));
		assertSame(profile, result.get(USER_PROFILE));
		verify(memoryService, never()).find(anyString());
		verify(llmService).callUserObserved(eq("user-profile.extract-profile"),
				argThat(prompt -> prompt.contains("只允许输出一个合法 JSON 对象")
				&& prompt.contains("\"contains_profile\"") && prompt.contains("\"preferences\"")));
		verify(llmService, never()).callUserObserved(anyString(), anyString(), any());
	}

	@Test
	void preferencesAreOptional() {
		state.updateState(Map.of(INPUT_KEY, "我叫小林，目前担任数据分析师"));
		mockExtraction(true, "小林", "数据分析师", "");
		UserProfile profile = UserProfile.builder().nickname("小林").position("数据分析师").preferences("").build();
		when(memoryService.save("session-1", "小林", "数据分析师", "")).thenReturn(profile);

		Map<String, Object> result = node.apply(state);

		assertEquals("saved", result.get(USER_PROFILE_STATUS));
	}

	@Test
	void completeProfileAllowsOriginalGraphToContinue() {
		UserProfile profile = UserProfile.builder()
			.nickname("小林")
			.position("数据分析师")
			.preferences("结论优先")
			.build();
		when(memoryService.find("session-1")).thenReturn(profile);

		Map<String, Object> result = node.apply(state);

		assertEquals("complete", result.get(USER_PROFILE_STATUS));
		assertSame(profile, result.get(USER_PROFILE));
	}

	private void mockExtraction(boolean containsProfile, String nickname, String position, String preferences) {
		String json = """
				{"contains_profile":%s,"nickname":"%s","position":"%s","preferences":"%s"}
				""".formatted(containsProfile, nickname, position, preferences);
		lenient()
			.when(llmService.callUserObserved(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(json)));
	}

}
