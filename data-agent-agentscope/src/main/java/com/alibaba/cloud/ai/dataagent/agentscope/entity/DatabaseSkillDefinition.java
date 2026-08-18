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
 * Read-only Harness skill definition stored in the application database.
 *
 * <p>
 * Database table: {@code harness_skill}. Columns: {@code id}, {@code skill_name},
 * {@code description}, and {@code content}. Repository queries only expose rows where
 * {@code is_enabled = 1}.
 *
 * @param id skill primary key
 * @param skillName unique Harness skill name
 * @param description short skill description
 * @param content skill instruction body
 */
public record DatabaseSkillDefinition(long id, String skillName, String description, String content) {
}
