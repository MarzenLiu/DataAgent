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
package com.alibaba.cloud.ai.dataagent.splitter;

import com.alibaba.cloud.ai.dataagent.rag.Document;
import java.util.ArrayList;
import java.util.List;

/** Framework-neutral text splitter used during knowledge ingestion. */
public abstract class TextSplitter {

	public abstract List<String> splitText(String text);

	public List<Document> apply(List<Document> documents) {
		List<Document> result = new ArrayList<>();
		for (Document document : documents) {
			List<String> chunks = splitText(document.getText());
			if (chunks.isEmpty()) {
				result.add(document);
				continue;
			}
			for (int index = 0; index < chunks.size(); index++) {
				var metadata = new java.util.LinkedHashMap<>(document.getMetadata());
				metadata.put("chunk_index", index);
				metadata.put("chunk_size", chunks.get(index).length());
				result.add(new Document(chunks.get(index), metadata));
			}
		}
		return result;
	}

}
