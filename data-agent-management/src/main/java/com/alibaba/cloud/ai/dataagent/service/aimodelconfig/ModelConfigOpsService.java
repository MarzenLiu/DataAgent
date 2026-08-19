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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.converter.ModelConfigConverter;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.rag.OpenAiCompatibleEmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists model configuration; AgentScope reloads active chat settings by fingerprint. */
@Service
@RequiredArgsConstructor
public class ModelConfigOpsService {

	private final ModelConfigDataService modelConfigDataService;
	private final AgentScopeModelClient agentScopeModelClient;
	private final OpenAiCompatibleEmbeddingClient embeddingClient;
	private final DataAgentProperties properties;

	@Transactional(rollbackFor = Exception.class)
	public void updateAndRefresh(ModelConfigDTO dto) {
		if (Boolean.TRUE.equals(dto.getIsActive()) && ModelType.EMBEDDING.getCode().equalsIgnoreCase(dto.getModelType())) {
			validateEmbedding(dto);
		}
		modelConfigDataService.updateConfigInDb(dto);
	}

	@Transactional(rollbackFor = Exception.class)
	public void activateConfig(Integer id) {
		ModelConfig entity = modelConfigDataService.findById(id);
		if (entity == null) {
			throw new IllegalArgumentException("配置不存在");
		}
		if (ModelType.EMBEDDING.equals(entity.getModelType())) {
			validateEmbedding(ModelConfigConverter.toDTO(entity));
		}
		modelConfigDataService.switchActiveStatus(id, entity.getModelType());
	}

	public void testConnection(Integer id) {
		ModelConfig entity = modelConfigDataService.findById(id);
		if (entity == null) {
			throw new IllegalArgumentException("配置不存在");
		}
		ModelConfigDTO config = ModelConfigConverter.toDTO(entity);
		if (ModelType.CHAT.getCode().equalsIgnoreCase(config.getModelType())) {
			agentScopeModelClient.testChatModel(config);
			return;
		}
		if (ModelType.EMBEDDING.getCode().equalsIgnoreCase(config.getModelType())) {
			validateEmbedding(config);
			return;
		}
		throw new IllegalArgumentException("未知的模型类型: " + config.getModelType());
	}

	private void validateEmbedding(ModelConfigDTO config) {
		double[] vector = embeddingClient.embed(config, "Test");
		int expected = properties.getVectorStore().getEmbeddingDimension();
		if (expected > 0 && vector.length != expected) {
			throw new IllegalStateException("向量维度不匹配: expected=" + expected + ", actual=" + vector.length);
		}
	}

}
