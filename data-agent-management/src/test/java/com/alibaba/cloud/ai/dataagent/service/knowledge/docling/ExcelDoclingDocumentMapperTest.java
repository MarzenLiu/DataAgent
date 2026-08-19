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
import ai.docling.core.DoclingDocument.ContentLayer;
import ai.docling.core.DoclingDocument.GroupItem;
import ai.docling.core.DoclingDocument.GroupLabel;
import ai.docling.core.DoclingDocument.RefItem;
import ai.docling.core.DoclingDocument.TableCell;
import ai.docling.core.DoclingDocument.TableData;
import ai.docling.core.DoclingDocument.TableItem;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DoclingProperties;
import org.junit.jupiter.api.Test;
import com.alibaba.cloud.ai.dataagent.rag.Document;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelDoclingDocumentMapperTest {

	@Test
	void mapsEachSheetAndRepeatsHeadersAcrossRowChunks() {
		DoclingProperties properties = new DoclingProperties();
		properties.getExcel().setMaxTableRowsPerChunk(2);
		ExcelDoclingDocumentMapper mapper = new ExcelDoclingDocumentMapper(properties);
		TableItem table = TableItem.builder()
			.selfRef("#/tables/0")
			.contentLayer(ContentLayer.BODY)
			.label("table")
			.data(TableData.builder()
				.grid(List.of(cell("月份", true), cell("销售额", true)))
				.grid(List.of(cell("1月", false), cell("10", false)))
				.grid(List.of(cell("2月", false), cell("20", false)))
				.grid(List.of(cell("3月", false), cell("30", false)))
				.numRows(4)
				.numCols(2)
				.build())
			.build();
		GroupItem sheet = GroupItem.builder()
			.selfRef("#/groups/0")
			.name("销售明细")
			.label(GroupLabel.SHEET)
			.contentLayer(ContentLayer.BODY)
			.child(ref("#/tables/0"))
			.build();
		GroupItem body = GroupItem.builder()
			.selfRef("#/body")
			.label(GroupLabel.UNSPECIFIED)
			.contentLayer(ContentLayer.BODY)
			.child(ref("#/groups/0"))
			.build();
		DoclingDocument document = DoclingDocument.builder()
			.name("sales")
			.version("1.8.0")
			.body(body)
			.group(sheet)
			.table(table)
			.build();

		List<Document> chunks = mapper.map(document, "sales.xlsx");

		assertThat(chunks).hasSize(2);
		assertThat(chunks).allSatisfy(chunk -> {
			assertThat(chunk.getText()).contains("工作表: 销售明细", "| 月份 | 销售额 |");
			assertThat(chunk.getMetadata()).containsEntry(DocumentMetadataConstant.SHEET_NAME, "销售明细")
				.containsEntry(DocumentMetadataConstant.ELEMENT_TYPE, "table");
		});
		assertThat(chunks.get(0).getText()).contains("1月", "2月").doesNotContain("3月");
		assertThat(chunks.get(1).getText()).contains("3月").doesNotContain("1月");
	}

	private static RefItem ref(String value) {
		return RefItem.builder().ref(value).build();
	}

	private static TableCell cell(String text, boolean header) {
		return TableCell.builder().text(text).columnHeader(header).rowSpan(1).colSpan(1).build();
	}

}
