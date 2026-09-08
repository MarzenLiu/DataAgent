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

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.review.KnowledgeParsePreviewVO;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.KnowledgeParseChunk;
import com.alibaba.cloud.ai.dataagent.entity.KnowledgeParseRevision;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeReviewStatus;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.enums.ParseRevisionStatus;
import com.alibaba.cloud.ai.dataagent.event.AgentKnowledgeEmbeddingEvent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.KnowledgeParseChunkMapper;
import com.alibaba.cloud.ai.dataagent.mapper.KnowledgeParseRevisionMapper;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentKnowledgeReviewServiceImpl implements AgentKnowledgeReviewService {

	private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
	};

	private static final TypeReference<List<String>> FLAGS_TYPE = new TypeReference<>() {
	};

	private final AgentKnowledgeMapper knowledgeMapper;

	private final KnowledgeParseRevisionMapper revisionMapper;

	private final KnowledgeParseChunkMapper chunkMapper;

	private final AgentKnowledgeResourceManager resourceManager;

	private final FileStorageService fileStorageService;

	private final ObjectMapper objectMapper;

	private final ApplicationEventPublisher eventPublisher;

	private final TransactionTemplate transactionTemplate;

	@Override
	public void parseForReview(Integer knowledgeId) {
		AgentKnowledge knowledge = requireDocumentKnowledge(knowledgeId);
		KnowledgeParseRevision revision = transactionTemplate.execute(status -> createParsingRevision(knowledge));
		if (revision == null) {
			throw new IllegalStateException("Unable to create parse revision");
		}

		try {
			List<Document> documents = resourceManager.parseDocument(knowledge);
			if (documents.isEmpty()) {
				throw new IllegalStateException("No documents extracted from file");
			}
			transactionTemplate.executeWithoutResult(status -> persistParsedDocuments(knowledge, revision, documents));
		}
		catch (RuntimeException ex) {
			log.error("Failed to create reviewable parse revision. knowledgeId={}", knowledgeId, ex);
			transactionTemplate.executeWithoutResult(status -> markParseFailed(knowledge, revision, ex));
		}
	}

	@Override
	public KnowledgeParsePreviewVO getLatestPreview(Integer knowledgeId) {
		AgentKnowledge knowledge = requireDocumentKnowledge(knowledgeId);
		KnowledgeParseRevision revision = requireLatestRevision(knowledgeId);
		List<KnowledgeParsePreviewVO.ChunkVO> chunks = chunkMapper.selectByRevisionId(revision.getId())
			.stream()
			.map(this::toPreviewChunk)
			.toList();
		return new KnowledgeParsePreviewVO(knowledgeId, knowledge.getTitle(), knowledge.getSourceFilename(),
				knowledge.getFileType(), revision.getId(), revision.getRevisionNo(), revision.getStatus(),
				revision.getParser(), revision.getParserVersion(), knowledge.getSplitterType(), revision.getChunkCount(),
				revision.getWarningCount(), revision.getReviewComment(), revision.getErrorMsg(), revision.getCreatedTime(), chunks);
	}

	@Override
	@Transactional
	public void approve(Integer knowledgeId, String comment) {
		AgentKnowledge knowledge = requireDocumentKnowledge(knowledgeId);
		KnowledgeParseRevision revision = requireLatestRevision(knowledgeId);
		int changed = revisionMapper.review(revision.getId(), ParseRevisionStatus.PENDING_REVIEW,
				ParseRevisionStatus.APPROVED, normalizeComment(comment), LocalDateTime.now());
		if (changed == 0) {
			throw new IllegalStateException("Only a pending parse revision can be approved");
		}
		knowledge.setReviewStatus(KnowledgeReviewStatus.APPROVED);
		knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING);
		knowledge.setErrorMsg("");
		knowledgeMapper.update(knowledge);
		eventPublisher.publishEvent(new AgentKnowledgeEmbeddingEvent(this, knowledgeId, knowledge.getSplitterType()));
	}

	@Override
	@Transactional
	public void reject(Integer knowledgeId, String comment) {
		AgentKnowledge knowledge = requireDocumentKnowledge(knowledgeId);
		KnowledgeParseRevision revision = requireLatestRevision(knowledgeId);
		int changed = revisionMapper.review(revision.getId(), ParseRevisionStatus.PENDING_REVIEW,
				ParseRevisionStatus.REJECTED, normalizeComment(comment), LocalDateTime.now());
		if (changed == 0) {
			throw new IllegalStateException("Only a pending parse revision can be rejected");
		}
		knowledge.setReviewStatus(KnowledgeReviewStatus.REJECTED);
		knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING);
		knowledge.setErrorMsg("");
		knowledgeMapper.update(knowledge);
	}

	@Override
	public List<Document> getApprovedDocuments(AgentKnowledge knowledge) {
		KnowledgeParseRevision revision = requireLatestRevision(knowledge.getId());
		if (revision.getStatus() != ParseRevisionStatus.APPROVED
				&& revision.getStatus() != ParseRevisionStatus.PUBLISHED) {
			throw new IllegalStateException("Document knowledge has no approved parse revision");
		}
		return chunkMapper.selectByRevisionId(revision.getId())
			.stream()
			.filter(chunk -> !Integer.valueOf(1).equals(chunk.getIsExcluded()))
			.map(this::toDocument)
			.toList();
	}

	@Override
	public void markPublished(Integer knowledgeId) {
		KnowledgeParseRevision revision = requireLatestRevision(knowledgeId);
		revisionMapper.updateResult(revision.getId(), ParseRevisionStatus.PUBLISHED, null, null, null,
				revision.getChunkCount(), revision.getWarningCount());
	}

	@Override
	public SourceDocument getSourceDocument(Integer knowledgeId) {
		AgentKnowledge knowledge = requireDocumentKnowledge(knowledgeId);
		Resource resource = fileStorageService.getFileResource(knowledge.getFilePath());
		String filename = StringUtils.hasText(knowledge.getSourceFilename()) ? knowledge.getSourceFilename()
				: "document";
		String mediaType = StringUtils.hasText(knowledge.getFileType()) ? knowledge.getFileType()
				: "application/octet-stream";
		return new SourceDocument(resource, filename, mediaType);
	}

	private KnowledgeParseRevision createParsingRevision(AgentKnowledge knowledge) {
		KnowledgeParseRevision revision = new KnowledgeParseRevision();
		revision.setKnowledgeId(knowledge.getId());
		revision.setRevisionNo(revisionMapper.nextRevisionNo(knowledge.getId()));
		revision.setStatus(ParseRevisionStatus.PARSING);
		revision.setParser("pending");
		revision.setParserVersion("unknown");
		revision.setChunkCount(0);
		revision.setWarningCount(0);
		revision.setCreatedTime(LocalDateTime.now());
		revision.setUpdatedTime(revision.getCreatedTime());
		revisionMapper.insert(revision);
		knowledge.setReviewStatus(KnowledgeReviewStatus.PARSING);
		knowledge.setEmbeddingStatus(EmbeddingStatus.PROCESSING);
		knowledge.setErrorMsg("");
		knowledgeMapper.update(knowledge);
		return revision;
	}

	private void persistParsedDocuments(AgentKnowledge knowledge, KnowledgeParseRevision revision,
			List<Document> documents) {
		Set<String> normalizedContents = new HashSet<>();
		int warningCount = 0;
		for (int index = 0; index < documents.size(); index++) {
			Document document = documents.get(index);
			List<String> flags = qualityFlags(document.getText(), normalizedContents);
			if (!flags.isEmpty()) {
				warningCount++;
			}
			chunkMapper.insert(toEntity(revision.getId(), index, document, flags));
		}
		Map<String, Object> metadata = documents.get(0).getMetadata();
		revision.setParser(stringValue(metadata.get(DocumentMetadataConstant.PARSER), "unknown"));
		revision.setParserVersion(stringValue(metadata.get(DocumentMetadataConstant.PARSER_VERSION), "unknown"));
		revisionMapper.updateResult(revision.getId(), ParseRevisionStatus.PENDING_REVIEW, revision.getParser(),
				revision.getParserVersion(), null, documents.size(), warningCount);
		knowledge.setReviewStatus(KnowledgeReviewStatus.PENDING_REVIEW);
		knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING);
		knowledge.setErrorMsg("");
		knowledgeMapper.update(knowledge);
	}

	private void markParseFailed(AgentKnowledge knowledge, KnowledgeParseRevision revision, RuntimeException ex) {
		String message = truncate(ex.getMessage(), 1000);
		revisionMapper.updateResult(revision.getId(), ParseRevisionStatus.FAILED, null, null, message, 0, 0);
		knowledge.setReviewStatus(KnowledgeReviewStatus.FAILED);
		knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
		knowledge.setErrorMsg(truncate(message, 250));
		knowledgeMapper.update(knowledge);
	}

	private KnowledgeParseChunk toEntity(Long revisionId, int index, Document document, List<String> flags) {
		Map<String, Object> metadata = document.getMetadata();
		KnowledgeParseChunk chunk = new KnowledgeParseChunk();
		chunk.setRevisionId(revisionId);
		chunk.setDocumentId(document.getId());
		chunk.setChunkIndex(index);
		chunk.setContent(document.getText());
		chunk.setContentType(stringValue(metadata.get(DocumentMetadataConstant.ELEMENT_TYPE), "text"));
		chunk.setPageNumber(integerValue(metadata.get(DocumentMetadataConstant.PAGE_NUMBER)));
		chunk.setBoundingBox(stringValue(metadata.get(DocumentMetadataConstant.BOUNDING_BOX), null));
		chunk.setSectionPath(stringValue(metadata.get(DocumentMetadataConstant.SECTION_PATH), null));
		chunk.setMetadataJson(writeJson(metadata));
		chunk.setQualityScore(Math.max(0, 100 - flags.size() * 20));
		chunk.setQualityFlagsJson(writeJson(flags));
		chunk.setIsExcluded(0);
		return chunk;
	}

	private List<String> qualityFlags(String content, Set<String> normalizedContents) {
		List<String> flags = new ArrayList<>();
		String normalized = content == null ? "" : content.strip();
		if (normalized.isEmpty()) {
			flags.add("EMPTY_CONTENT");
		}
		if (normalized.length() > 3000) {
			flags.add("TOO_LONG");
		}
		if (!normalized.isEmpty() && normalized.length() < 40) {
			flags.add("TOO_SHORT");
		}
		if (normalized.contains("�")) {
			flags.add("INVALID_CHARACTER");
		}
		String fingerprint = normalized.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		if (!fingerprint.isEmpty() && !normalizedContents.add(fingerprint)) {
			flags.add("DUPLICATE_CONTENT");
		}
		return flags;
	}

	private KnowledgeParsePreviewVO.ChunkVO toPreviewChunk(KnowledgeParseChunk chunk) {
		return new KnowledgeParsePreviewVO.ChunkVO(chunk.getId(), chunk.getDocumentId(), chunk.getChunkIndex(),
				chunk.getContent(), chunk.getContentType(), chunk.getPageNumber(), chunk.getBoundingBox(),
				chunk.getSectionPath(), chunk.getQualityScore(), readFlags(chunk.getQualityFlagsJson()),
				Integer.valueOf(1).equals(chunk.getIsExcluded()));
	}

	private Document toDocument(KnowledgeParseChunk chunk) {
		try {
			return new Document(chunk.getDocumentId(), chunk.getContent(),
					objectMapper.readValue(chunk.getMetadataJson(), METADATA_TYPE));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to deserialize parse chunk metadata", ex);
		}
	}

	private List<String> readFlags(String json) {
		if (!StringUtils.hasText(json)) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, FLAGS_TYPE);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to deserialize chunk quality flags", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize parse data", ex);
		}
	}

	private AgentKnowledge requireDocumentKnowledge(Integer knowledgeId) {
		AgentKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
		if (knowledge == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge not found");
		}
		if (knowledge.getType() != KnowledgeType.DOCUMENT) {
			throw new IllegalArgumentException("Parse review is only available for document knowledge");
		}
		return knowledge;
	}

	private KnowledgeParseRevision requireLatestRevision(Integer knowledgeId) {
		KnowledgeParseRevision revision = revisionMapper.selectLatest(knowledgeId);
		if (revision == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parse preview not found");
		}
		return revision;
	}

	private String normalizeComment(String comment) {
		return StringUtils.hasText(comment) ? comment.strip() : null;
	}

	private String stringValue(Object value, String fallback) {
		return value == null ? fallback : String.valueOf(value);
	}

	private Integer integerValue(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(String.valueOf(value));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private String truncate(String value, int maxLength) {
		String safe = StringUtils.hasText(value) ? value : "Unknown parsing error";
		return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
	}

}
