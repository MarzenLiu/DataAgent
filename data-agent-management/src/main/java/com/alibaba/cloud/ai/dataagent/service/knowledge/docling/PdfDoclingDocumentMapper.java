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
import ai.docling.core.DoclingDocument.DocItemLabel;
import ai.docling.core.DoclingDocument.GroupItem;
import ai.docling.core.DoclingDocument.PictureItem;
import ai.docling.core.DoclingDocument.ProvenanceItem;
import ai.docling.core.DoclingDocument.RefItem;
import ai.docling.core.DoclingDocument.SectionHeaderItem;
import ai.docling.core.DoclingDocument.TableCell;
import ai.docling.core.DoclingDocument.TableItem;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PdfDoclingDocumentMapper implements DoclingDocumentMapper {

	private final DoclingProperties properties;

	public PdfDoclingDocumentMapper(DoclingProperties properties) {
		this.properties = properties;
	}

	@Override
	public boolean supports(String extension) {
		return "pdf".equals(extension);
	}

	@Override
	public List<Document> map(DoclingDocument document, String sourceFilename) {
		MappingContext context = new MappingContext(document, sourceFilename);
        traverseGroup(document.getBody(), context);
        context.flushText();
		return context.documents;
	}

	private void traverseGroup(GroupItem group, MappingContext context) {
		if (group == null || !context.visitedGroups.add(group.getSelfRef())) {
			return;
		}
		for (RefItem child : group.getChildren()) {
			Object item = DoclingMappingSupport.resolveItem(context.document, child.getRef());
			if (item instanceof GroupItem childGroup) {
				traverseGroup(childGroup, context);
			}
			else if (item instanceof BaseTextItem textItem) {
				context.addText(textItem);
			}
			else if (item instanceof TableItem tableItem) {
				context.addTable(tableItem);
			}
			else if (item instanceof PictureItem pictureItem) {
				context.addPicture(pictureItem);
			}
		}
	}

	private final class MappingContext {

		private final DoclingDocument document;

		private final String sourceFilename;

		private final List<Document> documents = new ArrayList<>();

		private final Set<String> visitedGroups = new HashSet<>();

		private final List<String> headings = new ArrayList<>();

		private final List<String> textElementIds = new ArrayList<>();

		private final StringBuilder text = new StringBuilder();

		private Integer textPage;

		private List<ProvenanceItem> textProvenance = List.of();

		private List<String> textSectionPath = List.of();

		private int chunkIndex;

		private MappingContext(DoclingDocument document, String sourceFilename) {
			this.document = document;
			this.sourceFilename = sourceFilename;
		}

		private void addText(BaseTextItem item) {
			if (item.getContentLayer() == DoclingDocument.ContentLayer.FURNITURE
					|| item.getLabel() == DocItemLabel.PAGE_HEADER || item.getLabel() == DocItemLabel.PAGE_FOOTER
					|| !StringUtils.hasText(item.getText())) {
				return;
			}
			Integer page = DoclingMappingSupport.pageNumber(item.getProv());
			if (item.getLabel() == DocItemLabel.TITLE || item.getLabel() == DocItemLabel.SECTION_HEADER) {
				flushText();
				int level = item instanceof SectionHeaderItem section && section.getLevel() != null ? section.getLevel() : 1;
				updateHeading(level, item.getText());
			}
			else if (text.length() > 0 && page != null && textPage != null && !page.equals(textPage)) {
				flushText();
			}
			if (text.length() == 0) {
				textPage = page;
				textProvenance = item.getProv() == null ? List.of() : List.copyOf(item.getProv());
				textSectionPath = List.copyOf(headings);
			}
			text.append(item.getText().strip()).append('\n');
			textElementIds.add(item.getSelfRef());
		}

		private void updateHeading(int requestedLevel, String heading) {
			int level = Math.max(1, requestedLevel);
			while (headings.size() >= level) {
				headings.remove(headings.size() - 1);
			}
			headings.add(heading.strip());
		}

		private void flushText() {
			if (text.length() == 0) {
				return;
			}
			String sourceElementId = String.join(",", textElementIds);
			Map<String, Object> metadata = DoclingMappingSupport.baseMetadata(document, sourceFilename, "text",
					sourceElementId);
			DoclingMappingSupport.addProvenance(metadata, document, textProvenance);
			if (!textSectionPath.isEmpty()) {
				metadata.put(DocumentMetadataConstant.SECTION_PATH, String.join(" > ", textSectionPath));
			}
			metadata.put(DocumentMetadataConstant.CHUNK_INDEX, chunkIndex);
			String prefix = textSectionPath.isEmpty() ? ""
					: "标题路径: " + String.join(" > ", textSectionPath) + "\n\n";
			documents.add(new Document(DoclingMappingSupport.deterministicId(sourceFilename, sourceElementId, chunkIndex),
					prefix + text.toString().strip(), metadata));
			chunkIndex++;
			text.setLength(0);
			textElementIds.clear();
			textPage = null;
			textProvenance = List.of();
			textSectionPath = List.of();
		}

		private void addTable(TableItem table) {
			flushText();
			List<List<TableCell>> rows = DoclingMappingSupport.tableRows(table.getData());
			if (rows.isEmpty()) {
				return;
			}
			int headerRows = DoclingMappingSupport.headerRowCount(rows);
			int firstDataRow = Math.min(headerRows, rows.size());
			int maxRows = Math.max(1, maxTableRowsPerChunk(sourceFilename));
			String caption = DoclingMappingSupport.captions(document, table.getCaptions());
			if (firstDataRow >= rows.size()) {
				addTableChunk(table, rows, headerRows, firstDataRow, firstDataRow, caption);
				return;
			}
			for (int start = firstDataRow; start < rows.size(); start += maxRows) {
				addTableChunk(table, rows, headerRows, start, Math.min(rows.size(), start + maxRows), caption);
			}
		}

		private void addTableChunk(TableItem table, List<List<TableCell>> rows, int headerRows, int start, int end,
				String caption) {
				Map<String, Object> metadata = DoclingMappingSupport.baseMetadata(document, sourceFilename, "table",
						table.getSelfRef());
				DoclingMappingSupport.addProvenance(metadata, document, table.getProv());
				if (!headings.isEmpty()) {
					metadata.put(DocumentMetadataConstant.SECTION_PATH, String.join(" > ", headings));
				}
				metadata.put(DocumentMetadataConstant.ROW_START, start + 1);
				metadata.put(DocumentMetadataConstant.ROW_END, end);
				metadata.put(DocumentMetadataConstant.CHUNK_INDEX, chunkIndex);
				StringBuilder content = new StringBuilder();
				if (!headings.isEmpty()) {
					content.append("标题路径: ").append(String.join(" > ", headings)).append("\n");
				}
				if (StringUtils.hasText(caption)) {
					content.append("表格标题: ").append(caption).append("\n");
				}
				content.append(DoclingMappingSupport.markdownTable(rows, headerRows, start, end));
				documents.add(new Document(
						DoclingMappingSupport.deterministicId(sourceFilename, table.getSelfRef(), chunkIndex), content.toString(),
						metadata));
				chunkIndex++;
		}

		private void addPicture(PictureItem picture) {
			flushText();
			String caption = DoclingMappingSupport.captions(document, picture.getCaptions());
			String description = picture.getMeta() != null && picture.getMeta().getDescription() != null
					? picture.getMeta().getDescription().getText() : "";
			if (!StringUtils.hasText(caption) && !StringUtils.hasText(description)) {
				return;
			}
			Map<String, Object> metadata = DoclingMappingSupport.baseMetadata(document, sourceFilename, "picture",
					picture.getSelfRef());
			DoclingMappingSupport.addProvenance(metadata, document, picture.getProv());
			if (!headings.isEmpty()) {
				metadata.put(DocumentMetadataConstant.SECTION_PATH, String.join(" > ", headings));
			}
			metadata.put(DocumentMetadataConstant.CHUNK_INDEX, chunkIndex);
			String content = (StringUtils.hasText(caption) ? "图片标题: " + caption + "\n" : "")
					+ (StringUtils.hasText(description) ? "图片描述: " + description : "");
			documents.add(new Document(
					DoclingMappingSupport.deterministicId(sourceFilename, picture.getSelfRef(), chunkIndex), content.strip(), metadata));
			chunkIndex++;
		}

	}

	protected int maxTableRowsPerChunk(String sourceFilename) {
		return properties.getPdf().getMaxTableRowsPerChunk();
	}

}
