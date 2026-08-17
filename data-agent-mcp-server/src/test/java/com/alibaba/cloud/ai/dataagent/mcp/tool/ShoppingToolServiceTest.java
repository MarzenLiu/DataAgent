package com.alibaba.cloud.ai.dataagent.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.dataagent.mcp.repository.BusinessDataSourceConnectionFactory;
import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository.DatasourceSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.sql.SQLTransientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShoppingToolServiceTest {

	private static final String URL = "jdbc:h2:mem:shopping_tools;MODE=MySQL;DB_CLOSE_DELAY=-1";
	private final DatasourceSettings datasource = new DatasourceSettings(1, 2, "test", "h2", "", 0, "",
			"sa", "", URL);

	@BeforeEach
	void setUp() throws Exception {
		try (var connection = DriverManager.getConnection(URL, "sa", ""); var statement = connection.createStatement()) {
			statement.execute("DROP ALL OBJECTS");
			statement.execute("CREATE TABLE users(id INT PRIMARY KEY, username VARCHAR(50))");
			statement.execute("CREATE TABLE products(id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2), stock INT)");
			statement.execute("CREATE TABLE orders(id INT AUTO_INCREMENT PRIMARY KEY, user_id INT, order_date TIMESTAMP, total_amount DECIMAL(10,2), status VARCHAR(20))");
			statement.execute("CREATE TABLE order_items(id INT AUTO_INCREMENT PRIMARY KEY, order_id INT, product_id INT, quantity INT, unit_price DECIMAL(10,2))");
			statement.execute("CREATE TABLE mcp_order_operation(idempotency_key VARCHAR(128) PRIMARY KEY, agent_id BIGINT, user_id INT, product_id INT, quantity INT, order_id INT, state VARCHAR(20), create_time TIMESTAMP, update_time TIMESTAMP)");
			statement.execute("INSERT INTO users VALUES (1, 'alice')");
			statement.execute("INSERT INTO products VALUES (10, '咖啡机', 599.00, 5)");
		}
	}

	@Test
	void createsOrderAtomicallyAndReplaysIdempotently() throws Exception {
		ShoppingToolService service = serviceWithRealConnections();
		String first = service.placeOrder(5, "request-1", 1, 10, 2);
		String replay = service.placeOrder(5, "request-1", 1, 10, 2);
		assertThat(first).contains("\"success\":true", "\"totalAmount\":1198.00", "\"replayed\":false");
		assertThat(replay).contains("\"replayed\":true");
		try (var connection = DriverManager.getConnection(URL, "sa", ""); var statement = connection.createStatement()) {
			assertThat(singleInt(statement, "SELECT COUNT(*) FROM orders")).isEqualTo(1);
			assertThat(singleInt(statement, "SELECT stock FROM products WHERE id = 10")).isEqualTo(3);
		}
	}

	@Test
	void rollsBackRejectedOrder() throws Exception {
		ShoppingToolService service = serviceWithRealConnections();
		assertThatThrownBy(() -> service.placeOrder(5, "request-no-stock", 1, 10, 9))
			.isInstanceOf(ShoppingToolService.OrderRejectedException.class).hasMessageContaining("库存不足");
		try (var connection = DriverManager.getConnection(URL, "sa", ""); var statement = connection.createStatement()) {
			assertThat(singleInt(statement, "SELECT COUNT(*) FROM orders")).isZero();
			assertThat(singleInt(statement, "SELECT COUNT(*) FROM mcp_order_operation")).isZero();
			assertThat(singleInt(statement, "SELECT stock FROM products WHERE id = 10")).isEqualTo(5);
		}
	}

	@Test
	void silentlyRetriesTransientFailuresAtMostThreeTimes() throws Exception {
		BusinessDataSourceConnectionFactory connections = mock(BusinessDataSourceConnectionFactory.class);
		when(connections.require(5)).thenReturn(datasource);
		when(connections.open(datasource)).thenThrow(new SQLTransientException("temporary 1"))
			.thenThrow(new SQLTransientException("temporary 2"))
			.thenAnswer(invocation -> DriverManager.getConnection(URL, "sa", ""));
		ShoppingToolService service = new ShoppingToolService(connections, new ObjectMapper());
		assertThat(service.placeOrder(5, "request-retry", 1, 10, 1)).contains("\"success\":true");
		verify(connections, times(3)).open(datasource);
	}

	@Test
	void reportsRollbackAfterThirdTransientFailure() throws Exception {
		BusinessDataSourceConnectionFactory connections = mock(BusinessDataSourceConnectionFactory.class);
		when(connections.require(5)).thenReturn(datasource);
		when(connections.open(datasource)).thenThrow(new SQLTransientException("temporary"));
		ShoppingToolService service = new ShoppingToolService(connections, new ObjectMapper());
		assertThatThrownBy(() -> service.placeOrder(5, "request-fail", 1, 10, 1))
			.isInstanceOf(ShoppingToolService.OrderPlacementFailedException.class)
			.hasMessageContaining("3 次尝试").hasMessageContaining("已回滚");
		verify(connections, times(3)).open(datasource);
	}

	private ShoppingToolService serviceWithRealConnections() throws Exception {
		BusinessDataSourceConnectionFactory connections = mock(BusinessDataSourceConnectionFactory.class);
		when(connections.require(5)).thenReturn(datasource);
		when(connections.open(datasource)).thenAnswer(invocation -> DriverManager.getConnection(URL, "sa", ""));
		return new ShoppingToolService(connections, new ObjectMapper());
	}

	private int singleInt(java.sql.Statement statement, String sql) throws Exception {
		try (var rs = statement.executeQuery(sql)) { rs.next(); return rs.getInt(1); }
	}
}
