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
package com.alibaba.cloud.ai.dataagent.agentscope.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agentscope.data-agent")
public class AgentScopeDataAgentProperties {

	private final Model model = new Model();

	private final Mcp mcp = new Mcp();

	private Path stateDirectory = Path.of(".agentscope", "state");

	private Path skillsDirectory = Path.of("data-agent-agentscope", "skills");

	private int compactionTriggerMessages = 30;

	private int compactionKeepMessages = 10;

	public Model getModel() {
		return model;
	}

	public Mcp getMcp() {
		return mcp;
	}

	public Path getStateDirectory() {
		return stateDirectory;
	}

	public void setStateDirectory(Path stateDirectory) {
		this.stateDirectory = stateDirectory;
	}

	public Path getSkillsDirectory() {
		return skillsDirectory;
	}

	public void setSkillsDirectory(Path skillsDirectory) {
		this.skillsDirectory = skillsDirectory;
	}

	public int getCompactionTriggerMessages() {
		return compactionTriggerMessages;
	}

	public void setCompactionTriggerMessages(int compactionTriggerMessages) {
		this.compactionTriggerMessages = compactionTriggerMessages;
	}

	public int getCompactionKeepMessages() {
		return compactionKeepMessages;
	}

	public void setCompactionKeepMessages(int compactionKeepMessages) {
		this.compactionKeepMessages = compactionKeepMessages;
	}

	public static class Model {

		private String provider = "dashscope";

		private String apiKey;

		private String name = "qwen-plus";

		private String baseUrl;

		private String endpointPath;

		private double temperature = 0.1;

		private int maxTokens = 8000;

		public String getProvider() {
			return provider;
		}

		public void setProvider(String provider) {
			this.provider = provider;
		}

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getEndpointPath() {
			return endpointPath;
		}

		public void setEndpointPath(String endpointPath) {
			this.endpointPath = endpointPath;
		}

		public double getTemperature() {
			return temperature;
		}

		public void setTemperature(double temperature) {
			this.temperature = temperature;
		}

		public int getMaxTokens() {
			return maxTokens;
		}

		public void setMaxTokens(int maxTokens) {
			this.maxTokens = maxTokens;
		}

	}

	public static class Mcp {

		private String url = "http://127.0.0.1:8068/mcp";

		private Duration timeout = Duration.ofSeconds(60);

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public Duration getTimeout() {
			return timeout;
		}

		public void setTimeout(Duration timeout) {
			this.timeout = timeout;
		}

	}

}
