package com.alibaba.cloud.ai.dataagent.agentscope.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** Stores only AgentScope orchestration configuration and generated reports. */
@Repository
public class DataAgentRegistryRepository {
	private final JdbcTemplate jdbcTemplate;

	public DataAgentRegistryRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

	public Optional<AgentConfiguration> findAgentConfiguration(long agentId) {
		try {
			return jdbcTemplate.query("SELECT name, description, prompt FROM agent WHERE id = ?", rs -> {
				if (!rs.next()) return Optional.empty();
				return Optional.of(new AgentConfiguration(rs.getString("name"), rs.getString("description"),
						rs.getString("prompt"), findEnabledTools(agentId)));
			}, agentId);
		}
		catch (DataAccessException ex) { return Optional.empty(); }
	}

	public boolean requiresHumanApproval(long agentId) {
		try {
			Integer count = jdbcTemplate.queryForObject("""
					SELECT COUNT(*) FROM agent_tool
					WHERE agent_id = ? AND is_enabled = 1 AND approval_mode = 'ALWAYS_ASK'
					""", Integer.class, agentId);
			return count != null && count > 0;
		}
		catch (DataAccessException ex) { return false; }
	}

	private List<ToolConfiguration> findEnabledTools(long agentId) {
		return jdbcTemplate.query("""
				SELECT tool_name, approval_mode, inject_agent_id, available_in_nl2sql_only
				FROM agent_tool WHERE agent_id = ? AND is_enabled = 1 ORDER BY sort_order, id
				""", (rs, rowNum) -> new ToolConfiguration(rs.getString("tool_name"),
				ApprovalMode.from(rs.getString("approval_mode")), rs.getBoolean("inject_agent_id"),
				rs.getBoolean("available_in_nl2sql_only")), agentId);
	}

	public Optional<ModelSettings> findActiveChatModel() {
		try {
			return jdbcTemplate.query("""
					SELECT provider, base_url, api_key, model_name, temperature, max_tokens, completions_path
					FROM model_config WHERE model_type = 'CHAT' AND is_active = 1 AND is_deleted = 0
					ORDER BY id DESC LIMIT 1
					""", rs -> rs.next() ? Optional.of(mapModel(rs)) : Optional.empty());
		}
		catch (DataAccessException ex) { return Optional.empty(); }
	}

	public void saveReport(String conversationId, long agentId, String sourceQuery, String content) {
		if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(content)) return;
		try {
			jdbcTemplate.update("""
					INSERT INTO report_artifact (conversation_id, agent_id, source_query, content, create_time)
					VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
					""", conversationId, agentId, sourceQuery, content);
		}
		catch (DataAccessException ignored) { }
	}

	private ModelSettings mapModel(ResultSet rs) throws SQLException {
		return new ModelSettings(rs.getString("provider"), rs.getString("base_url"), rs.getString("api_key"),
				rs.getString("model_name"), rs.getObject("temperature", Double.class),
				rs.getObject("max_tokens", Integer.class), rs.getString("completions_path"));
	}

	public record ModelSettings(String provider, String baseUrl, String apiKey, String modelName, Double temperature,
			Integer maxTokens, String endpointPath) { }
	public record AgentConfiguration(String name, String description, String prompt, List<ToolConfiguration> tools) { }
	public record ToolConfiguration(String toolName, ApprovalMode approvalMode, boolean injectAgentId,
			boolean availableInNl2sqlOnly) { }
	public enum ApprovalMode {
		ALLOW, ASK_WHEN_HITL, ALWAYS_ASK;

		static ApprovalMode from(String value) {
			try { return ApprovalMode.valueOf(value == null ? "" : value.trim().toUpperCase()); }
			catch (IllegalArgumentException ex) {
				throw new IllegalStateException("Unsupported agent_tool approval_mode: " + value, ex);
			}
		}
	}
}
