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
 * Active chat-model configuration loaded from persistent storage.
 *
 * <p>
 * Database table: {@code model_config}. Columns: {@code provider}, {@code base_url},
 * {@code api_key}, {@code model_name}, {@code temperature}, {@code max_tokens}, and
 * {@code completions_path}. The mapper selects the latest active, non-deleted CHAT row.
 *
 * @param provider model provider identifier
 * @param baseUrl provider API base URL
 * @param apiKey provider API key
 * @param modelName provider model name
 * @param temperature generation temperature
 * @param maxTokens maximum generated tokens
 * @param completionsPath provider-specific completions endpoint path
 */
public record ChatModelConfiguration(String provider, String baseUrl, String apiKey, String modelName,
		Double temperature, Integer maxTokens, String completionsPath) {
}
