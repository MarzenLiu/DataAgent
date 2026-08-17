package com.alibaba.cloud.ai.dataagent.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.dataagent.mcp.config.McpToolProperties;
import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository;
import com.alibaba.cloud.ai.dataagent.mcp.repository.BusinessDataSourceConnectionFactory;
import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository.DatasourceSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataQueryToolServiceTest {
	private DataQueryToolService service;

	@BeforeEach
	void setUp() throws Exception {
		String url = "jdbc:h2:mem:mcp_tools;DB_CLOSE_DELAY=-1";
		try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS SALES");
			statement.execute("CREATE TABLE SALES(ID INT PRIMARY KEY, AMOUNT DECIMAL(10,2))");
			statement.execute("INSERT INTO SALES VALUES (1, 12.50)");
		}
		ToolDataRepository repository = mock(ToolDataRepository.class);
		when(repository.findActiveDatasource(7L)).thenReturn(Optional.of(
				new DatasourceSettings(1, 2, "test", "h2", "", 0, "", "sa", "", url)));
		when(repository.findSelectedTables(1)).thenReturn(List.of("SALES"));
		when(repository.findBusinessContext(7L, 2)).thenReturn("销售口径");
		service = new DataQueryToolService(repository, new BusinessDataSourceConnectionFactory(repository),
				new McpToolProperties(), new ObjectMapper());
	}

	@Test void inspectsAndQueriesThroughToolService() {
		assertThat(service.inspectDataSource(7L)).contains("SALES", "AMOUNT", "销售口径");
		assertThat(service.executeReadOnlySql(7L, "SELECT * FROM SALES")).contains("12.50", "rowCount");
	}
}
