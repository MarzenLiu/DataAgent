package com.alibaba.cloud.ai.dataagent.agentscope.agent;

import com.alibaba.cloud.ai.dataagent.agentscope.config.AgentScopeDataAgentProperties;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.springframework.stereotype.Component;

@Component
public class DataAgentMcpClientFactory {

	private final AgentScopeDataAgentProperties properties;

	public DataAgentMcpClientFactory(AgentScopeDataAgentProperties properties) {
		this.properties = properties;
	}

	public McpClientWrapper connect() {
		return McpClientBuilder.create("data-agent-tools")
			.streamableHttpTransport(properties.getMcp().getUrl())
			.timeout(properties.getMcp().getTimeout())
			.initializationTimeout(properties.getMcp().getTimeout())
			.buildSync();
	}

}
