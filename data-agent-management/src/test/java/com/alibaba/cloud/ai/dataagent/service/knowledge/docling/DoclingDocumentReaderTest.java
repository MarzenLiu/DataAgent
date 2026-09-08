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
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.InputFormat;
import ai.docling.serve.api.convert.request.options.PdfBackend;
import ai.docling.serve.api.convert.request.options.TableFormerMode;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import com.alibaba.cloud.ai.dataagent.service.knowledge.TextSplitterFactory;
import org.junit.jupiter.api.Test;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import com.alibaba.cloud.ai.dataagent.splitter.TextSplitter;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class DoclingDocumentReaderTest {

	@Test
	void invokesServeMapsPdfAndPreservesSectionContextAfterSplitting() {
		DoclingProperties properties = new DoclingProperties();
		properties.setEnabled(true);
		DoclingServeApi client = mock(DoclingServeApi.class);
		DoclingDocumentMapper mapper = mock(DoclingDocumentMapper.class);
		TextSplitterFactory splitterFactory = mock(TextSplitterFactory.class);
		DoclingHybridChunkMapper hybridChunkMapper = mock(DoclingHybridChunkMapper.class);
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
		DoclingDocumentReader reader = new DoclingDocumentReader(properties, client, List.of(mapper), splitterFactory,
				hybridChunkMapper);
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
		ArgumentCaptor<ConvertDocumentRequest> request = ArgumentCaptor.forClass(ConvertDocumentRequest.class);
		verify(client).convertFilesAsync(request.capture(), any(Path[].class));
		assertThat(request.getValue().getOptions())
			.returns(PdfBackend.PYPDFIUM2, options -> options.getPdfBackend())
			.returns(false, options -> options.getIncludeImages())
			.returns(true, options -> options.getDoTableStructure())
			.returns(TableFormerMode.FAST, options -> options.getTableMode());
	}

	@Test
	void usesDoclingHybridChunkerForPdfWhenSelected() {
		DoclingProperties properties = new DoclingProperties();
		properties.setEnabled(true);
		DoclingServeApi client = mock(DoclingServeApi.class);
		DoclingDocumentMapper mapper = mock(DoclingDocumentMapper.class);
		DoclingHybridChunkMapper hybridChunkMapper = mock(DoclingHybridChunkMapper.class);
		ChunkDocumentResponse response = ChunkDocumentResponse.builder().build();
		Document hybridChunk = new Document("hybrid", "contextualized chunk", Map.of());
		when(mapper.supports("pdf")).thenReturn(true);
		when(client.chunkFilesWithHybridChunkerAsync(any(HybridChunkDocumentRequest.class), any(Path[].class)))
			.thenReturn(CompletableFuture.completedFuture(response));
		when(hybridChunkMapper.map(response, "report.pdf")).thenReturn(List.of(hybridChunk));
		DoclingDocumentReader reader = new DoclingDocumentReader(properties, client, List.of(mapper),
				mock(TextSplitterFactory.class), hybridChunkMapper);
		ByteArrayResource resource = new ByteArrayResource("pdf".getBytes()) {
			@Override
			public String getFilename() {
				return "report.pdf";
			}
		};

		List<Document> result = reader.read(resource, "report.pdf", "application/pdf", "docling-hybrid");

		assertThat(result).containsExactly(hybridChunk);
	}

	@Test
	void fallsBackToLocalTokenSplittingWhenHybridChunkingFails() {
		DoclingProperties properties = new DoclingProperties();
		properties.setEnabled(true);
		DoclingServeApi client = mock(DoclingServeApi.class);
		DoclingDocumentMapper mapper = mock(DoclingDocumentMapper.class);
		TextSplitterFactory splitterFactory = mock(TextSplitterFactory.class);
		TextSplitter splitter = mock(TextSplitter.class);
		DoclingDocument source = DoclingDocument.builder()
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
			.document(DocumentResponse.builder().filename("report.pdf").jsonContent(source).build())
			.build();
		Document mapped = new Document("mapped", "fallback text",
				Map.of(DocumentMetadataConstant.ELEMENT_TYPE, "text", DocumentMetadataConstant.SOURCE_FILENAME, "report.pdf",
						DocumentMetadataConstant.SOURCE_ELEMENT_ID, "#/texts/0"));
		when(mapper.supports("pdf")).thenReturn(true);
		when(client.chunkFilesWithHybridChunkerAsync(any(HybridChunkDocumentRequest.class), any(Path[].class)))
			.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("tokenizer unavailable")));
		when(client.convertFilesAsync(any(ConvertDocumentRequest.class), any(Path[].class)))
			.thenReturn(CompletableFuture.completedFuture(response));
		when(mapper.map(source, "report.pdf")).thenReturn(List.of(mapped));
		when(splitterFactory.getSplitter("token")).thenReturn(splitter);
		when(splitter.apply(any())).thenReturn(List.of(new Document("fallback chunk")));
		DoclingDocumentReader reader = new DoclingDocumentReader(properties, client, List.of(mapper), splitterFactory,
				mock(DoclingHybridChunkMapper.class));

		List<Document> result = reader.read(new ByteArrayResource("pdf".getBytes()), "report.pdf", "application/pdf",
				"docling-hybrid");

		assertThat(result).singleElement().satisfies(chunk -> assertThat(chunk.getText()).isEqualTo("fallback chunk"));
	}

	@Test
	void isInactiveWhenDoclingIsDisabled() {
		DoclingProperties properties = new DoclingProperties();
		DoclingDocumentReader reader = new DoclingDocumentReader(properties, mock(DoclingServeApi.class), List.of(),
				mock(TextSplitterFactory.class), mock(DoclingHybridChunkMapper.class));

		assertThat(reader.supports("report.pdf", "application/pdf")).isFalse();
		assertThat(reader.fallbackToTika()).isTrue();
	}

	@Test
	void invokesServeWithDocxInputFormat() {
		DoclingProperties properties = new DoclingProperties();
		properties.setEnabled(true);
		DoclingServeApi client = mock(DoclingServeApi.class);
		DoclingDocumentMapper mapper = mock(DoclingDocumentMapper.class);
		TextSplitterFactory splitterFactory = mock(TextSplitterFactory.class);
		TextSplitter splitter = mock(TextSplitter.class);
		DoclingDocument doclingDocument = DoclingDocument.builder()
			.name("水工规范")
			.version("1.0")
			.body(GroupItem.builder()
				.selfRef("#/body")
				.label(GroupLabel.UNSPECIFIED)
				.contentLayer(ContentLayer.BODY)
				.build())
			.build();
		InBodyConvertDocumentResponse response = InBodyConvertDocumentResponse.builder()
			.status("success")
			.document(DocumentResponse.builder().filename("水工规范.docx").jsonContent(doclingDocument).build())
			.build();
		Document mapped = new Document("mapped", "水工建筑物设计条文",
				Map.of(DocumentMetadataConstant.ELEMENT_TYPE, "text"));
		when(mapper.supports("docx")).thenReturn(true);
		when(mapper.map(doclingDocument, "水工规范.docx")).thenReturn(List.of(mapped));
		when(splitterFactory.getSplitter("token")).thenReturn(splitter);
		when(splitter.apply(any())).thenReturn(List.of(mapped));
		when(client.convertFilesAsync(any(ConvertDocumentRequest.class), any(Path[].class)))
			.thenReturn(CompletableFuture.completedFuture(response));
		DoclingDocumentReader reader = new DoclingDocumentReader(properties, client, List.of(mapper), splitterFactory,
				mock(DoclingHybridChunkMapper.class));

		assertThat(reader.supports(null,
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
		List<Document> result = reader.read(new ByteArrayResource("docx".getBytes()), "水工规范.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "token");

		assertThat(result).hasSize(1);
		ArgumentCaptor<ConvertDocumentRequest> request = ArgumentCaptor.forClass(ConvertDocumentRequest.class);
		verify(client).convertFilesAsync(request.capture(), any(Path[].class));
		assertThat(request.getValue().getOptions().getFromFormats()).containsExactly(InputFormat.DOCX);
		assertThat(request.getValue().getOptions().getDoOcr()).isFalse();
	}

}
