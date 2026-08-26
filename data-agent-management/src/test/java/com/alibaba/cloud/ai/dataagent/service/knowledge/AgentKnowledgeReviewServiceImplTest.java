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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.KnowledgeParseRevision;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.enums.ParseRevisionStatus;
import com.alibaba.cloud.ai.dataagent.event.AgentKnowledgeEmbeddingEvent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.KnowledgeParseChunkMapper;
import com.alibaba.cloud.ai.dataagent.mapper.KnowledgeParseRevisionMapper;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeReviewServiceImplTest {

	@Mock
	private AgentKnowledgeMapper knowledgeMapper;

	@Mock
	private KnowledgeParseRevisionMapper revisionMapper;

	@Mock
	private KnowledgeParseChunkMapper chunkMapper;

	@Mock
	private AgentKnowledgeResourceManager resourceManager;

	@Mock
	private FileStorageService fileStorageService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private AgentKnowledgeReviewServiceImpl service;

	private AgentKnowledge knowledge;

	@BeforeEach
	void setUp() {
		service = new AgentKnowledgeReviewServiceImpl(knowledgeMapper, revisionMapper, chunkMapper, resourceManager,
				fileStorageService, new ObjectMapper(), eventPublisher,
				new TransactionTemplate(new NoOpTransactionManager()));
		knowledge = new AgentKnowledge();
		knowledge.setId(7);
		knowledge.setAgentId(1);
		knowledge.setType(KnowledgeType.DOCUMENT);
		knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING);
		knowledge.setFilePath("uploads/report.pdf");
		knowledge.setSourceFilename("report.pdf");
		when(knowledgeMapper.selectById(7)).thenReturn(knowledge);
	}

	@Test
	void parsesAndPersistsReviewableChunksWithoutPublishingVectors() {
		when(revisionMapper.nextRevisionNo(7)).thenReturn(1);
		when(revisionMapper.insert(any())).thenAnswer(invocation -> {
			KnowledgeParseRevision revision = invocation.getArgument(0);
			revision.setId(11L);
			return 1;
		});
		Map<String, Object> metadata = Map.of(DocumentMetadataConstant.PARSER, "docling",
				DocumentMetadataConstant.PARSER_VERSION, "2.0", DocumentMetadataConstant.PAGE_NUMBER, 1,
				DocumentMetadataConstant.ELEMENT_TYPE, "text");
		when(resourceManager.parseDocument(knowledge))
			.thenReturn(List.of(new Document("a", "A sufficiently long first parsed paragraph for review.", metadata),
					new Document("b", "Another sufficiently long parsed paragraph for review.", metadata)));

		service.parseForReview(7);

		verify(chunkMapper, times(2)).insert(any());
		verify(revisionMapper).updateResult(11L, ParseRevisionStatus.PENDING_REVIEW, "docling", "2.0", null, 2, 0);
		verify(knowledgeMapper, times(2)).update(knowledge);
		verify(eventPublisher, times(0)).publishEvent(any(AgentKnowledgeEmbeddingEvent.class));
	}

	@Test
	void approvalPublishesEmbeddingEventAfterStateTransition() {
		KnowledgeParseRevision revision = new KnowledgeParseRevision();
		revision.setId(11L);
		revision.setStatus(ParseRevisionStatus.PENDING_REVIEW);
		when(revisionMapper.selectLatest(7)).thenReturn(revision);
		when(revisionMapper.review(eq(11L), eq(ParseRevisionStatus.PENDING_REVIEW),
				eq(ParseRevisionStatus.APPROVED), eq("looks good"), any())).thenReturn(1);

		service.approve(7, "looks good");

		assertThat(knowledge.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.PENDING);
		verify(knowledgeMapper).update(knowledge);
		verify(eventPublisher).publishEvent(any(AgentKnowledgeEmbeddingEvent.class));
	}

	private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
		}

	}

}
