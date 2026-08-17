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
package com.alibaba.cloud.ai.dataagent.agentscope.controller;

import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.api.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.agentscope.service.AgentScopeSearchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class GraphController {

	private final AgentScopeSearchService searchService;

	public GraphController(AgentScopeSearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping(value = "/stream/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<GraphNodeResponse>> streamSearch(@RequestParam("agentId") String agentId,
			@RequestParam(value = "conversationId", required = false) String conversationId,
			@RequestParam(value = "threadId", required = false) String threadId, @RequestParam("query") String query,
			@RequestParam(value = "humanFeedback", required = false) boolean humanFeedback,
			@RequestParam(value = "humanFeedbackContent", required = false) String humanFeedbackContent,
			@RequestParam(value = "rejectedPlan", required = false) boolean rejectedPlan,
			@RequestParam(value = "nl2sqlOnly", required = false) boolean nl2sqlOnly, ServerHttpResponse response) {
		response.getHeaders().setCacheControl("no-cache");
		response.getHeaders().set("Connection", "keep-alive");
		response.getHeaders().setAccessControlAllowOrigin("*");
		return searchService.streamSearch(new GraphRequest(agentId, conversationId, threadId, query, humanFeedback,
				humanFeedbackContent, rejectedPlan, nl2sqlOnly));
	}

	@PostMapping("/stream/stop")
	public ResponseEntity<Void> stopStream(@RequestParam("conversationId") String conversationId,
			@RequestParam(value = "threadId", required = false) String threadId) {
		searchService.stop(conversationId, StringUtils.hasText(threadId) ? threadId : null);
		return ResponseEntity.noContent().build();
	}

}
