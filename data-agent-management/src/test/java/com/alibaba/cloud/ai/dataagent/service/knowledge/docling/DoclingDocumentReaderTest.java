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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import ai.docling.core.DoclingDocument;
import ai.docling.core.DoclingDocument.ContentLayer;
import ai.docling.core.DoclingDocument.GroupItem;
import ai.docling.core.DoclingDocument.GroupLabel;
import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import com.alibaba.cloud.ai.dataagent.service.knowledge.TextSplitterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoclingDocumentReaderTest {

	@Test
	void invokesServeMapsPdfAndPreservesSectionContextAfterSplitting() {
		DoclingProperties properties = new DoclingProperties();
		properties.setEnabled(true);
		DoclingServeApi client = mock(DoclingServeApi.class);
		DoclingDocumentMapper mapper = mock(DoclingDocumentMapper.class);
		TextSplitterFactory splitterFactory = mock(TextSplitterFactory.class);
		TextSplitter splitter = mock(TextSplitter.class);
		DoclingDocument doclingDocument = DoclingDocument.builder()
			.name("report")
			.version("1.0")
			.body(GroupItem.builder()
				.selfRef("#/body")
				.label(GroupLabel.UNSPECIFIED)
				.contentLayer(ContentLayer.BODY)
				.build())
			.build();
		InBodyConvertDocumentResponse response = InBodyConvertDocumentResponse.builder()
			.status("success")
			.document(DocumentResponse.builder().filename("report.pdf").jsonContent(doclingDocument).build())
			.build();
		Map<String, Object> metadata = Map.of(DocumentMetadataConstant.ELEMENT_TYPE, "text",
				DocumentMetadataConstant.SECTION_PATH, "财务分析", DocumentMetadataConstant.SOURCE_FILENAME, "report.pdf",
				DocumentMetadataConstant.SOURCE_ELEMENT_ID, "#/texts/0");
		Document mapped = new Document("mapped", "long text", metadata);
		when(mapper.supports("pdf")).thenReturn(true);
		when(mapper.map(doclingDocument, "report.pdf")).thenReturn(List.of(mapped));
		when(splitterFactory.getSplitter("token")).thenReturn(splitter);
		when(splitter.apply(any())).thenReturn(List.of(new Document("part one"), new Document("part two")));
		when(client.convertFilesAsync(any(ConvertDocumentRequest.class), any(Path[].class)))
			.thenReturn(CompletableFuture.completedFuture(response));
		DoclingDocumentReader reader = new DoclingDocumentReader(properties, client, List.of(mapper), splitterFactory);
		ByteArrayResource resource = new ByteArrayResource("pdf".getBytes()) {
			@Override
			public String getFilename() {
				return "report.pdf";
			}
		};

		List<Document> result = reader.read(resource, "report.pdf", "application/pdf", "token");

		assertThat(result).hasSize(2);
		assertThat(result).allSatisfy(chunk -> assertThat(chunk.getText()).startsWith("标题路径: 财务分析"));
		assertThat(result).extracting(Document::getId).doesNotHaveDuplicates();
	}

	@Test
	void isInactiveWhenDoclingIsDisabled() {
		DoclingProperties properties = new DoclingProperties();
		DoclingDocumentReader reader = new DoclingDocumentReader(properties, mock(DoclingServeApi.class), List.of(),
				mock(TextSplitterFactory.class));

		assertThat(reader.supports("report.pdf", "application/pdf")).isFalse();
		assertThat(reader.fallbackToTika()).isTrue();
	}

}
