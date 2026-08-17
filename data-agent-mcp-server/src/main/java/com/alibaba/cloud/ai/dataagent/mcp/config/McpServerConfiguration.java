package com.alibaba.cloud.ai.dataagent.mcp.config;

import com.alibaba.cloud.ai.dataagent.mcp.rag.KnowledgeRetrievalToolService;
import com.alibaba.cloud.ai.dataagent.mcp.tool.DataQueryToolService;
import com.alibaba.cloud.ai.dataagent.mcp.tool.ShoppingToolService;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class McpServerConfiguration {

	private static final ToolAnnotations READ_ONLY = new ToolAnnotations(null, true, false, true, false, false);
	private static final ToolAnnotations SIDE_EFFECTING = new ToolAnnotations(null, false, true, true, false, false);

	@Bean
	WebMvcStreamableServerTransportProvider mcpTransport(McpToolProperties properties) {
		return WebMvcStreamableServerTransportProvider.builder()
				.jsonMapper(McpJsonMapper.getDefault()).mcpEndpoint(properties.getEndpoint())
				.keepAliveInterval(Duration.ofSeconds(30)).build();
	}

	@Bean
	RouterFunction<ServerResponse> mcpRouter(WebMvcStreamableServerTransportProvider transport) {
		return transport.getRouterFunction();
	}

	@Bean(destroyMethod = "closeGracefully")
	McpSyncServer mcpServer(WebMvcStreamableServerTransportProvider transport, DataQueryToolService queryTools,
			ShoppingToolService shoppingTools, Optional<KnowledgeRetrievalToolService> knowledgeTools,
			McpToolProperties properties) {
		List<SyncToolSpecification> tools = new ArrayList<>();
		tools.add(spec(inspectSchema(), args -> queryTools.inspectDataSource(longArg(args, "agentId"))));
		tools.add(spec(executeSchema(), args -> queryTools.executeReadOnlySql(longArg(args, "agentId"),
				stringArg(args, "sql"))));
		tools.add(spec(searchProductsSchema(), args -> shoppingTools.searchProducts(longArg(args, "agentId"),
				optionalStringArg(args, "keyword"), decimalArg(args, "minPrice"), decimalArg(args, "maxPrice"),
				booleanArg(args, "inStockOnly"), intArg(args, "limit", 20))));
		tools.add(spec(placeOrderSchema(), args -> shoppingTools.placeOrder(longArg(args, "agentId"),
				stringArg(args, "idempotencyKey"), longArg(args, "userId"), longArg(args, "productId"),
				intArg(args, "quantity", 1))));
		if (properties.getRag().isEnabled()) {
			KnowledgeRetrievalToolService rag = knowledgeTools.orElseThrow();
			tools.add(spec(searchSchema(), args -> rag.search(longArg(args, "agentId"), stringArg(args, "query"))));
		}
		return McpServer.sync(transport).serverInfo("data-agent-tools", "1.1.0")
				.instructions("Tools for configured DataAgent business data, knowledge, product search, and transactional ordering.")
				.capabilities(ServerCapabilities.builder().tools(false).build()).tools(tools).build();
	}

	private SyncToolSpecification spec(Tool tool, Function<Map<String, Object>, String> callback) {
		return new SyncToolSpecification(tool, (exchange, arguments) -> {
			try { return new CallToolResult(List.of(new TextContent(callback.apply(arguments))), false); }
			catch (RuntimeException ex) { return new CallToolResult(List.of(new TextContent(rootMessage(ex))), true); }
		});
	}

	private Tool inspectSchema() {
		return Tool.builder().name("inspect_data_source")
				.description("Inspect the active datasource schema, selected tables, business terms, and semantic column definitions before writing SQL.")
				.inputSchema(schema(Map.of("agentId", property("integer", "Current DataAgent id")), List.of("agentId")))
				.annotations(READ_ONLY).build();
	}

	private Tool executeSchema() {
		return Tool.builder().name("execute_read_only_sql")
				.description("Execute exactly one read-only SELECT/WITH/EXPLAIN statement against the active datasource. Results are capped by the server row limit.")
				.inputSchema(schema(Map.of(
						"agentId", property("integer", "Current DataAgent id"),
						"sql", property("string", "A single read-only SQL statement")),
						List.of("agentId", "sql"))).annotations(READ_ONLY).build();
	}

	private Tool searchSchema() {
		return Tool.builder().name("search_knowledge_base")
				.description("Semantically search the current agent's enabled document, QA, and FAQ knowledge before answering configured business knowledge questions.")
				.inputSchema(schema(Map.of("agentId", property("integer", "Current DataAgent id"),
						"query", property("string", "A concise standalone semantic search query")), List.of("agentId", "query")))
				.annotations(READ_ONLY).build();
	}

	private Tool searchProductsSchema() {
		return Tool.builder().name("search_products")
				.description("Search real products by name and optional price range. Returns product id, current price, and current stock.")
				.inputSchema(schema(Map.of(
						"agentId", property("integer", "Current shopping agent id"),
						"keyword", property("string", "Optional product name keyword"),
						"minPrice", property("number", "Optional minimum price"),
						"maxPrice", property("number", "Optional maximum price"),
						"inStockOnly", property("boolean", "Only return products with stock; defaults to true"),
						"limit", property("integer", "Maximum results, 1 to 50; defaults to 20")),
						List.of("agentId"))).annotations(READ_ONLY).build();
	}

	private Tool placeOrderSchema() {
		return Tool.builder().name("place_order")
				.description("Create one pending order and deduct stock in one transaction. The server silently retries transient failures up to three total attempts and rolls back a failed attempt. Never call again after an error. Requires explicit user confirmation before invocation.")
				.inputSchema(schema(Map.of(
						"agentId", property("integer", "Current shopping agent id"),
						"idempotencyKey", property("string", "A unique stable request id; reuse it for the exact same order intent"),
						"userId", property("integer", "Existing user id placing the order"),
						"productId", property("integer", "Product id returned by search_products"),
						"quantity", property("integer", "Positive purchase quantity")),
						List.of("agentId", "idempotencyKey", "userId", "productId", "quantity")))
				.annotations(SIDE_EFFECTING).build();
	}

	private JsonSchema schema(Map<String, Object> properties, List<String> required) {
		return new JsonSchema("object", properties, required, false, null, null);
	}
	private Map<String, Object> property(String type, String description) {
		return Map.of("type", type, "description", description);
	}
	private long longArg(Map<String, Object> args, String name) {
		Object value = args.get(name);
		if (value instanceof Number number) return number.longValue();
		try { return Long.parseLong(String.valueOf(value)); }
		catch (RuntimeException ex) { throw new IllegalArgumentException(name + " must be numeric", ex); }
	}
	private boolean booleanArg(Map<String, Object> args, String name) {
		Object value = args.get(name);
		if (value == null && "inStockOnly".equals(name)) return true;
		return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
	}
	private int intArg(Map<String, Object> args, String name, int defaultValue) {
		Object value = args.get(name);
		if (value == null) return defaultValue;
		if (value instanceof Number number) return number.intValue();
		try { return Integer.parseInt(value.toString()); }
		catch (RuntimeException ex) { throw new IllegalArgumentException(name + " must be an integer", ex); }
	}
	private BigDecimal decimalArg(Map<String, Object> args, String name) {
		Object value = args.get(name);
		if (value == null || value.toString().isBlank()) return null;
		try { return new BigDecimal(value.toString()); }
		catch (RuntimeException ex) { throw new IllegalArgumentException(name + " must be numeric", ex); }
	}
	private String optionalStringArg(Map<String, Object> args, String name) {
		Object value = args.get(name);
		return value == null ? null : value.toString();
	}
	private String stringArg(Map<String, Object> args, String name) {
		Object value = args.get(name);
		if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(name + " is required");
		return value.toString();
	}
	private String rootMessage(Throwable error) {
		return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
	}
}
