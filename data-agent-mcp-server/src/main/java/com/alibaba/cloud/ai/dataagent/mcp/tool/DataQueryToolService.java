package com.alibaba.cloud.ai.dataagent.mcp.tool;

import com.alibaba.cloud.ai.dataagent.mcp.config.McpToolProperties;
import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository;
import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository.DatasourceSettings;
import com.alibaba.cloud.ai.dataagent.mcp.repository.BusinessDataSourceConnectionFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DataQueryToolService {

	private final ToolDataRepository repository;
	private final BusinessDataSourceConnectionFactory connections;
	private final McpToolProperties properties;
	private final ObjectMapper objectMapper;

	public DataQueryToolService(ToolDataRepository repository, BusinessDataSourceConnectionFactory connections,
			McpToolProperties properties, ObjectMapper objectMapper) {
		this.repository = repository;
		this.connections = connections;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public String inspectDataSource(long agentId) {
		DatasourceSettings datasource = connections.require(agentId);
		List<String> selectedTables = repository.findSelectedTables(datasource.relationId());
		try (Connection connection = connections.open(datasource)) {
			DatabaseMetaData metadata = connection.getMetaData();
			String schema = connections.schema(datasource);
			Set<String> allowed = selectedTables.isEmpty() ? discoverTables(metadata, connection.getCatalog(), schema)
					: new LinkedHashSet<>(selectedTables);
			List<Map<String, Object>> tables = new ArrayList<>();
			for (String table : allowed) {
				Map<String, Object> tableInfo = new LinkedHashMap<>();
				tableInfo.put("table", table);
				List<Map<String, String>> columns = new ArrayList<>();
				try (ResultSet rs = metadata.getColumns(connection.getCatalog(), schema, table, "%")) {
					while (rs.next()) {
						Map<String, String> column = new LinkedHashMap<>();
						column.put("name", rs.getString("COLUMN_NAME"));
						column.put("type", rs.getString("TYPE_NAME"));
						column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? "true" : "false");
						column.put("comment", value(rs.getString("REMARKS")));
						columns.add(column);
					}
				}
				tableInfo.put("columns", columns);
				tables.add(tableInfo);
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("datasource", datasource.name());
			result.put("dialect", datasource.type());
			result.put("tables", tables);
			result.put("businessContext", repository.findBusinessContext(agentId, datasource.datasourceId()));
			return json(result);
		}
		catch (SQLException ex) { throw new IllegalStateException("Failed to inspect datasource metadata: " + ex.getMessage(), ex); }
	}

	public String executeReadOnlySql(long agentId, String sql) {
		String safeSql = SqlSafety.requireReadOnly(sql);
		DatasourceSettings datasource = connections.require(agentId);
		try (Connection connection = connections.open(datasource)) {
			connection.setReadOnly(true);
			try (Statement statement = connection.createStatement()) {
				statement.setMaxRows(Math.max(1, properties.getMaxQueryRows()));
				statement.setQueryTimeout(30);
				try (ResultSet rs = statement.executeQuery(safeSql)) {
					ResultSetMetaData metadata = rs.getMetaData();
					List<String> columns = new ArrayList<>();
					for (int i = 1; i <= metadata.getColumnCount(); i++) columns.add(metadata.getColumnLabel(i));
					List<Map<String, Object>> rows = new ArrayList<>();
					while (rs.next()) {
						Map<String, Object> row = new LinkedHashMap<>();
						for (int i = 1; i <= columns.size(); i++) row.put(columns.get(i - 1), rs.getObject(i));
						rows.add(row);
					}
					return json(Map.of("sql", safeSql, "columns", columns, "rows", rows, "rowCount", rows.size(),
							"truncated", rows.size() >= properties.getMaxQueryRows()));
				}
			}
		}
		catch (SQLException ex) { throw new IllegalStateException("SQL execution failed: " + ex.getMessage(), ex); }
	}

	private Set<String> discoverTables(DatabaseMetaData metadata, String catalog, String schema) throws SQLException {
		Set<String> tables = new LinkedHashSet<>();
		try (ResultSet rs = metadata.getTables(catalog, schema, "%", new String[] { "TABLE", "VIEW" })) {
			while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
		}
		return tables;
	}
	private String json(Object value) {
		try { return objectMapper.writeValueAsString(value); }
		catch (JsonProcessingException ex) { throw new IllegalStateException("Failed to serialize tool result", ex); }
	}
	private String value(String value) { return value == null ? "" : value; }
}
