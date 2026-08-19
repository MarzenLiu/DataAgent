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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.rag.Document;
import java.util.List;
import java.util.Map;

/** Application-layer ingestion API shared with the AgentScope MCP retrieval service. */
public interface AgentVectorStoreService {

	Boolean deleteDocumentsByVectorType(String agentId, String vectorType);

	Boolean deleteDocumentsByMetadata(String agentId, Map<String, Object> metadata);

	Boolean deleteDocumentsByMetadata(Map<String, Object> metadata);

	boolean hasTableDocuments(Integer datasourceId, List<String> tableNames);

	void replaceDocumentsByMetadata(Map<String, Object> metadata, List<Document> documents);

	void addDocuments(String ownerId, List<Document> documents);

}
