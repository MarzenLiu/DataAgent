package com.alibaba.cloud.ai.dataagent.mcp.tool;

import com.alibaba.cloud.ai.dataagent.mcp.repository.BusinessDataSourceConnectionFactory;
import com.alibaba.cloud.ai.dataagent.mcp.repository.ToolDataRepository.DatasourceSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ShoppingToolService {

	static final int MAX_ORDER_ATTEMPTS = 3;

	private final BusinessDataSourceConnectionFactory connections;
	private final ObjectMapper objectMapper;

	public ShoppingToolService(BusinessDataSourceConnectionFactory connections, ObjectMapper objectMapper) {
		this.connections = connections;
		this.objectMapper = objectMapper;
	}

	public String searchProducts(long agentId, String keyword, BigDecimal minPrice, BigDecimal maxPrice,
			boolean inStockOnly, int requestedLimit) {
		if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0) {
			throw new IllegalArgumentException("商品价格不能为负数");
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new IllegalArgumentException("最低价格不能高于最高价格");
		}
		int limit = Math.min(50, Math.max(1, requestedLimit));
		StringBuilder sql = new StringBuilder("SELECT id, name, price, stock FROM products WHERE 1=1");
		List<Object> parameters = new ArrayList<>();
		if (StringUtils.hasText(keyword)) {
			sql.append(" AND LOWER(name) LIKE ?");
			parameters.add("%" + keyword.trim().toLowerCase() + "%");
		}
		if (minPrice != null) { sql.append(" AND price >= ?"); parameters.add(minPrice); }
		if (maxPrice != null) { sql.append(" AND price <= ?"); parameters.add(maxPrice); }
		if (inStockOnly) sql.append(" AND stock > 0");
		sql.append(" ORDER BY id LIMIT ?");
		parameters.add(limit);

		DatasourceSettings datasource = connections.require(agentId);
		try (Connection connection = connections.open(datasource);
				PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			connection.setReadOnly(true);
			for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
			List<Map<String, Object>> products = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> product = new LinkedHashMap<>();
					product.put("productId", rs.getLong("id"));
					product.put("name", rs.getString("name"));
					product.put("price", rs.getBigDecimal("price"));
					product.put("stock", rs.getInt("stock"));
					products.add(product);
				}
			}
			return json(Map.of("products", products, "count", products.size()));
		}
		catch (SQLException ex) {
			throw new IllegalStateException("查询商品失败，请稍后重试", ex);
		}
	}

	public String placeOrder(long agentId, String idempotencyKey, long userId, long productId, int quantity) {
		if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 128) {
			throw new IllegalArgumentException("idempotencyKey 必须为不超过 128 字符的非空值");
		}
		if (userId <= 0 || productId <= 0 || quantity <= 0) {
			throw new IllegalArgumentException("userId、productId 和 quantity 必须为正数");
		}
		DatasourceSettings datasource = connections.require(agentId);
		SQLException lastFailure = null;
		for (int attempt = 1; attempt <= MAX_ORDER_ATTEMPTS; attempt++) {
			try {
				return json(placeOrderAttempt(datasource, agentId, idempotencyKey.trim(), userId, productId, quantity));
			}
			catch (OrderRejectedException ex) {
				throw ex;
			}
			catch (SQLException ex) {
				lastFailure = ex;
				if (!isRetryable(ex)) {
					throw new OrderPlacementFailedException("下单失败：数据库更改已回滚，请稍后重试。", ex);
				}
				if (attempt == MAX_ORDER_ATTEMPTS) break;
			}
		}
		throw new OrderPlacementFailedException(
				"下单失败：系统在 3 次尝试后仍无法完成订单，数据库更改已回滚，请稍后重试。", lastFailure);
	}

	private Map<String, Object> placeOrderAttempt(DatasourceSettings datasource, long agentId, String idempotencyKey,
			long userId, long productId, int quantity) throws SQLException {
		try (Connection connection = connections.open(datasource)) {
			connection.setAutoCommit(false);
			connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			try {
				Map<String, Object> previous = findCompletedOperation(connection, idempotencyKey, agentId, userId,
						productId, quantity);
				if (previous != null) {
					connection.commit();
					return previous;
				}
				insertOperation(connection, idempotencyKey, agentId, userId, productId, quantity);
				requireUser(connection, userId);
				Product product = lockProduct(connection, productId);
				if (product.stock < quantity) {
					throw new OrderRejectedException("下单失败：商品“%s”库存不足，当前库存 %d。".formatted(product.name, product.stock));
				}
				BigDecimal totalAmount = product.price.multiply(BigDecimal.valueOf(quantity));
				long orderId = insertOrder(connection, userId, totalAmount);
				insertOrderItem(connection, orderId, productId, quantity, product.price);
				decrementStock(connection, productId, quantity);
				completeOperation(connection, idempotencyKey, orderId);
				connection.commit();
				return orderResult(orderId, userId, productId, product.name, product.price, quantity, totalAmount, false);
			}
			catch (SQLException | RuntimeException ex) {
				try { connection.rollback(); }
				catch (SQLException rollbackFailure) { ex.addSuppressed(rollbackFailure); }
				throw ex;
			}
		}
	}

	private Map<String, Object> findCompletedOperation(Connection connection, String key, long agentId, long userId,
			long productId, int quantity) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT agent_id, user_id, product_id, quantity, order_id, state
				FROM mcp_order_operation WHERE idempotency_key = ? FOR UPDATE
				""")) {
			statement.setString(1, key);
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next()) return null;
				if (rs.getLong("agent_id") != agentId || rs.getLong("user_id") != userId
						|| rs.getLong("product_id") != productId || rs.getInt("quantity") != quantity) {
					throw new OrderRejectedException("下单失败：幂等键已用于另一笔不同的订单请求。");
				}
				if (!"COMPLETED".equals(rs.getString("state"))) {
					throw new SQLException("Order operation is not complete", "40001");
				}
				return loadOrderResult(connection, rs.getLong("order_id"), true);
			}
		}
	}

	private void insertOperation(Connection connection, String key, long agentId, long userId, long productId,
			int quantity) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO mcp_order_operation
				(idempotency_key, agent_id, user_id, product_id, quantity, state, create_time, update_time)
				VALUES (?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""")) {
			statement.setString(1, key); statement.setLong(2, agentId); statement.setLong(3, userId);
			statement.setLong(4, productId); statement.setInt(5, quantity); statement.executeUpdate();
		}
	}

	private void requireUser(Connection connection, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE id = ?")) {
			statement.setLong(1, userId);
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next()) throw new OrderRejectedException("下单失败：用户 " + userId + " 不存在。");
			}
		}
	}

	private Product lockProduct(Connection connection, long productId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT name, price, stock FROM products WHERE id = ? FOR UPDATE")) {
			statement.setLong(1, productId);
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next()) throw new OrderRejectedException("下单失败：商品 " + productId + " 不存在。");
				return new Product(rs.getString("name"), rs.getBigDecimal("price"), rs.getInt("stock"));
			}
		}
	}

	private long insertOrder(Connection connection, long userId, BigDecimal totalAmount) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO orders (user_id, order_date, total_amount, status) VALUES (?, CURRENT_TIMESTAMP, ?, 'pending')",
				Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, userId); statement.setBigDecimal(2, totalAmount); statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) throw new SQLException("Order id was not generated");
				return keys.getLong(1);
			}
		}
	}

	private void insertOrderItem(Connection connection, long orderId, long productId, int quantity, BigDecimal price)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)")) {
			statement.setLong(1, orderId); statement.setLong(2, productId); statement.setInt(3, quantity);
			statement.setBigDecimal(4, price); statement.executeUpdate();
		}
	}

	private void decrementStock(Connection connection, long productId, int quantity) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?")) {
			statement.setInt(1, quantity); statement.setLong(2, productId); statement.setInt(3, quantity);
			if (statement.executeUpdate() != 1) throw new SQLException("Stock changed concurrently", "40001");
		}
	}

	private void completeOperation(Connection connection, String key, long orderId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"UPDATE mcp_order_operation SET state = 'COMPLETED', order_id = ?, update_time = CURRENT_TIMESTAMP WHERE idempotency_key = ?")) {
			statement.setLong(1, orderId); statement.setString(2, key); statement.executeUpdate();
		}
	}

	private Map<String, Object> loadOrderResult(Connection connection, long orderId, boolean replayed) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT o.id, o.user_id, o.total_amount, i.product_id, i.quantity, i.unit_price, p.name
				FROM orders o JOIN order_items i ON i.order_id = o.id JOIN products p ON p.id = i.product_id
				WHERE o.id = ?
				""")) {
			statement.setLong(1, orderId);
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next()) throw new SQLException("Completed operation refers to a missing order");
				return orderResult(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("product_id"),
						rs.getString("name"), rs.getBigDecimal("unit_price"), rs.getInt("quantity"),
						rs.getBigDecimal("total_amount"), replayed);
			}
		}
	}

	private Map<String, Object> orderResult(long orderId, long userId, long productId, String productName,
			BigDecimal unitPrice, int quantity, BigDecimal totalAmount, boolean replayed) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true); result.put("orderId", orderId); result.put("status", "pending");
		result.put("userId", userId); result.put("productId", productId); result.put("productName", productName);
		result.put("unitPrice", unitPrice); result.put("quantity", quantity); result.put("totalAmount", totalAmount);
		result.put("replayed", replayed);
		return result;
	}

	private boolean isRetryable(SQLException error) {
		for (SQLException current = error; current != null; current = current.getNextException()) {
			if (current instanceof SQLTransientException) return true;
			String state = current.getSQLState();
			if (state != null && (state.startsWith("08") || "40001".equals(state) || "23505".equals(state))) return true;
			if (current.getErrorCode() == 1062 || current.getErrorCode() == 1205 || current.getErrorCode() == 1213) return true;
		}
		return false;
	}

	private String json(Object value) {
		try { return objectMapper.writeValueAsString(value); }
		catch (JsonProcessingException ex) { throw new IllegalStateException("Failed to serialize tool result", ex); }
	}

	private record Product(String name, BigDecimal price, int stock) { }

	public static class OrderRejectedException extends RuntimeException {
		public OrderRejectedException(String message) { super(message); }
	}

	public static class OrderPlacementFailedException extends RuntimeException {
		public OrderPlacementFailedException(String message, Throwable cause) { super(message, cause); }
	}
}
