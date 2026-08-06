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

import java.util.List;

import ai.docling.core.DoclingDocument;
import ai.docling.core.DoclingDocument.BoundingBox;
import ai.docling.core.DoclingDocument.ContentLayer;
import ai.docling.core.DoclingDocument.DocItemLabel;
import ai.docling.core.DoclingDocument.GroupItem;
import ai.docling.core.DoclingDocument.GroupLabel;
import ai.docling.core.DoclingDocument.ProvenanceItem;
import ai.docling.core.DoclingDocument.RefItem;
import ai.docling.core.DoclingDocument.SectionHeaderItem;
import ai.docling.core.DoclingDocument.TableCell;
import ai.docling.core.DoclingDocument.TableData;
import ai.docling.core.DoclingDocument.TableItem;
import ai.docling.core.DoclingDocument.TextItem;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDoclingDocumentMapperTest {

	@Test
	void mapsPdfReadingOrderHeadingsPageAndTableChunks() {
		DoclingProperties properties = new DoclingProperties();
		properties.getPdf().setMaxTableRowsPerChunk(1);
		PdfDoclingDocumentMapper mapper = new PdfDoclingDocumentMapper(properties);

		SectionHeaderItem heading = SectionHeaderItem.builder()
			.selfRef("#/texts/0")
			.contentLayer(ContentLayer.BODY)
			.label(DocItemLabel.SECTION_HEADER)
			.orig("财务分析")
			.text("财务分析")
			.level(1)
			.prov(provenance(2))
			.build();
		TextItem paragraph = TextItem.builder()
			.selfRef("#/texts/1")
			.contentLayer(ContentLayer.BODY)
			.label(DocItemLabel.TEXT)
			.orig("本期收入增长")
			.text("本期收入增长")
			.prov(provenance(2))
			.build();
		TableItem table = TableItem.builder()
			.selfRef("#/tables/0")
			.contentLayer(ContentLayer.BODY)
			.label("table")
			.prov(provenance(2))
			.data(TableData.builder()
				.grid(List.of(cell("区域", true), cell("收入", true)))
				.grid(List.of(cell("华东", false), cell("100", false)))
				.grid(List.of(cell("华南", false), cell("80", false)))
				.numRows(3)
				.numCols(2)
				.build())
			.build();
		GroupItem body = GroupItem.builder()
			.selfRef("#/body")
			.label(GroupLabel.UNSPECIFIED)
			.contentLayer(ContentLayer.BODY)
			.child(ref("#/texts/0"))
			.child(ref("#/texts/1"))
			.child(ref("#/tables/0"))
			.build();
		DoclingDocument document = DoclingDocument.builder()
			.name("report")
			.version("1.8.0")
			.body(body)
			.text(heading)
			.text(paragraph)
			.table(table)
			.build();

		List<org.springframework.ai.document.Document> chunks = mapper.map(document, "report.pdf");

		assertThat(chunks).hasSize(3);
		assertThat(chunks.get(0).getText()).contains("标题路径: 财务分析", "本期收入增长");
		assertThat(chunks.get(0).getMetadata()).containsEntry(DocumentMetadataConstant.PAGE_NUMBER, 2)
			.containsEntry(DocumentMetadataConstant.SECTION_PATH, "财务分析")
			.containsEntry(DocumentMetadataConstant.PARSER, "docling");
		assertThat(chunks.get(1).getText()).contains("| 区域 | 收入 |", "| 华东 | 100 |");
		assertThat(chunks.get(2).getText()).contains("| 区域 | 收入 |", "| 华南 | 80 |");
	}

	private static RefItem ref(String value) {
		return RefItem.builder().ref(value).build();
	}

	private static ProvenanceItem provenance(int page) {
		return ProvenanceItem.builder()
			.pageNo(page)
			.bbox(BoundingBox.builder().l(1.0).t(2.0).r(3.0).b(4.0).build())
			.build();
	}

	private static TableCell cell(String text, boolean header) {
		return TableCell.builder().text(text).columnHeader(header).rowSpan(1).colSpan(1).build();
	}

}
