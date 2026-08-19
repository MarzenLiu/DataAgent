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
package com.alibaba.cloud.ai.dataagent.config;

import ai.docling.serve.api.DoclingServeApi;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(DoclingProperties.class)
public class DoclingConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "data-agent.docling", name = "enabled", havingValue = "true")
	public DoclingServeApi doclingServeApi(DoclingProperties properties) {
		var builder = DoclingServeApi.builder()
			.baseUrl(properties.getBaseUrl())
			.connectTimeout(properties.getConnectTimeout())
			.readTimeout(properties.getReadTimeout())
			.asyncPollInterval(properties.getAsyncPollInterval())
			.asyncTimeout(properties.getAsyncTimeout());
		if (StringUtils.hasText(properties.getApiKey())) {
			builder.apiKey(properties.getApiKey());
		}
		return builder.build();
	}

}
