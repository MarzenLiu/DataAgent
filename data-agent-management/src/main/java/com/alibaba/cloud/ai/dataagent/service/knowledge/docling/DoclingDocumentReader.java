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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import ai.docling.core.DoclingDocument;
import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.ImageRefMode;
import ai.docling.serve.api.convert.request.options.InputFormat;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.options.TableFormerMode;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import com.alibaba.cloud.ai.dataagent.service.knowledge.TextSplitterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class DoclingDocumentReader {

	private final DoclingProperties properties;

	private final DoclingServeApi client;

	private final List<DoclingDocumentMapper> mappers;

	private final TextSplitterFactory textSplitterFactory;


	public	DoclingDocumentReader(DoclingProperties properties, DoclingServeApi client, List<DoclingDocumentMapper> mappers,
			TextSplitterFactory textSplitterFactory) {
		this.properties = properties;
		this.client = client;
		this.mappers = mappers;
		this.textSplitterFactory = textSplitterFactory;
	}

	public boolean supports(String sourceFilename, String fileType) {
		if (!properties.isEnabled() || client == null) {
			return false;
		}
		String extension = extension(sourceFilename, fileType);
		return mappers.stream().anyMatch(mapper -> mapper.supports(extension));
	}

	public boolean fallbackToTika() {
		return properties.isFallbackToTika();
	}

	public List<Document> read(Resource resource, String sourceFilename, String fileType, String splitterType) {
		String filename = StringUtils.hasText(sourceFilename) ? sourceFilename : resource.getFilename();
		String extension = extension(filename, fileType);
		if (!StringUtils.hasText(filename)) {
			filename = "document." + extension;
		}
		DoclingDocumentMapper mapper = mappers.stream()
			.filter(candidate -> candidate.supports(extension))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unsupported Docling document format: " + extension));

        try (MaterializedResource materialized = materialize(resource, filename, extension)) {
            ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                    .options(options(extension))
                    .build();
            var response = client.convertFilesAsync(request, materialized.path()).toCompletableFuture().join();
            if (!(response instanceof InBodyConvertDocumentResponse inBody)) {
                throw new IllegalStateException("Docling returned unsupported response type: " + response.getResponseType());
            }
            if ("failure".equalsIgnoreCase(inBody.getStatus()) || "skipped".equalsIgnoreCase(inBody.getStatus())) {
                throw new IllegalStateException("Docling conversion failed with status: " + inBody.getStatus());
            }
            DoclingDocument document = inBody.getDocument() == null ? null : inBody.getDocument().getJsonContent();
            if (document == null) {
                throw new IllegalStateException("Docling response did not contain json_content");
            }
            List<Document> mapped = mapper.map(document, filename);
            List<Document> result = "pdf".equals(extension) ? splitPdfText(mapped, splitterType) : mapped;
            log.info("Docling parsed document: filename={}, format={}, status={}, chunks={}", filename, extension,
                    inBody.getStatus(), result.size());
            return result;
        }
	}

	private ConvertDocumentOptions options(String extension) {
		var builder = ConvertDocumentOptions.builder()
			.toFormat(OutputFormat.JSON)
			.imageExportMode(ImageRefMode.PLACEHOLDER)
			.includeImages(true)
			.doTableStructure(true)
			.abortOnError(false)
			.documentTimeout(properties.getAsyncTimeout());
		if ("pdf".equals(extension)) {
			builder.fromFormat(InputFormat.PDF)
				.doOcr(properties.getPdf().isOcrEnabled())
				.tableMode(TableFormerMode.ACCURATE);
		}
		else {
			builder.fromFormat(InputFormat.XLSX).doOcr(false);
		}
		return builder.build();
	}

	private List<Document> splitPdfText(List<Document> documents, String splitterType) {
		TextSplitter splitter = textSplitterFactory.getSplitter(splitterType);
		List<Document> result = new ArrayList<>();
		for (Document document : documents) {
			if (!"text".equals(document.getMetadata().get(DocumentMetadataConstant.ELEMENT_TYPE))) {
				result.add(document);
				continue;
			}
			List<Document> split = splitter.apply(List.of(document));
			if (split.isEmpty()) {
				result.add(document);
				continue;
			}
			for (int index = 0; index < split.size(); index++) {
				Document chunk = split.get(index);
				var metadata = new HashMap<String, Object>(document.getMetadata());
				metadata.putAll(chunk.getMetadata());
				metadata.put("splitChunkIndex", index);
				String sectionPath = (String) metadata.get(DocumentMetadataConstant.SECTION_PATH);
				String text = StringUtils.hasText(sectionPath) && !chunk.getText().startsWith("标题路径:")
						? "标题路径: " + sectionPath + "\n\n" + chunk.getText() : chunk.getText();
				String sourceElementId = (String) metadata.getOrDefault(DocumentMetadataConstant.SOURCE_ELEMENT_ID,
						document.getId());
				result.add(new Document(DoclingMappingSupport.deterministicId(
						(String) metadata.get(DocumentMetadataConstant.SOURCE_FILENAME), sourceElementId, index), text, metadata));
			}
		}
		return result;
	}

	private MaterializedResource materialize(Resource resource, String filename, String extension) {
		String suffix = StringUtils.hasText(extension) ? "." + extension : ".bin";
		try {
			Path path = Files.createTempFile("data-agent-docling-", suffix);
			try (InputStream input = resource.getInputStream()) {
				Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
			}
			return new MaterializedResource(path);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to materialize document for Docling: " + filename, ex);
		}
	}

	private String extension(String sourceFilename, String fileType) {
		String candidate = StringUtils.hasText(sourceFilename) ? sourceFilename : fileType;
		if (!StringUtils.hasText(candidate)) {
			return "";
		}
		candidate = candidate.toLowerCase(Locale.ROOT);
		int dot = candidate.lastIndexOf('.');
		return dot >= 0 ? candidate.substring(dot + 1) : candidate.replace("application/", "");
	}

	private record MaterializedResource(Path path) implements AutoCloseable {

		@Override
		public void close() {
			try {
				Files.deleteIfExists(path);
			}
			catch (IOException ex) {
				log.warn("Failed to delete temporary Docling input: {}", path, ex);
			}
		}

	}

}
