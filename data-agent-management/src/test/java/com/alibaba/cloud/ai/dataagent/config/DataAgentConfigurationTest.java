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

import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorServiceFactory;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageServiceFactory;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.AgentBasicInfo;
import com.alibaba.cloud.ai.dataagent.workflow.agent.capability.WorkflowCapabilityAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_NODE;
import static org.assertj.core.api.Assertions.assertThat;

class DataAgentConfigurationTest {

	@Test
	void buildSupervisorPrompt_usesAgentDescriptorConfiguration() {
		DataAgentConfiguration configuration = new DataAgentConfiguration();
		Agent configuredAgent = new WorkflowCapabilityAgent(
				new AgentBasicInfo("configured_agent", "Configured responsibility.", "configured work is pending."),
				() -> null);

		String prompt = configuration.buildSupervisorPrompt(List.of(configuredAgent));

		assertThat(prompt).contains("configured_agent", "Configured responsibility.",
				"configured work is pending.");
	}

	@Test
	void normalizeRouterResponse_wrapsBareFinishInJsonArray() {
		DataAgentConfiguration configuration = new DataAgentConfiguration();

		assertThat(ChatResponseUtil.getText(
				configuration.normalizeRouterResponse(ChatResponseUtil.createPureResponse("FINISH"))))
			.isEqualTo("[\"FINISH\"]");
		assertThat(ChatResponseUtil.getText(configuration
			.normalizeRouterStream(reactor.core.publisher.Flux.just(ChatResponseUtil.createPureResponse("FIN"),
					ChatResponseUtil.createPureResponse("ISH")))
			.blockFirst())).isEqualTo("[\"FINISH\"]");
	}

	@Test
	void serializePrompt_producesStructuredJsonForLangfuse() throws Exception {
		DataAgentConfiguration configuration = new DataAgentConfiguration();
		Prompt prompt = new Prompt(List.of(new SystemMessage("Route deterministically"), new UserMessage("Next agent")),
				ChatOptions.builder().model("router-model").temperature(0.0).build());

		JsonNode payload = JsonUtil.getObjectMapper().readTree(configuration.serializePrompt(prompt));

		assertThat(payload.path("messages").isArray()).isTrue();
		assertThat(payload.path("messages").get(0).path("role").asText()).isEqualTo("system");
		assertThat(payload.path("messages").get(0).path("content").asText()).isEqualTo("Route deterministically");
		assertThat(payload.path("messages").get(1).path("role").asText()).isEqualTo("user");
		assertThat(payload.path("messages").get(1).path("content").asText()).isEqualTo("Next agent");
		assertThat(payload.path("options").path("model").asText()).isEqualTo("router-model");
	}

	@Test
	void runtimeServiceFactories_arePlainSelectorsInsteadOfSpringFactoryBeans() {
		assertThat(FactoryBean.class.isAssignableFrom(FileStorageServiceFactory.class)).isFalse();
		assertThat(FactoryBean.class.isAssignableFrom(CodePoolExecutorServiceFactory.class)).isFalse();
	}

	@Test
	void nl2sqlGraphCompileConfig_usesProvidedFrameworkSaver() throws Exception {
		DataAgentConfiguration configuration = new DataAgentConfiguration();
		BaseCheckpointSaver configuredSaver = configuration.memoryCheckpointSaver();
		CompileConfig compileConfig = configuration.nl2sqlGraphCompileConfig(configuredSaver);
		BaseCheckpointSaver checkpointSaver = compileConfig.checkpointSaver().orElseThrow();
		RunnableConfig runnableConfig = RunnableConfig.builder().threadId("chat-session-1").build();
		Checkpoint checkpoint = Checkpoint.builder()
			.id(UUID.randomUUID().toString())
			.nodeId("planner")
			.nextNodeId(HUMAN_FEEDBACK_NODE)
			.state(Map.of("question", "analyse orders"))
			.build();

		checkpointSaver.put(runnableConfig, checkpoint);

		assertThat(checkpointSaver).isInstanceOf(MemorySaver.class);
		assertThat(compileConfig.interruptsBefore()).contains(HUMAN_FEEDBACK_NODE);
		assertThat(checkpointSaver.get(runnableConfig)).isPresent();
		checkpointSaver.release(runnableConfig);
		assertThat(checkpointSaver.get(runnableConfig)).isEmpty();
	}

}
