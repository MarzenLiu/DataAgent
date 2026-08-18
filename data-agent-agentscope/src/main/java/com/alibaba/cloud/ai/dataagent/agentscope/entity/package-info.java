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
/**
 * Business entities and database read models used by the AgentScope module.
 *
 * <p>
 * The package contains projections and aggregates sourced from {@code agent},
 * {@code agent_tool}, {@code agent_skill}, {@code harness_skill},
 * {@code harness_skill_resource}, and {@code model_config}. Each record is declared in
 * its own source file, and each type documents whether it maps directly to a table or
 * combines multiple configuration sources.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.entity;
