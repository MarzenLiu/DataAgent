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
package com.alibaba.cloud.ai.dataagent.service.schema;

import static com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil.convertColumnsToDocuments;
import static com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil.convertTablesToDocuments;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SchemaInitRequest;
import com.alibaba.cloud.ai.dataagent.rag.Document;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads relational metadata and writes framework-neutral vectors. AgentScope retrieves
 * these records through the standalone MCP service.
 */
@Slf4j
@Service
@AllArgsConstructor
public class SchemaServiceImpl implements SchemaService {

	private final ExecutorService dbOperationExecutor;

	private final AccessorFactory accessorFactory;

	private final TableMetadataService tableMetadataService;

	private final AgentVectorStoreService agentVectorStoreService;

	@Override
	public Boolean schema(Integer datasourceId, SchemaInitRequest request) {
		log.info("Starting schema initialization for datasource {}", datasourceId);
		DbConfigBO config = request.getDbConfig();
		DbQueryParameter query = DbQueryParameter.from(config)
			.setSchema(config.getSchema())
			.setTables(request.getTables());
		try {
			Accessor accessor = accessorFactory.getAccessorByDbConfig(config);
			List<ForeignKeyInfoBO> foreignKeys = accessor.showForeignKeys(config, query);
			List<TableInfoBO> tables = accessor.fetchTables(config, query);
			Map<String, List<String>> foreignKeyMap = buildForeignKeyMap(foreignKeys);
			if (tables.size() > 5) {
				processTablesInParallel(tables, config, foreignKeyMap);
			}
			else {
				tableMetadataService.batchEnrichTableMetadata(tables, config, foreignKeyMap);
			}
			List<Document> documents = new ArrayList<>();
			documents.addAll(convertColumnsToDocuments(datasourceId, tables));
			documents.addAll(convertTablesToDocuments(datasourceId, tables));
			if (documents.isEmpty()) {
				throw new IllegalStateException("Refusing to replace schema vectors with an empty schema");
			}
			agentVectorStoreService.replaceDocumentsByMetadata(
					Map.of(Constant.DATASOURCE_ID, datasourceId.toString()), documents);
			log.info("Stored {} schema documents for datasource {}", documents.size(), datasourceId);
			return true;
		}
		catch (Exception ex) {
			log.error("Failed to initialize schema for datasource {}", datasourceId, ex);
			return false;
		}
	}

	private void processTablesInParallel(List<TableInfoBO> tables, DbConfigBO config,
			Map<String, List<String>> foreignKeyMap) {
		int parallelism = Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() * 2), tables.size());
		int batchSize = (int) Math.ceil((double) tables.size() / parallelism);
		List<CompletableFuture<Void>> futures = partition(tables, batchSize).stream()
			.map(batch -> CompletableFuture.runAsync(() -> {
				try {
					tableMetadataService.batchEnrichTableMetadata(batch, config, foreignKeyMap);
				}
				catch (Exception ex) {
					throw new CompletionException(ex);
				}
			}, dbOperationExecutor))
			.toList();
		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
	}

	private <T> List<List<T>> partition(List<T> values, int batchSize) {
		List<List<T>> partitions = new ArrayList<>();
		for (int start = 0; start < values.size(); start += batchSize) {
			partitions.add(values.subList(start, Math.min(start + batchSize, values.size())));
		}
		return partitions;
	}

	private Map<String, List<String>> buildForeignKeyMap(List<ForeignKeyInfoBO> foreignKeys) {
		Map<String, List<String>> result = new HashMap<>();
		for (ForeignKeyInfoBO foreignKey : foreignKeys) {
			String relation = foreignKey.getTable() + "." + foreignKey.getColumn() + "="
					+ foreignKey.getReferencedTable() + "." + foreignKey.getReferencedColumn();
			result.computeIfAbsent(foreignKey.getTable(), ignored -> new ArrayList<>()).add(relation);
			result.computeIfAbsent(foreignKey.getReferencedTable(), ignored -> new ArrayList<>()).add(relation);
		}
		return result;
	}

}
