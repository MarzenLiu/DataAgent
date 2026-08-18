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

import java.time.LocalDateTime;

/**
 * Runtime-visible Harness skill assigned to an agent.
 *
 * <p>
 * Database sources: {@code agent_skill.skill_name} and, for database-backed skills,
 * {@code harness_skill.update_time}. A missing database timestamp identifies a skill
 * loaded from the configured project directory.
 *
 * @param skillName Harness skill name
 * @param databaseUpdatedAt database skill revision, or {@code null} for a directory skill
 */
public record SkillConfiguration(String skillName, LocalDateTime databaseUpdatedAt) {
}
