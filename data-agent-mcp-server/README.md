# DataAgent MCP Server

This standalone Spring Boot service owns the DataAgent tools that access business data. It exposes
the official MCP Streamable HTTP endpoint at `http://127.0.0.1:8068/mcp` and registers:

- `inspect_data_source`
- `execute_read_only_sql`
- `search_knowledge_base`
- `search_products`
- `place_order`

The module uses the official MCP Java SDK WebMVC transport. It contains no Spring AI or Spring AI
Alibaba dependency; Maven Enforcer checks the complete transitive dependency tree.

## Responsibility boundary

Only this service reads datasource associations/credentials, table selections, business terms,
semantic models, embedding configuration, enabled knowledge, and Milvus vectors. SQL is restricted
to a single `SELECT`, `WITH`, or `EXPLAIN` statement, JDBC connections are marked read-only, and
results are capped by `DATA_AGENT_MCP_MAX_QUERY_ROWS`.

`place_order` is the only side-effecting tool. It validates the user and product, locks the product
row, creates the order and item, deducts stock, and records the idempotency key in one database
transaction. Retryable connection/deadlock failures are retried internally for at most three total
attempts; intermediate failures are not returned to the agent. A final failure rolls back the
attempt and is returned as a failed MCP result. The `mcp_order_operation` table makes a completed
request safe to replay with the same key.

`agentId` is injected by the AgentScope client where configured, so the model cannot select another
agent. NL2SQL-only is an AgentScope request mode and is enforced through the conversation's runtime
permissions; it is not sent to MCP or exposed as a tool argument.

## Launch

Start this service before `data-agent-agentscope`:

```bash
export DATA_AGENT_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/saa_data_agent?...'
export DATA_AGENT_DATASOURCE_USERNAME='root'
export DATA_AGENT_DATASOURCE_PASSWORD='root'
mvn -pl data-agent-mcp-server spring-boot:run
```

The HTTP listener binds to `127.0.0.1` by default. Set `DATA_AGENT_MCP_SERVER_ADDRESS` only when a
remote AgentScope deployment must connect, and protect that network path with platform-level
authentication/TLS. RAG/Milvus options use the `DATA_AGENT_MCP_*` environment variables documented
in `src/main/resources/application.yml`.
