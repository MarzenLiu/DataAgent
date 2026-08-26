package com.alibaba.cloud.ai.dataagent.agentscope.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.dataagent.agentscope.config.AgentScopeDataAgentProperties;
import com.alibaba.cloud.ai.dataagent.agentscope.config.LangfuseProperties;
import com.alibaba.cloud.ai.dataagent.agentscope.observability.LangfuseAgentScopeMiddleware;
import com.alibaba.cloud.ai.dataagent.agentscope.observability.LangfuseTelemetry;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DatabaseSkillRepository;
import com.alibaba.cloud.ai.dataagent.agentscope.repository.DataAgentRegistryRepository;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ApprovalMode;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.SkillConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ToolConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import io.agentscope.core.agent.RuntimeContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentScopeAgentFactoryTest {

	@TempDir
	Path stateDirectory;

	@Test
	void buildsHarnessWithoutSpringAi() throws Exception {
		Path skillsDirectory = stateDirectory.resolve("skills");
		Path skillDirectory = skillsDirectory.resolve("data-analysis-sop");
		Files.createDirectories(skillDirectory);
		Files.writeString(skillDirectory.resolve("SKILL.md"), """
				---
				name: data-analysis-sop
				description: Reliable data analysis procedure.
				---

				Inspect the datasource before writing SQL.
				""");
		DataAgentRegistryRepository repository = mock(DataAgentRegistryRepository.class);
		when(repository.findActiveChatModel()).thenReturn(Optional.empty());
		when(repository.findAgentConfiguration(7L))
			.thenReturn(Optional.of(new AgentConfiguration("测试智能体", "测试描述", "完整的数据库提示词",
					List.of(new ToolConfiguration("inspect_data_source", ApprovalMode.ALLOW, true, true),
							new ToolConfiguration("execute_read_only_sql", ApprovalMode.ASK_WHEN_HITL, true, false),
							new ToolConfiguration("search_knowledge_base", ApprovalMode.ALLOW, true, true)),
					List.of(new SkillConfiguration("data-analysis-sop", null),
							new SkillConfiguration("result-validation-sop", null)))));
		AgentScopeDataAgentProperties properties = new AgentScopeDataAgentProperties();
		properties.setStateDirectory(stateDirectory);
		properties.setSkillsDirectory(skillsDirectory);
		properties.getModel().setApiKey("test-key");
		ObjectMapper objectMapper = new ObjectMapper();
		McpClientWrapper mcpClient = mock(McpClientWrapper.class);
		when(mcpClient.getName()).thenReturn("data-agent-tools");
		when(mcpClient.initialize()).thenReturn(reactor.core.publisher.Mono.empty());
		when(mcpClient.listTools()).thenReturn(reactor.core.publisher.Mono
			.just(List.of(tool("inspect_data_source", Map.of("agentId", property("integer")), List.of("agentId")),
					tool("execute_read_only_sql", Map.of("agentId", property("integer"), "sql", property("string")),
							List.of("agentId", "sql")),
					tool("search_knowledge_base", Map.of("agentId", property("integer"), "query", property("string")),
							List.of("agentId", "query")))));
		DataAgentMcpClientFactory mcpClientFactory = mock(DataAgentMcpClientFactory.class);
		when(mcpClientFactory.connect()).thenReturn(mcpClient);
		DatabaseSkillRepository databaseSkillRepository = mock(DatabaseSkillRepository.class);
		AgentSkill databaseSkill = new AgentSkill("result-validation-sop", "Validate query results.",
				"Check totals, nulls, and result granularity before answering.", Map.of(), "database");
		when(databaseSkillRepository.getAllSkillNames()).thenReturn(List.of("result-validation-sop"));
		when(databaseSkillRepository.getAllSkills()).thenReturn(List.of(databaseSkill));
		when(databaseSkillRepository.getSource()).thenReturn("database");
		LangfuseTelemetry telemetry = new LangfuseTelemetry(new LangfuseProperties());
		AgentStateStore stateStore = mock(AgentStateStore.class);
		AgentScopeAgentFactory factory = new AgentScopeAgentFactory(repository, properties, mcpClientFactory,
				databaseSkillRepository, telemetry, new LangfuseAgentScopeMiddleware(objectMapper), new InMemoryStore(),
				stateStore);
		AgentRuntimePolicy runtimePolicy = new AgentRuntimePolicy(repository);

		HarnessAgent agent = factory.get(7L);
		assertThat(agent.getName()).isEqualTo("configured_agent");
		assertThat(agent.getToolkit().getToolNames()).contains("inspect_data_source", "execute_read_only_sql",
				"search_knowledge_base", "load_skill_through_path", "wait_async_results", "memory_search", "memory_get",
				"memory_save", "session_search");
		assertThat(agent.getSkillRepositories()).hasSize(2);
		assertThat(agent.getSkillRepositories().get(0).getAllSkillNames()).containsExactly("data-analysis-sop");
		assertThat(agent.getSkillRepositories().get(1).getAllSkillNames()).containsExactly("result-validation-sop");
		agent.getToolkit()
			.getToolSchemas()
			.stream()
			.filter(schema -> List.of("inspect_data_source", "execute_read_only_sql", "search_knowledge_base")
				.contains(schema.getName()))
			.forEach(schema -> {
				Map<?, ?> visibleProperties = (Map<?, ?>) schema.getParameters().get("properties");
				assertThat(visibleProperties.containsKey("agentId")).isFalse();
				assertThat(visibleProperties.containsKey("nl2sqlOnly")).isFalse();
			});
		RuntimeContext runtime = RuntimeContext.builder().userId("1").sessionId("same-conversation").build();
		runtimePolicy.apply(7L, agent, runtime, false, false);
		var nonHitlPermissions = agent.getDelegate().getAgentState(runtime).getPermissionContext();
		assertThat(nonHitlPermissions.getAskRules()).isEmpty();
		assertThat(nonHitlPermissions.getAllowRules()).containsKey("execute_read_only_sql");

		runtimePolicy.apply(7L, agent, runtime, true, false);
		var permissionContext = agent.getDelegate().getAgentState(runtime).getPermissionContext();
		assertThat(permissionContext.getMode()).isEqualTo(io.agentscope.core.permission.PermissionMode.DEFAULT);
		assertThat(permissionContext.getAskRules()).containsKey("execute_read_only_sql");
		assertThat(permissionContext.getAllowRules()).containsOnlyKeys("inspect_data_source", "search_knowledge_base");

		runtimePolicy.apply(7L, agent, runtime, false, true);
		var nl2sqlPermissions = agent.getDelegate().getAgentState(runtime).getPermissionContext();
		assertThat(nl2sqlPermissions.getDenyRules()).containsKey("execute_read_only_sql");
		assertThat(factory.get(7L)).isSameAs(agent);
		agent.close();
	}

	private Tool tool(String name, Map<String, Object> properties, List<String> required) {
		return Tool.builder()
			.name(name)
			.description(name)
			.inputSchema(new JsonSchema("object", properties, required, false, null, null))
			.build();
	}

	private Map<String, Object> property(String type) {
		return Map.of("type", type);
	}

}
