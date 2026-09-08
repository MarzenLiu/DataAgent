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
package com.alibaba.cloud.ai.dataagent.service.knowledge.docling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.docling.core.DoclingDocument;
import ai.docling.core.DoclingDocument.BaseTextItem;
import ai.docling.core.DoclingDocument.PictureItem;
import ai.docling.core.DoclingDocument.TableItem;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DoclingHybridChunkMapper {

	private static final String SPLITTER_TYPE = "splitterType";

	private static final String NUM_TOKENS = "numTokens";

	private static final String CAPTIONS = "captions";

	public List<Document> map(ChunkDocumentResponse response, String sourceFilename) {
		DoclingDocument source = convertedDocument(response);
		List<Document> documents = new ArrayList<>();
		for (Chunk chunk : response.getChunks()) {
			if (!StringUtils.hasText(chunk.getText())) {
				continue;
			}
			List<String> itemReferences = chunk.getDocItems() == null ? List.of() : chunk.getDocItems();
			String sourceElementId = itemReferences.isEmpty() ? "hybrid-chunk-" + chunk.getChunkIndex()
					: String.join(",", itemReferences);
			Map<String, Object> metadata = chunk.getMetadata() == null ? new HashMap<>()
					: new HashMap<>(chunk.getMetadata());
			metadata.putAll(DoclingMappingSupport.baseMetadata(source, sourceFilename,
					elementType(source, itemReferences), sourceElementId));
			metadata.put(DocumentMetadataConstant.CHUNK_INDEX, chunk.getChunkIndex());
			metadata.put(SPLITTER_TYPE, "docling-hybrid");
			if (chunk.getNumTokens() != null) {
				metadata.put(NUM_TOKENS, chunk.getNumTokens());
			}
			if (chunk.getHeadings() != null && !chunk.getHeadings().isEmpty()) {
				metadata.put(DocumentMetadataConstant.SECTION_PATH, String.join(" > ", chunk.getHeadings()));
			}
			if (chunk.getCaptions() != null && !chunk.getCaptions().isEmpty()) {
				metadata.put(CAPTIONS, List.copyOf(chunk.getCaptions()));
			}
			addLocation(metadata, source, itemReferences, chunk.getPageNumbers());
			documents.add(new Document(
					DoclingMappingSupport.deterministicId(sourceFilename, sourceElementId, chunk.getChunkIndex()),
					chunk.getText(), metadata));
		}
		return documents;
	}

	private DoclingDocument convertedDocument(ChunkDocumentResponse response) {
		if (response == null || response.getDocuments() == null) {
			throw new IllegalStateException("Docling hybrid response did not contain converted documents");
		}
		return response.getDocuments()
			.stream()
			.filter(document -> document.getContent() != null && document.getContent().getJsonContent() != null)
			.map(document -> document.getContent().getJsonContent())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Docling hybrid response did not contain json_content"));
	}

	private String elementType(DoclingDocument source, List<String> references) {
		for (String reference : references) {
			Object item = DoclingMappingSupport.resolveItem(source, reference);
			if (item instanceof TableItem) {
				return "table";
			}
			if (item instanceof PictureItem) {
				return "picture";
			}
		}
		return "text";
	}

	private void addLocation(Map<String, Object> metadata, DoclingDocument source, List<String> references,
			List<Integer> pageNumbers) {
		for (String reference : references) {
			Object item = DoclingMappingSupport.resolveItem(source, reference);
			if (item instanceof BaseTextItem text) {
				DoclingMappingSupport.addProvenance(metadata, source, text.getProv());
				return;
			}
			if (item instanceof TableItem table) {
				DoclingMappingSupport.addProvenance(metadata, source, table.getProv());
				return;
			}
			if (item instanceof PictureItem picture) {
				DoclingMappingSupport.addProvenance(metadata, source, picture.getProv());
				return;
			}
		}
		if (pageNumbers != null && !pageNumbers.isEmpty()) {
			metadata.put(DocumentMetadataConstant.PAGE_NUMBER, pageNumbers.get(0));
		}
	}

}
