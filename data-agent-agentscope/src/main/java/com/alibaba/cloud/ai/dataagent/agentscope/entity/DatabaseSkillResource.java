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
package com.alibaba.cloud.ai.dataagent.agentscope.entity;

/**
 * Text resource belonging to a database-backed Harness skill.
 *
 * <p>
 * Database table: {@code harness_skill_resource}. Columns: {@code resource_path} and
 * {@code content}. The owning {@code skill_id} is supplied as a query condition.
 *
 * @param resourcePath relative path inside the skill
 * @param content UTF-8 text resource content
 */
public record DatabaseSkillResource(String resourcePath, String content) {
}
