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
package com.alibaba.cloud.ai.dataagent.dto.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserProfileExtractionDTO {

	@JsonProperty("contains_profile")
	@JsonPropertyDescription("输入是否在介绍或更新用户自己的个人资料")
	private boolean containsProfile;

	@JsonProperty("nickname")
	@JsonPropertyDescription("用户希望在报告中使用的昵称或称呼；未提及时返回空字符串")
	private String nickname;

	@JsonProperty("position")
	@JsonPropertyDescription("用户的职位或岗位；未提及时返回空字符串")
	private String position;

	@JsonProperty("preferences")
	@JsonPropertyDescription("用户的报告或沟通偏好；未提及时返回空字符串，此字段可选")
	private String preferences;

}
