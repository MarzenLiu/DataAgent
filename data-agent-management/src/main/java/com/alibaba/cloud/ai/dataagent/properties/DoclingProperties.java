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
package com.alibaba.cloud.ai.dataagent.properties;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "data-agent.docling")
public class DoclingProperties {

	private boolean enabled;

	private String baseUrl = "http://localhost:5001";

	private String apiKey;

	private Duration connectTimeout = Duration.ofSeconds(5);

	private Duration readTimeout = Duration.ofMinutes(10);

	private Duration asyncPollInterval = Duration.ofSeconds(2);

	private Duration asyncTimeout = Duration.ofMinutes(15);

	private boolean fallbackToTika = true;

	private final Pdf pdf = new Pdf();

	private final Excel excel = new Excel();

	@Getter
	@Setter
	public static class Pdf {

		private boolean ocrEnabled = true;

		private int maxTableRowsPerChunk = 20;

	}

	@Getter
	@Setter
	public static class Excel {

		private int maxTableRowsPerChunk = 50;

	}

}
