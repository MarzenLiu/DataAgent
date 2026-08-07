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

import com.alibaba.cloud.ai.dataagent.entity.ReportArtifact;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.report.ReportArtifactService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.CONVERSATION_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.RESULT;

/** Revises the latest report without rerunning schema, planning or data execution. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportRevisionNode implements NodeAction {

	private final LlmService llmService;

	private final ReportArtifactService reportArtifactService;

	@Override
	public Map<String, Object> apply(OverAllState state) {
		String conversationId = StateUtil.getStringValue(state, CONVERSATION_ID);
		Long agentId = Long.valueOf(StateUtil.getStringValue(state, AGENT_ID));
		String revisionRequest = StateUtil.getStringValue(state, INPUT_KEY);
		ReportArtifact previous = reportArtifactService.findLatest(conversationId, agentId)
			.orElseThrow(() -> new IllegalStateException("No previous report is available for revision"));

		String systemPrompt = """
				You revise an existing Markdown data-analysis report.
				Follow the user's presentation and wording instructions while preserving all facts,
				numbers, filters, dates and conclusions from the existing report. Do not invent or
				recalculate data. Return only the complete revised Markdown report.
				""";
		String userPrompt = """
				## Existing report
				%s

				## Revision request
				%s
				""".formatted(previous.getContent(), revisionRequest);
		Flux<ChatResponse> revisionFlux = llmService.callObserved("report-revision.compose", systemPrompt, userPrompt);
		Flux<ChatResponse> preFlux = Flux.just(ChatResponseUtil.createResponse("开始修改报告..."),
				ChatResponseUtil.createPureResponse(TextType.MARK_DOWN.getStartSign()));
		Flux<ChatResponse> suffixFlux = Flux.just(ChatResponseUtil.createPureResponse(TextType.MARK_DOWN.getEndSign()),
				ChatResponseUtil.createResponse("报告修改完成！"));

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(getClass(), state, revisionFlux,
				preFlux, suffixFlux, revisedReport -> {
					saveRevision(conversationId, agentId, revisionRequest, revisedReport);
					return Map.of(RESULT, revisedReport);
				});
		return Map.of(RESULT, generator);
	}

	private void saveRevision(String conversationId, Long agentId, String sourceQuery, String reportContent) {
		try {
			reportArtifactService.save(conversationId, agentId, sourceQuery, reportContent);
		}
		catch (RuntimeException ex) {
			log.warn("Unable to persist revised report for conversation {}", conversationId, ex);
		}
	}

}
