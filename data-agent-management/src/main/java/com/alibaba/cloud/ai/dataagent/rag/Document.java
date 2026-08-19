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
package com.alibaba.cloud.ai.dataagent.rag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Framework-neutral document persisted by the DataAgent knowledge store. */
public final class Document {

	private final String id;

	private final String text;

	private final Map<String, Object> metadata;

	public Document(String text) {
		this(UUID.randomUUID().toString(), text, Map.of());
	}

	public Document(String text, Map<String, Object> metadata) {
		this(UUID.randomUUID().toString(), text, metadata);
	}

	public Document(String id, String text, Map<String, Object> metadata) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Document id must not be blank");
		}
		if (text == null) {
			throw new IllegalArgumentException("Document text must not be null");
		}
		this.id = id;
		this.text = text;
		this.metadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
	}

	public String getId() {
		return id;
	}

	public String getText() {
		return text;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public Builder mutate() {
		return new Builder().id(id).text(text).metadata(metadata);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String id = UUID.randomUUID().toString();

		private String text;

		private Map<String, Object> metadata = Map.of();

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder text(String text) {
			this.text = text;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public Document build() {
			return new Document(id, text, metadata);
		}

	}

}
