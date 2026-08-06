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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

@Slf4j
@Component
@AllArgsConstructor
public class BusinessDataDiscoveryNode implements NodeAction {

	private static final int DISCOVERY_MAX_TOKENS = 8000;

	private final LlmService llmService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		List<Document> tableDocuments = state.value(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, List.of());
		List<Document> columnDocuments = state.value(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, List.of());

		Flux<ChatResponse> responseFlux;
		if (tableDocuments == null || tableDocuments.isEmpty()) {
			responseFlux = Flux.just(ChatResponseUtil.createPureResponse(
					"当前没有可用于能力发现的 Schema 信息。请先为该智能体配置、激活并初始化数据源。"));
		}
		else {
			String prompt = buildPrompt(tableDocuments, columnDocuments);
			responseFlux = llmService.callUserObservedWithMaxTokens("business-data-discovery.summarize", prompt,
					DISCOVERY_MAX_TOKENS);
		}
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "正在整理可查询的业务数据...", "业务数据能力整理完成。", output -> Map.of(
						BUSINESS_DATA_DISCOVERY_NODE_OUTPUT, output.trim(), FINAL_ANSWER, output.trim()), responseFlux);
		return Map.of(BUSINESS_DATA_DISCOVERY_NODE_OUTPUT, generator);
	}

	String buildPrompt(List<Document> tableDocuments, List<Document> columnDocuments) throws Exception {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("tables", serializeDocuments(tableDocuments));
		schema.put("columns", serializeDocuments(columnDocuments));
		String schemaJson = JsonUtil.getObjectMapper().writeValueAsString(schema);
		return """
				你是数据产品顾问。请根据下面真实的数据库 Schema，向首次使用系统的业务用户说明可以查询哪些业务数据。

				输出要求：
				1. 按业务主题分组，不要只罗列表名和字段名。
				2. 每个主题说明可查询的核心指标、分析维度和典型问题示例。
				3. 指标只能由现有字段直接查询或通过明确的聚合计算得到，禁止虚构业务口径。
				4. 对需要业务口径确认的指标明确标注“需确认口径”。
				5. 使用清晰的中文 Markdown，避免暴露无意义的技术字段。
				6. 最后邀请用户选择一个示例问题继续查询。
				7. 直接给出结论，不展示分析或推理过程；全文控制在 1500 个中文字符以内。

				数据库 Schema：
				%s
				""".formatted(schemaJson);
	}

	private List<Map<String, Object>> serializeDocuments(List<Document> documents) {
		if (documents == null) {
			return List.of();
		}
		return documents.stream().map(document -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("content", document.getText());
			item.put("metadata", document.getMetadata());
			return item;
		}).toList();
	}

}
