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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ai.docling.core.DoclingDocument;
import ai.docling.core.DoclingDocument.BaseTextItem;
import ai.docling.core.DoclingDocument.BoundingBox;
import ai.docling.core.DoclingDocument.ProvenanceItem;
import ai.docling.core.DoclingDocument.RefItem;
import ai.docling.core.DoclingDocument.TableCell;
import ai.docling.core.DoclingDocument.TableData;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import org.springframework.util.StringUtils;

final class DoclingMappingSupport {

	private DoclingMappingSupport() {
	}

	static Map<String, Object> baseMetadata(DoclingDocument document, String sourceFilename, String elementType,
			String sourceElementId) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.PARSER, "docling");
		metadata.put(DocumentMetadataConstant.PARSER_VERSION,
				StringUtils.hasText(document.getVersion()) ? document.getVersion() : "unknown");
		metadata.put(DocumentMetadataConstant.SOURCE_FILENAME, sourceFilename);
		metadata.put(DocumentMetadataConstant.ELEMENT_TYPE, elementType);
		if (StringUtils.hasText(sourceElementId)) {
			metadata.put(DocumentMetadataConstant.SOURCE_ELEMENT_ID, sourceElementId);
		}
		return metadata;
	}

	static void addProvenance(Map<String, Object> metadata, List<ProvenanceItem> provenance) {
		if (provenance == null || provenance.isEmpty()) {
			return;
		}
		ProvenanceItem first = provenance.get(0);
		if (first.getPageNo() != null) {
			metadata.put(DocumentMetadataConstant.PAGE_NUMBER, first.getPageNo());
		}
		BoundingBox bbox = first.getBbox();
		if (bbox != null) {
			metadata.put(DocumentMetadataConstant.BOUNDING_BOX,
					"%s,%s,%s,%s".formatted(bbox.getL(), bbox.getT(), bbox.getR(), bbox.getB()));
		}
	}

	static Integer pageNumber(List<ProvenanceItem> provenance) {
		return provenance == null || provenance.isEmpty() ? null : provenance.get(0).getPageNo();
	}

	static String deterministicId(String sourceFilename, String sourceElementId, int chunkIndex) {
		String seed = sourceFilename + '|' + sourceElementId + '|' + chunkIndex;
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
	}

	static String resolveText(DoclingDocument document, RefItem reference) {
		if (reference == null || !StringUtils.hasText(reference.getRef())) {
			return "";
		}
		String prefix = "#/texts/";
		if (!reference.getRef().startsWith(prefix)) {
			return "";
		}
		try {
			int index = Integer.parseInt(reference.getRef().substring(prefix.length()));
			BaseTextItem item = document.getTexts().get(index);
			return item == null ? "" : item.getText();
		}
		catch (RuntimeException ex) {
			return "";
		}
	}

	static String captions(DoclingDocument document, List<RefItem> references) {
		if (references == null) {
			return "";
		}
		return references.stream()
			.map(reference -> resolveText(document, reference))
			.filter(StringUtils::hasText)
			.distinct()
			.reduce((left, right) -> left + "；" + right)
			.orElse("");
	}

	static List<List<TableCell>> tableRows(TableData data) {
		if (data == null) {
			return Collections.emptyList();
		}
		if (data.getGrid() != null && !data.getGrid().isEmpty()) {
			return data.getGrid();
		}
		int rowCount = data.getNumRows() == null ? 0 : data.getNumRows();
		int columnCount = data.getNumCols() == null ? 0 : data.getNumCols();
		if (rowCount <= 0 || columnCount <= 0) {
			return Collections.emptyList();
		}
		List<List<TableCell>> rows = new ArrayList<>();
		for (int row = 0; row < rowCount; row++) {
			rows.add(new ArrayList<>(Collections.nCopies(columnCount, null)));
		}
		for (TableCell cell : data.getTableCells()) {
			int row = cell.getStartRowOffsetIdx() == null ? 0 : cell.getStartRowOffsetIdx();
			int column = cell.getStartColOffsetIdx() == null ? 0 : cell.getStartColOffsetIdx();
			if (row >= 0 && row < rows.size() && column >= 0 && column < columnCount) {
				rows.get(row).set(column, cell);
			}
		}
		return rows;
	}

	static int headerRowCount(List<List<TableCell>> rows) {
		int count = 0;
		for (List<TableCell> row : rows) {
			boolean header = row.stream().filter(cell -> cell != null).anyMatch(TableCell::isColumnHeader);
			if (!header) {
				break;
			}
			count++;
		}
		return count == 0 && rows.size() > 1 ? 1 : count;
	}

	static String markdownTable(List<List<TableCell>> rows, int headerRows, int dataStart, int dataEnd) {
		if (rows.isEmpty()) {
			return "";
		}
		List<List<TableCell>> selected = new ArrayList<>();
		selected.addAll(rows.subList(0, Math.min(headerRows, rows.size())));
		selected.addAll(rows.subList(Math.min(dataStart, rows.size()), Math.min(dataEnd, rows.size())));
		if (selected.isEmpty()) {
			selected.add(rows.get(0));
		}
		int columns = selected.stream().mapToInt(List::size).max().orElse(1);
		StringBuilder markdown = new StringBuilder();
		appendRow(markdown, selected.get(0), columns);
		markdown.append('|');
		for (int column = 0; column < columns; column++) {
			markdown.append(" --- |");
		}
		markdown.append('\n');
		for (int row = 1; row < selected.size(); row++) {
			appendRow(markdown, selected.get(row), columns);
		}
		return markdown.toString().stripTrailing();
	}

	private static void appendRow(StringBuilder markdown, List<TableCell> row, int columns) {
		markdown.append('|');
		for (int column = 0; column < columns; column++) {
			TableCell cell = column < row.size() ? row.get(column) : null;
			String text = cell == null || cell.getText() == null ? "" : cell.getText();
			markdown.append(' ').append(text.replace("|", "\\|").replace('\n', ' ')).append(" |");
		}
		markdown.append('\n');
	}

}
