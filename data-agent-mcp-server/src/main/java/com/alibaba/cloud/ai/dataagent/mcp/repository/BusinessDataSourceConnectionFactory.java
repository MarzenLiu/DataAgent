package com.alibaba.cloud.ai.dataagent.mcp.repository;

import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository.DatasourceSettings;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BusinessDataSourceConnectionFactory {

	private final ToolDataRepository repository;

	public BusinessDataSourceConnectionFactory(ToolDataRepository repository) {
		this.repository = repository;
	}

	public DatasourceSettings require(long agentId) {
		return repository.findActiveDatasource(agentId)
			.orElseThrow(() -> new IllegalStateException("No active datasource is configured for agent " + agentId));
	}

	public Connection open(DatasourceSettings datasource) throws SQLException {
		return DriverManager.getConnection(resolveUrl(datasource), datasource.username(), datasource.password());
	}

	public String schema(DatasourceSettings datasource) {
		String database = value(datasource.databaseName());
		return database.contains("|") ? database.split("\\|", 2)[1] : null;
	}

	private String resolveUrl(DatasourceSettings datasource) {
		if (StringUtils.hasText(datasource.connectionUrl())) return datasource.connectionUrl();
		String type = value(datasource.type()).toLowerCase(Locale.ROOT);
		String database = value(datasource.databaseName()).split("\\|", 2)[0];
		return switch (type) {
			case "mysql", "mysql-vpc" -> "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai".formatted(datasource.host(), datasource.port(), database);
			case "postgresql", "postgresql-vpc", "hologress" -> "jdbc:postgresql://%s:%d/%s".formatted(datasource.host(), datasource.port(), database);
			case "h2" -> "jdbc:h2:tcp://%s:%d/%s".formatted(datasource.host(), datasource.port(), database);
			case "sqlite" -> "jdbc:sqlite:" + database;
			default -> throw new IllegalStateException("Datasource type requires an explicit connection_url: " + type);
		};
	}

	private String value(String value) { return value == null ? "" : value; }
}
