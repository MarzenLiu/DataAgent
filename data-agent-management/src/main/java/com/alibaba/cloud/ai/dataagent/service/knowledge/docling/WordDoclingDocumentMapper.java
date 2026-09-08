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
package com.alibaba.cloud.ai.dataagent.service.knowledge.docling;

import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import org.springframework.stereotype.Component;

/**
 * Maps Docling's structured DOCX output with the same reading-order semantics used
 * for paginated documents. Legacy binary DOC files are intentionally handled by
 * Tika/POI because Docling Serve does not accept the DOC input format.
 */
@Component
public class WordDoclingDocumentMapper extends PdfDoclingDocumentMapper {

	private final DoclingProperties properties;

	public WordDoclingDocumentMapper(DoclingProperties properties) {
		super(properties);
		this.properties = properties;
	}

	@Override
	public boolean supports(String extension) {
		return "docx".equals(extension);
	}

	@Override
	protected int maxTableRowsPerChunk(String sourceFilename) {
		return properties.getWord().getMaxTableRowsPerChunk();
	}

}
