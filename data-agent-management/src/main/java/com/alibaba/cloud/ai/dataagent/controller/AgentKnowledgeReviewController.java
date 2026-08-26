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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.knowledge.review.KnowledgeParsePreviewVO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.review.KnowledgeReviewRequest;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeReviewService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for document parse preview and review decisions. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent-knowledge/{knowledgeId}/review")
public class AgentKnowledgeReviewController {

	private final AgentKnowledgeReviewService reviewService;

	@GetMapping
	public ApiResponse<KnowledgeParsePreviewVO> preview(@PathVariable Integer knowledgeId) {
		return ApiResponse.success("查询成功", reviewService.getLatestPreview(knowledgeId));
	}

	@GetMapping("/source")
	public ResponseEntity<Resource> source(@PathVariable Integer knowledgeId) {
		AgentKnowledgeReviewService.SourceDocument source = reviewService.getSourceDocument(knowledgeId);
		MediaType mediaType;
		try {
			mediaType = MediaType.parseMediaType(source.mediaType());
		}
		catch (IllegalArgumentException ex) {
			mediaType = MediaType.APPLICATION_OCTET_STREAM;
		}
		return ResponseEntity.ok()
			.contentType(mediaType)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(source.filename(), StandardCharsets.UTF_8).build().toString())
			.body(source.resource());
	}

	@PostMapping("/approve")
	public ApiResponse<Void> approve(@PathVariable Integer knowledgeId,
			@Valid @RequestBody(required = false) KnowledgeReviewRequest request) {
		reviewService.approve(knowledgeId, request == null ? null : request.comment());
		return ApiResponse.success("审核通过，后台正在生成向量。");
	}

	@PostMapping("/reject")
	public ApiResponse<Void> reject(@PathVariable Integer knowledgeId,
			@Valid @RequestBody(required = false) KnowledgeReviewRequest request) {
		reviewService.reject(knowledgeId, request == null ? null : request.comment());
		return ApiResponse.success("已驳回当前解析结果。");
	}

}
