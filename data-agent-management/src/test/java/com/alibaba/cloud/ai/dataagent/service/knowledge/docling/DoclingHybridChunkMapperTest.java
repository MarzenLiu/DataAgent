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

import ai.docling.core.DoclingDocument;
import ai.docling.core.DoclingDocument.ContentLayer;
import ai.docling.core.DoclingDocument.GroupItem;
import ai.docling.core.DoclingDocument.GroupLabel;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.chunk.response.ExportDocumentResponse;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoclingHybridChunkMapperTest {

	@Test
	void mapsContextAndStructuredMetadata() {
		DoclingDocument source = DoclingDocument.builder()
			.name("report")
			.version("1.2")
			.body(GroupItem.builder()
				.selfRef("#/body")
				.label(GroupLabel.UNSPECIFIED)
				.contentLayer(ContentLayer.BODY)
				.build())
			.build();
		Chunk chunk = Chunk.builder()
			.chunkIndex(3)
			.text("Overview\nQuarterly revenue increased.")
			.numTokens(12)
			.heading("Overview")
			.heading("Revenue")
			.pageNumber(2)
			.build();
		ChunkDocumentResponse response = ChunkDocumentResponse.builder()
			.chunk(chunk)
			.document(ai.docling.serve.api.chunk.response.Document.builder()
				.status("success")
				.content(ExportDocumentResponse.builder().filename("report.pdf").jsonContent(source).build())
				.build())
			.build();

		Document result = new DoclingHybridChunkMapper().map(response, "report.pdf").get(0);

		assertThat(result.getText()).isEqualTo("Overview\nQuarterly revenue increased.");
		assertThat(result.getMetadata())
			.containsEntry(DocumentMetadataConstant.PARSER, "docling")
			.containsEntry(DocumentMetadataConstant.ELEMENT_TYPE, "text")
			.containsEntry(DocumentMetadataConstant.SECTION_PATH, "Overview > Revenue")
			.containsEntry(DocumentMetadataConstant.PAGE_NUMBER, 2)
			.containsEntry(DocumentMetadataConstant.CHUNK_INDEX, 3)
			.containsEntry("splitterType", "docling-hybrid")
			.containsEntry("numTokens", 12);
	}

}
