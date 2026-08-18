package com.alibaba.cloud.ai.dataagent.agentscope.repository;

import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.AgentProfile;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ApprovalMode;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ChatModelConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ModelSettings;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.SkillConfiguration;
import com.alibaba.cloud.ai.dataagent.agentscope.entity.ToolConfiguration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** Stores only AgentScope orchestration configuration and generated reports. */
@Repository
public class DataAgentRegistryRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(DataAgentRegistryRepository.class);

	private final DataAgentRegistryMapper mapper;

	public DataAgentRegistryRepository(DataAgentRegistryMapper mapper) {
		this.mapper = mapper;
	}

	public Optional<AgentConfiguration> findAgentConfiguration(long agentId) {
		AgentProfile agent = mapper.findAgentById(agentId);
		if (agent == null) {
			return Optional.empty();
		}
		List<ToolConfiguration> tools = mapper.findEnabledTools(agentId)
			.stream()
			.map(tool -> new ToolConfiguration(tool.toolName(), ApprovalMode.from(tool.approvalMode()),
					tool.injectAgentId(), tool.availableInNl2sqlOnly()))
			.toList();
		List<SkillConfiguration> skills = mapper.findEnabledSkills(agentId)
			.stream()
			.map(skill -> new SkillConfiguration(skill.skillName(), skill.databaseUpdatedAt()))
			.toList();
		return Optional.of(new AgentConfiguration(agent.name(), agent.description(), agent.prompt(), tools, skills));
	}

	public boolean requiresHumanApproval(long agentId) {
		return mapper.countAlwaysAskTools(agentId) > 0;
	}

	public Optional<Long> findConversationAgentId(String conversationId) {
		if (!StringUtils.hasText(conversationId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(mapper.findConversationAgentId(conversationId));
	}

	public Optional<ModelSettings> findActiveChatModel() {
		ChatModelConfiguration model = mapper.findActiveChatModel();
		if (model == null) {
			return Optional.empty();
		}
		return Optional.of(new ModelSettings(model.provider(), model.baseUrl(), model.apiKey(), model.modelName(),
				model.temperature(), model.maxTokens(), model.completionsPath()));
	}

	public void saveReport(String conversationId, long agentId, String sourceQuery, String content) {
		if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(content)) {
			return;
		}
		try {
			mapper.insertReport(conversationId, agentId, sourceQuery, content);
		}
		catch (DataAccessException ex) {
			LOGGER.warn("Failed to persist generated report, agentId={}, conversationId={}", agentId, conversationId,
					ex);
		}
	}

}
