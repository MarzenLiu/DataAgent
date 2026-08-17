package com.alibaba.cloud.ai.dataagent.mcp;

import com.alibaba.cloud.ai.dataagent.mcp.config.McpToolProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(McpToolProperties.class)
public class DataAgentMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DataAgentMcpServerApplication.class, args);
	}

}
