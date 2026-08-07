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
package com.alibaba.cloud.ai.dataagent.service.report;

import com.alibaba.cloud.ai.dataagent.entity.ReportArtifact;
import com.alibaba.cloud.ai.dataagent.mapper.ReportArtifactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportArtifactService {

	private final ReportArtifactMapper reportArtifactMapper;

	public Optional<ReportArtifact> findLatest(String conversationId, Long agentId) {
		if (!StringUtils.hasText(conversationId) || agentId == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(reportArtifactMapper.selectLatest(conversationId, agentId));
	}

	public ReportArtifact save(String conversationId, Long agentId, String sourceQuery, String content) {
		if (!StringUtils.hasText(conversationId) || agentId == null || !StringUtils.hasText(content)) {
			throw new IllegalArgumentException("conversationId, agentId and report content are required");
		}
		ReportArtifact artifact = ReportArtifact.builder()
			.conversationId(conversationId)
			.agentId(agentId)
			.sourceQuery(sourceQuery)
			.content(content)
			.build();
		reportArtifactMapper.insert(artifact);
		return artifact;
	}

}
