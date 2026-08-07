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
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.report.ReportArtifactService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportRevisionNodeTest {

	@Mock
	private LlmService llmService;

	@Mock
	private ReportArtifactService reportArtifactService;

	@Test
	void revisesAndPersistsLatestReportWithoutOtherWorkflowState() throws Exception {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(CONVERSATION_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(RESULT, new ReplaceStrategy());
		state.updateState(Map.of(CONVERSATION_ID, "conversation-1", AGENT_ID, "7", INPUT_KEY, "精简报告"));
		when(reportArtifactService.findLatest("conversation-1", 7L))
			.thenReturn(Optional.of(ReportArtifact.builder().content("# 原报告\n销售额100元").build()));
		when(llmService.callObserved(eq("report-revision.compose"), anyString(), contains("精简报告")))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("# 精简报告\n销售额100元")));

		Map<String, Object> result = new ReportRevisionNode(llmService, reportArtifactService).apply(state);
		assertTrue(result.get(RESULT) instanceof Flux<?>);
		((Flux<?>) result.get(RESULT)).blockLast();

		verify(reportArtifactService).save("conversation-1", 7L, "精简报告", "# 精简报告\n销售额100元");
	}

}
