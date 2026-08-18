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
 * Enabled skill assignment and optional database-backed skill revision.
 *
 * <p>
 * Primary database table: {@code agent_skill}, column {@code skill_name}; joined table:
 * {@code harness_skill}, column {@code update_time}. A {@code null}
 * {@code databaseUpdatedAt} means the assignment resolves to a project-directory skill.
 *
 * @param skillName Harness skill name assigned to the agent
 * @param databaseUpdatedAt last update time of the matching database skill, if present
 */
public record AgentSkillAssignment(String skillName, LocalDateTime databaseUpdatedAt) {
}
