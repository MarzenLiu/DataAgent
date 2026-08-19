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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.docling.core.DoclingDocument;
import ai.docling.core.DoclingDocument.BaseTextItem;
import ai.docling.core.DoclingDocument.GroupItem;
import ai.docling.core.DoclingDocument.GroupLabel;
import ai.docling.core.DoclingDocument.RefItem;
import ai.docling.core.DoclingDocument.TableCell;
import ai.docling.core.DoclingDocument.TableItem;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ExcelDoclingDocumentMapper implements DoclingDocumentMapper {

	private final DoclingProperties properties;

	public ExcelDoclingDocumentMapper(DoclingProperties properties) {
		this.properties = properties;
	}

	@Override
	public boolean supports(String extension) {
		return "xlsx".equals(extension);
	}

	@Override
	public List<Document> map(DoclingDocument document, String sourceFilename) {
		MappingContext context = new MappingContext(document, sourceFilename);
		if (document.getBody() != null) {
			for (RefItem child : document.getBody().getChildren()) {
				Object resolved = resolve(document, child);
				if (resolved instanceof GroupItem group && group.getLabel() == GroupLabel.SHEET) {
					context.mapSheet(group, sheetName(group, context.sheetIndex + 1));
				}
			}
		}
		for (TableItem table : document.getTables()) {
			if (!context.visitedTables.contains(table.getSelfRef())) {
				context.addTable(table, "Workbook");
			}
		}
		return context.documents;
	}

	private String sheetName(GroupItem group, int index) {
		return StringUtils.hasText(group.getName()) ? group.getName() : "Sheet " + index;
	}

	private Object resolve(DoclingDocument document, RefItem reference) {
		if (reference == null || !StringUtils.hasText(reference.getRef())) {
			return null;
		}
		String ref = reference.getRef();
		try {
			if (ref.startsWith("#/groups/")) {
				return document.getGroups().get(index(ref, "#/groups/"));
			}
			if (ref.startsWith("#/texts/")) {
				return document.getTexts().get(index(ref, "#/texts/"));
			}
			if (ref.startsWith("#/tables/")) {
				return document.getTables().get(index(ref, "#/tables/"));
			}
		}
		catch (RuntimeException ignored) {
			return null;
		}
		return null;
	}

	private int index(String reference, String prefix) {
		return Integer.parseInt(reference.substring(prefix.length()));
	}

	private final class MappingContext {

		private final DoclingDocument document;

		private final String sourceFilename;

		private final List<Document> documents = new ArrayList<>();

		private final Set<String> visitedGroups = new HashSet<>();

		private final Set<String> visitedTables = new HashSet<>();

		private int sheetIndex;

		private int tableIndex;

		private int chunkIndex;

		private MappingContext(DoclingDocument document, String sourceFilename) {
			this.document = document;
			this.sourceFilename = sourceFilename;
		}

		private void mapSheet(GroupItem sheet, String name) {
			sheetIndex++;
			StringBuilder looseText = new StringBuilder();
			walkSheet(sheet, name, looseText);
			if (!looseText.isEmpty()) {
				Map<String, Object> metadata = DoclingMappingSupport.baseMetadata(document, sourceFilename, "sheet_text",
						sheet.getSelfRef());
				metadata.put(DocumentMetadataConstant.SHEET_NAME, name);
				metadata.put(DocumentMetadataConstant.CHUNK_INDEX, chunkIndex);
				String content = "工作表: " + name + "\n\n" + looseText.toString().strip();
				documents.add(new Document(
						DoclingMappingSupport.deterministicId(sourceFilename, sheet.getSelfRef(), chunkIndex), content, metadata));
				chunkIndex++;
			}
		}

		private void walkSheet(GroupItem group, String sheetName, StringBuilder looseText) {
			if (group == null || !visitedGroups.add(group.getSelfRef())) {
				return;
			}
			for (RefItem child : group.getChildren()) {
				Object item = resolve(document, child);
				if (item instanceof GroupItem childGroup) {
					walkSheet(childGroup, sheetName, looseText);
				}
				else if (item instanceof TableItem table) {
					addTable(table, sheetName);
				}
				else if (item instanceof BaseTextItem textItem && StringUtils.hasText(textItem.getText())) {
					looseText.append(textItem.getText().strip()).append('\n');
				}
			}
		}

		private void addTable(TableItem table, String sheetName) {
			if (!visitedTables.add(table.getSelfRef())) {
				return;
			}
			tableIndex++;
			List<List<TableCell>> rows = DoclingMappingSupport.tableRows(table.getData());
			if (rows.isEmpty()) {
				return;
			}
			int headerRows = DoclingMappingSupport.headerRowCount(rows);
			int firstDataRow = Math.min(headerRows, rows.size());
			int maxRows = Math.max(1, properties.getExcel().getMaxTableRowsPerChunk());
			String caption = DoclingMappingSupport.captions(document, table.getCaptions());
			if (firstDataRow >= rows.size()) {
				addTableChunk(table, sheetName, caption, rows, headerRows, firstDataRow, firstDataRow);
				return;
			}
			for (int start = firstDataRow; start < rows.size(); start += maxRows) {
				addTableChunk(table, sheetName, caption, rows, headerRows, start, Math.min(rows.size(), start + maxRows));
			}
		}

		private void addTableChunk(TableItem table, String sheetName, String caption, List<List<TableCell>> rows,
				int headerRows, int start, int end) {
			Map<String, Object> metadata = DoclingMappingSupport.baseMetadata(document, sourceFilename, "table",
					table.getSelfRef());
			metadata.put(DocumentMetadataConstant.SHEET_NAME, sheetName);
			metadata.put(DocumentMetadataConstant.TABLE_INDEX, tableIndex);
			metadata.put(DocumentMetadataConstant.ROW_START, start + 1);
			metadata.put(DocumentMetadataConstant.ROW_END, end);
			metadata.put(DocumentMetadataConstant.CHUNK_INDEX, chunkIndex);
			StringBuilder content = new StringBuilder("工作表: ").append(sheetName).append('\n');
			if (StringUtils.hasText(caption)) {
				content.append("表格标题: ").append(caption).append('\n');
			}
			content.append(DoclingMappingSupport.markdownTable(rows, headerRows, start, end));
			documents.add(new Document(DoclingMappingSupport.deterministicId(sourceFilename, table.getSelfRef(), chunkIndex),
					content.toString(), metadata));
			chunkIndex++;
		}

	}

}
