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
package com.alibaba.cloud.ai.dataagent.dto.knowledge.review;

import com.alibaba.cloud.ai.dataagent.enums.ParseRevisionStatus;
import java.time.LocalDateTime;
import java.util.List;

/** Read model used by the three-pane document review page. */
public record KnowledgeParsePreviewVO(Integer knowledgeId, String title, String sourceFilename, String fileType,
		Long revisionId, Integer revisionNo, ParseRevisionStatus status, String parser, String parserVersion,
		String splitterType, Integer chunkCount, Integer warningCount, String reviewComment, String errorMsg,
		LocalDateTime createdTime, List<ChunkVO> chunks) {

	public record ChunkVO(Long id, String documentId, Integer chunkIndex, String content, String contentType,
			Integer pageNumber, String boundingBox, String sectionPath, Integer qualityScore, List<String> qualityFlags,
			boolean excluded) {
	}

}
