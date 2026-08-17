package com.alibaba.cloud.ai.dataagent.mcp.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class ToolDataRepository {

	private final JdbcTemplate jdbcTemplate;

	public ToolDataRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public Optional<EmbeddingSettings> findActiveEmbeddingModel() {
		try {
			return jdbcTemplate.query("""
					SELECT provider, base_url, api_key, model_name, embeddings_path FROM model_config
					WHERE model_type = 'EMBEDDING' AND is_active = 1 AND is_deleted = 0
					ORDER BY id DESC LIMIT 1
					""", rs -> rs.next() ? Optional.of(new EmbeddingSettings(rs.getString("provider"), rs.getString("base_url"),
					rs.getString("api_key"), rs.getString("model_name"), rs.getString("embeddings_path"))) : Optional.empty());
		}
		catch (DataAccessException ex) { return Optional.empty(); }
	}

	public Map<Integer, KnowledgeRecord> findRecalledKnowledge(long agentId) {
		try {
			return jdbcTemplate.query("""
					SELECT id, title, type, question, content, source_filename FROM agent_knowledge
					WHERE agent_id = ? AND is_recall = 1 AND is_deleted = 0 ORDER BY id
					""", (rs, rowNum) -> new KnowledgeRecord(rs.getInt("id"), rs.getString("title"), rs.getString("type"),
					rs.getString("question"), rs.getString("content"), rs.getString("source_filename")), agentId)
				.stream().collect(Collectors.toUnmodifiableMap(KnowledgeRecord::id, Function.identity()));
		}
		catch (DataAccessException ex) { return Map.of(); }
	}

	public Optional<DatasourceSettings> findActiveDatasource(long agentId) {
		try {
			return jdbcTemplate.query("""
					SELECT ad.id AS relation_id, d.id, d.name, d.type, d.host, d.port,
					       d.database_name, d.username, d.password, d.connection_url
					FROM agent_datasource ad JOIN datasource d ON d.id = ad.datasource_id
					WHERE ad.agent_id = ? AND ad.is_active = 1 ORDER BY ad.update_time DESC LIMIT 1
					""", rs -> rs.next() ? Optional.of(mapDatasource(rs)) : Optional.empty(), agentId);
		}
		catch (DataAccessException ex) { return Optional.empty(); }
	}

	public List<String> findSelectedTables(int relationId) {
		try {
			return jdbcTemplate.queryForList("SELECT table_name FROM agent_datasource_tables WHERE agent_datasource_id = ? ORDER BY table_name",
					String.class, relationId);
		}
		catch (DataAccessException ex) { return List.of(); }
	}

	public String findBusinessContext(long agentId, int datasourceId) {
		List<String> lines = new ArrayList<>();
		try {
			jdbcTemplate.query("SELECT business_term, description, synonyms FROM business_knowledge WHERE agent_id = ? AND is_recall = 1 AND is_deleted = 0 ORDER BY id",
					(RowCallbackHandler) rs -> lines.add("业务术语 %s：%s；同义词：%s".formatted(rs.getString(1), value(rs.getString(2)), value(rs.getString(3)))), agentId);
		}
		catch (DataAccessException ignored) { }
		try {
			jdbcTemplate.query("""
					SELECT table_name, column_name, business_name, synonyms, business_description, data_type
					FROM semantic_model WHERE agent_id = ? AND datasource_id = ? AND status = 1
					ORDER BY table_name, column_name
					""", (RowCallbackHandler) rs -> lines.add("语义字段 %s.%s（%s，%s）：%s；同义词：%s".formatted(rs.getString(1), rs.getString(2),
					value(rs.getString(3)), value(rs.getString(6)), value(rs.getString(5)), value(rs.getString(4)))), agentId, datasourceId);
		}
		catch (DataAccessException ignored) { }
		return String.join("\n", lines);
	}

	private DatasourceSettings mapDatasource(ResultSet rs) throws SQLException {
		return new DatasourceSettings(rs.getInt("relation_id"), rs.getInt("id"), rs.getString("name"), rs.getString("type"),
				rs.getString("host"), rs.getInt("port"), rs.getString("database_name"), rs.getString("username"),
				rs.getString("password"), rs.getString("connection_url"));
	}

	private String value(String value) { return value == null ? "" : value; }

	public record EmbeddingSettings(String provider, String baseUrl, String apiKey, String modelName, String embeddingsPath) { }
	public record KnowledgeRecord(int id, String title, String type, String question, String content, String sourceFilename) { }
	public record DatasourceSettings(int relationId, int datasourceId, String name, String type, String host, int port,
			String databaseName, String username, String password, String connectionUrl) { }
}
