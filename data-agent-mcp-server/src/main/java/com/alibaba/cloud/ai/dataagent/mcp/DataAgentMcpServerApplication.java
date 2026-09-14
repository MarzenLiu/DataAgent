package com.alibaba.cloud.ai.dataagent.mcp;

import com.alibaba.cloud.ai.dataagent.mcp.config.McpToolProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(McpToolProperties.class)
public class DataAgentMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(DataAgentMcpServerApplication.class);
		application.setApplicationStartup(new BufferingApplicationStartup(4096));
		application.run(args);
	}

}
