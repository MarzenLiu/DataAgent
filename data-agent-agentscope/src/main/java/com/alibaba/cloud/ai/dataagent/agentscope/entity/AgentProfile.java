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
 * Agent identity and prompt fields used to build an AgentScope agent.
 *
 * <p>
 * Database table: {@code agent}. Columns: {@code name}, {@code description}, and
 * {@code prompt}. The primary key {@code id} is supplied as a query condition and is not
 * repeated in this read model.
 *
 * @param name agent display name
 * @param description agent purpose shown to the runtime
 * @param prompt system prompt used by the agent
 */
public record AgentProfile(String name, String description, String prompt) {
}
