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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ai.docling.serve.api.DoclingServeApi;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeResourceManager;
import com.alibaba.cloud.ai.dataagent.service.knowledge.TextSplitterFactory;
import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.MetadataAwareSimpleVectorStore;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DOCLING_E2E", matches = "true")
class DoclingRealFilesE2ETest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final int AGENT_ID = 99;

	@Test
	void parsesChunksAndStoresRealPdfAndExcelDocuments() throws Exception {
		Path pdf = requiredPath("DOCLING_E2E_PDF");
		Path xlsx = requiredPath("DOCLING_E2E_XLSX");
		DoclingProperties properties = doclingProperties();
		DoclingServeApi client = client(properties);
		TextSplitter splitter = new TokenTextSplitter(1000, 400, 10, 5000, true);
		TextSplitterFactory splitterFactory = new TextSplitterFactory(Map.of("token", splitter));
		DoclingDocumentReader reader = new DoclingDocumentReader(properties, client,
				List.of(new PdfDoclingDocumentMapper(properties), new ExcelDoclingDocumentMapper(properties)), splitterFactory);

		MetadataAwareSimpleVectorStore vectorStore = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		DataAgentProperties dataAgentProperties = new DataAgentProperties();
		AgentVectorStoreService vectorService = new AgentVectorStoreServiceImpl(vectorStore, Optional.empty(),
				dataAgentProperties, new DynamicFilterService(null, null),
				new MetadataDocumentRetriever(new StandardEnvironment()));
		FileStorageService fileStorage = new LocalFileStorageService(Map.of("pdf", pdf, "xlsx", xlsx));
		AgentKnowledgeResourceManager resourceManager = new AgentKnowledgeResourceManager(splitterFactory, fileStorage,
				vectorService, reader);

		runCase("pdf", pdf, 9001, reader, resourceManager, vectorService, "polar bear");
		runCase("xlsx", xlsx, 9002, reader, resourceManager, vectorService, "order");
	}

	private void runCase(String extension, Path source, int knowledgeId, DoclingDocumentReader reader,
			AgentKnowledgeResourceManager resourceManager, AgentVectorStoreService vectorService, String query) throws Exception {
		String filename = source.getFileName().toString();
		List<Document> parsed = reader.read(new FileSystemResource(source), filename, extension, "token");
		assertThat(parsed).as("Docling chunks for %s", filename).isNotEmpty();
		assertThat(parsed).allSatisfy(document -> {
			assertThat(document.getText()).isNotBlank();
			assertThat(document.getMetadata()).containsEntry(DocumentMetadataConstant.PARSER, "docling")
				.containsEntry(DocumentMetadataConstant.SOURCE_FILENAME, filename)
				.containsKeys(DocumentMetadataConstant.ELEMENT_TYPE, DocumentMetadataConstant.CHUNK_INDEX);
		});

		System.out.printf("E2E|SOURCE|type=%s|file=%s|bytes=%d|parsedChunks=%d%n", extension, filename,
				Files.size(source), parsed.size());
		for (int index = 0; index < parsed.size(); index++) {
			Document chunk = parsed.get(index);
			System.out.printf("E2E|CHUNK|type=%s|position=%d|id=%s|chars=%d|metadata=%s|text=%s%n", extension,
					index, chunk.getId(), chunk.getText().length(), JSON.writeValueAsString(chunk.getMetadata()),
					oneLine(chunk.getText()));
		}

		AgentKnowledge knowledge = knowledge(extension, source, knowledgeId);
		resourceManager.doEmbedingToVectorStore(knowledge);
		Filter.Expression filter = new FilterExpressionBuilder()
			.eq(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledgeId)
			.build();
		List<Document> stored = vectorService.getDocumentsOnlyByFilter(filter, 100);
		assertThat(stored).hasSameSizeAs(parsed).allSatisfy(document -> {
			assertThat(document.getMetadata()).containsEntry(Constant.AGENT_ID, Integer.toString(AGENT_ID))
				.containsEntry(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledgeId)
				.containsEntry(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE)
				.containsEntry(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, KnowledgeType.DOCUMENT.getCode());
		});

		List<Document> recalled = vectorService.similaritySearch(query, filter, 5, 0.8);
		assertThat(recalled).as("vector recall for %s", filename).isNotEmpty();
		Path evidence = Path.of("target", "docling-e2e-" + extension + ".json");
		JSON.writerWithDefaultPrettyPrinter()
			.writeValue(evidence.toFile(),
					Map.of("source", source.toString(), "bytes", Files.size(source), "parsed", evidence(parsed), "stored",
							evidence(stored), "query", query, "recalled", evidence(recalled)));
		System.out.printf("E2E|VECTOR|type=%s|stored=%d|query=%s|recalled=%d|topId=%s|topText=%s%n", extension,
				stored.size(), query, recalled.size(), recalled.get(0).getId(), oneLine(recalled.get(0).getText()));
	}

	private List<Map<String, Object>> evidence(List<Document> documents) {
		return documents.stream()
			.map(document -> Map.<String, Object>of("id", document.getId(), "text", document.getText(), "metadata",
					document.getMetadata()))
			.toList();
	}

	private DoclingProperties doclingProperties() {
		DoclingProperties properties = new DoclingProperties();
		properties.setEnabled(true);
		properties.setBaseUrl(System.getenv().getOrDefault("DOCLING_E2E_URL", "http://localhost:5001"));
		properties.setApiKey(System.getenv().getOrDefault("DOCLING_E2E_API_KEY", "data-agent-docling"));
		properties.setConnectTimeout(Duration.ofSeconds(10));
		properties.setReadTimeout(Duration.ofMinutes(10));
		properties.setAsyncPollInterval(Duration.ofSeconds(1));
		properties.setAsyncTimeout(Duration.ofMinutes(10));
		properties.setFallbackToTika(false);
		properties.getExcel().setMaxTableRowsPerChunk(2);
		return properties;
	}

	private DoclingServeApi client(DoclingProperties properties) {
		return DoclingServeApi.builder()
			.baseUrl(properties.getBaseUrl())
			.apiKey(properties.getApiKey())
			.connectTimeout(properties.getConnectTimeout())
			.readTimeout(properties.getReadTimeout())
			.asyncPollInterval(properties.getAsyncPollInterval())
			.asyncTimeout(properties.getAsyncTimeout())
			.build();
	}

	private AgentKnowledge knowledge(String extension, Path source, int id) throws Exception {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(id);
		knowledge.setAgentId(AGENT_ID);
		knowledge.setTitle("Docling E2E " + extension);
		knowledge.setType(KnowledgeType.DOCUMENT);
		knowledge.setSourceFilename(source.getFileName().toString());
		knowledge.setFilePath(extension);
		knowledge.setFileSize(Files.size(source));
		knowledge.setFileType(extension);
		knowledge.setSplitterType("token");
		return knowledge;
	}

	private static Path requiredPath(String environmentVariable) {
		String value = System.getenv(environmentVariable);
		assertThat(value).as("environment variable %s", environmentVariable).isNotBlank();
		Path path = Path.of(value).toAbsolutePath().normalize();
		assertThat(path).isRegularFile();
		return path;
	}

	private static String oneLine(String text) {
		String normalized = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").strip();
		return normalized.length() <= 280 ? normalized : normalized.substring(0, 277) + "...";
	}

	private record LocalFileStorageService(Map<String, Path> files) implements FileStorageService {

		@Override
		public Mono<String> storeFile(FilePart filePart, String subPath) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String storeFile(MultipartFile file, String subPath) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean deleteFile(String filePath) {
			return false;
		}

		@Override
		public String getFileUrl(String filePath) {
			return files.get(filePath).toUri().toString();
		}

		@Override
		public Resource getFileResource(String filePath) {
			return new FileSystemResource(files.get(filePath));
		}

	}

}
