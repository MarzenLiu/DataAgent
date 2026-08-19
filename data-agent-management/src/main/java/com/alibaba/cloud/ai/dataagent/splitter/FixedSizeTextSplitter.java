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

import java.util.ArrayList;
import java.util.List;

/** Deterministic character splitter with overlap for token/recursive compatibility modes. */
public class FixedSizeTextSplitter extends TextSplitter {

	private final int chunkSize;

	private final int overlap;

	public FixedSizeTextSplitter(int chunkSize, int overlap) {
		this.chunkSize = Math.max(1, chunkSize);
		this.overlap = Math.max(0, Math.min(overlap, this.chunkSize - 1));
	}

	@Override
	public List<String> splitText(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = Math.min(text.length(), start + chunkSize);
			String chunk = text.substring(start, end).trim();
			if (!chunk.isEmpty()) {
				chunks.add(chunk);
			}
			if (end == text.length()) {
				break;
			}
			start = end - overlap;
		}
		return chunks;
	}

}
