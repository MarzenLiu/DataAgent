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
package com.alibaba.cloud.ai.dataagent.agentscope.agent;

import com.alibaba.cloud.ai.dataagent.agentscope.config.AgentScopeDataAgentProperties;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ModelSettings;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.SkillConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ToolConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.observability.LangfuseAgentScopeMiddleware;
import com.alibaba.cloud.ai.dataagent.agentscope.observability.LangfuseTelemetry;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DatabaseSkillRepository;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentScopeAgentFactory {

	private final DataAgentRegistryRepository repository;

	private final AgentScopeDataAgentProperties properties;

	private final DataAgentMcpClientFactory mcpClientFactory;

	private final DatabaseSkillRepository databaseSkillRepository;

	private final LangfuseTelemetry langfuseTelemetry;

	private final LangfuseAgentScopeMiddleware langfuseMiddleware;

	private final JsonFileAgentStateStore stateStore;

	private final Path workspace;

	private final Map<AgentKey, AgentHolder> agents = new ConcurrentHashMap<>();

	public AgentScopeAgentFactory(DataAgentRegistryRepository repository, AgentScopeDataAgentProperties properties,
			DataAgentMcpClientFactory mcpClientFactory, DatabaseSkillRepository databaseSkillRepository,
			LangfuseTelemetry langfuseTelemetry, LangfuseAgentScopeMiddleware langfuseMiddleware) {
		this.repository = repository;
		this.properties = properties;
		this.mcpClientFactory = mcpClientFactory;
		this.databaseSkillRepository = databaseSkillRepository;
		this.langfuseTelemetry = langfuseTelemetry;
		this.langfuseMiddleware = langfuseMiddleware;
		Path stateDirectory = properties.getStateDirectory().toAbsolutePath().normalize();
		this.workspace = stateDirectory.resolve("workspace");
		try {
			Files.createDirectories(workspace);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to create AgentScope state directory", ex);
		}
		this.stateStore = new JsonFileAgentStateStore(stateDirectory);
	}

	public HarnessAgent get(long agentId) {
		ModelSettings modelSettings = repository.findActiveChatModel().orElseGet(this::fallbackModelSettings);
		AgentConfiguration configuration = repository.findAgentConfiguration(agentId)
			.orElseThrow(() -> new IllegalStateException("Agent configuration was not found for agent " + agentId));
		if (!StringUtils.hasText(configuration.prompt())) {
			throw new IllegalStateException("Agent prompt is empty for agent " + agentId);
		}
		String fingerprint = modelSettings + "|" + configuration;
		AgentKey key = new AgentKey(agentId);
		AgentHolder holder = agents.compute(key, (ignored, existing) -> {
			if (existing != null && existing.fingerprint().equals(fingerprint)) {
				return existing;
			}
			if (existing != null) {
				existing.agent().close();
			}
			return new AgentHolder(fingerprint, createAgent(agentId, configuration, modelSettings));
		});
		return holder.agent();
	}

	private HarnessAgent createAgent(long agentId, AgentConfiguration configuration, ModelSettings settings) {
		Toolkit toolkit = new Toolkit();
		List<String> enabledTools = configuration.tools().stream().map(ToolConfiguration::toolName).toList();
		List<String> enabledSkills = configuration.skills().stream().map(SkillConfiguration::skillName).toList();
		if (!enabledTools.isEmpty()) {
			McpClientWrapper mcpClient = mcpClientFactory.connect();
			Map<String, Map<String, Object>> presetParameters = presetParameters(configuration.tools(), agentId);
			toolkit.registration()
				.mcpClient(mcpClient)
				.presetParameters(presetParameters)
				.enableTools(enabledTools)
				.apply();
		}
		HarnessAgent.Builder builder = HarnessAgent.builder()
			.name("configured_agent")
			.agentId("data-agent-%d".formatted(agentId))
			.description(value(configuration.description()))
			.sysPrompt(configuration.prompt().trim())
			.model(createModel(settings))
			.toolkit(toolkit)
			.workspace(workspace)
			.stateStore(stateStore)
			.maxIters(12)
			.compaction(CompactionConfig.builder()
				.triggerMessages(Math.max(4, properties.getCompactionTriggerMessages()))
				.keepMessages(Math.max(2, properties.getCompactionKeepMessages()))
				.keepTokens(0)
				.flushBeforeCompact(false)
				.offloadBeforeCompact(false)
				.build())
			.disableFilesystemTools()
			.disableShellTool()
			.disableMemoryTools()
			.disableMemoryHooks()
			.disableSubagents()
			.disableDynamicSkills()
			.disableDefaultWorkspaceSkills()
			.disableDynamicSubagents()
			.disableToolsConfig()
			.disableAtPathExpansion()
			.disableWorkspaceContext()
			.permissionContext(PermissionContextState.builder().mode(PermissionMode.DEFAULT).build());
		configureSkills(builder, enabledSkills);
		if (langfuseTelemetry.isEnabled()) {
			builder.middleware(langfuseTelemetry.getAgentScopeTracingMiddleware()).middleware(langfuseMiddleware);
		}
		return builder.build();
	}

	private void configureSkills(HarnessAgent.Builder builder, List<String> enabledSkills) {
		if (enabledSkills.isEmpty()) {
			return;
		}
		Path skillsDirectory = properties.getSkillsDirectory().toAbsolutePath().normalize();
		boolean hasProjectDirectory = Files.isDirectory(skillsDirectory);
		Set<String> databaseSkills = new HashSet<>(databaseSkillRepository.getAllSkillNames());
		boolean usesDatabaseSkills = false;
		if (hasProjectDirectory) {
			builder.projectGlobalSkillsDir(skillsDirectory);
		}
		for (String skillName : enabledSkills) {
			if (!StringUtils.hasText(skillName)) {
				throw new IllegalStateException("Configured Harness skill name must not be blank");
			}
			boolean databaseSkill = databaseSkills.contains(skillName);
			usesDatabaseSkills = usesDatabaseSkills || databaseSkill;
			Path skillFile = skillsDirectory.resolve(skillName).resolve("SKILL.md").normalize();
			boolean projectSkill = hasProjectDirectory && skillFile.startsWith(skillsDirectory)
					&& Files.isRegularFile(skillFile);
			if (!databaseSkill && !projectSkill) {
				throw new IllegalStateException("Configured Harness skill was not found: " + skillName);
			}
		}
		if (usesDatabaseSkills) {
			builder.skillRepository(databaseSkillRepository);
		}
		builder.enableSkills(enabledSkills.toArray(String[]::new));
	}

	private Map<String, Map<String, Object>> presetParameters(List<ToolConfiguration> tools, long agentId) {
		Map<String, Map<String, Object>> result = new LinkedHashMap<>();
		for (ToolConfiguration tool : tools) {
			Map<String, Object> parameters = new LinkedHashMap<>();
			if (tool.injectAgentId()) {
				parameters.put("agentId", agentId);
			}
			if (!parameters.isEmpty()) {
				result.put(tool.toolName(), parameters);
			}
		}
		return result;
	}

	public Model createModel(ModelSettings settings) {
		if (!StringUtils.hasText(settings.apiKey())) {
			throw new IllegalStateException(
					"No active chat model API key is configured in model_config or agentscope.data-agent.model.api-key");
		}
		GenerateOptions options = GenerateOptions.builder()
			.temperature(settings.temperature())
			.maxTokens(settings.maxTokens())
			.parallelToolCalls(false)
			.build();
		String provider = value(settings.provider()).toLowerCase(Locale.ROOT);
		if (provider.contains("dashscope") || provider.contains("bailian") || provider.contains("百炼")) {
			DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
				.apiKey(settings.apiKey())
				.modelName(settings.modelName())
				.stream(true)
				.defaultOptions(options);
			if (StringUtils.hasText(settings.baseUrl())) {
				builder.baseUrl(settings.baseUrl());
			}
			return builder.build();
		}
		OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
			.apiKey(settings.apiKey())
			.modelName(settings.modelName())
			.stream(true)
			.generateOptions(options);
		if (StringUtils.hasText(settings.baseUrl())) {
			builder.baseUrl(settings.baseUrl());
		}
		if (StringUtils.hasText(settings.endpointPath())) {
			builder.endpointPath(settings.endpointPath());
		}
		return builder.build();
	}

	public Model createActiveModel() {
		return createModel(repository.findActiveChatModel().orElseGet(this::fallbackModelSettings));
	}

	public void deleteSessionState(long agentId, String sessionId) {
		stateStore.delete("data-agent-%d".formatted(agentId), sessionId);
	}

	private ModelSettings fallbackModelSettings() {
		AgentScopeDataAgentProperties.Model model = properties.getModel();
		return new ModelSettings(model.getProvider(), model.getBaseUrl(), model.getApiKey(), model.getName(),
				model.getTemperature(), model.getMaxTokens(), model.getEndpointPath());
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

}
