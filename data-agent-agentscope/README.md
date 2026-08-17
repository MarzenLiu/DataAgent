# DataAgent AgentScope Backend

This module is an independent implementation of the existing DataAgent streaming endpoint based on
AgentScope Java 2.0.1 and Spring Boot WebFlux. It does not depend on the existing management module,
Spring AI, or Spring AI Alibaba. Business-data tools are discovered and invoked through the
separate `data-agent-mcp-server` process.

## API compatibility

- `GET /api/stream/search` keeps the existing eight query parameters and SSE response fields.
- `POST /api/stream/stop` keeps the existing `conversationId` / optional `threadId` contract.
- SSE terminal event names remain `complete` and `error`.
- Human review pauses before a protected tool executes and resumes the same AgentScope tool call.
- `nl2sqlOnly=true` generates SQL without executing it.

The implementation deliberately replaces the old graph-node workflow with configured AgentScope
Harness agents. Agent behavior is database-driven: `agent.prompt` is the complete system prompt,
while `agent_tool` is the MCP tool whitelist and controls parameter injection, enablement, ordering,
and approval policy. No category-to-agent mapping, tool whitelist, or agent prompt is hard-coded in
the AgentScope service. Configuration changes are fingerprinted and the cached agent is rebuilt on
the next request. Only `agentId` is injected where configured and is not exposed to the model.

There is one cached AgentScope agent per configured agent, independent of request modes. HITL and
NL2SQL-only are applied to the existing conversation's permission context before each new run. The
HITL rejection middleware is always installed but remains inert unless a tool is denied. Switching
HITL therefore does not replace the agent or change the AgentScope session. `nl2sqlOnly` is not an
MCP tool argument; tools with `available_in_nl2sql_only=0` are denied for that run.

The seeded `商品购买智能体` (agent id `5`) configures `search_products` as `ALLOW` and
`place_order` as `ALWAYS_ASK`. The latter automatically enables HITL and pauses for one of the
approval scopes below before the MCP transaction executes.

`agent_tool.approval_mode` supports:

- `ALLOW`: allow the tool when HITL is active.
- `ASK_WHEN_HITL`: ask only when the request explicitly enables HITL.
- `ALWAYS_ASK`: automatically enable HITL for the agent and always ask before the tool runs.

## HITL approval scopes

The existing `humanFeedbackContent` query parameter carries the approval choice, so the request and
response schemas remain unchanged:

- `HITL_APPROVE_ONCE`: approve only the pending tool call.
- `HITL_APPROVE_TOOL_FOR_SESSION`: approve the pending call and allow that tool for the current
  `(agentId, conversationId)` session.
- `HITL_APPROVE_ALL_FOR_SESSION`: approve the pending call and switch only the current session to
  AgentScope `BYPASS` mode. Deny rules and non-bypassable tool safety checks still apply.
- Any content submitted with `rejectedPlan=true` rejects the call and becomes feedback for replanning.

Session permissions are stored in AgentScope `AgentState`; they survive subsequent requests and a
service restart, but do not affect another conversation.

## Configuration and launch

The service defaults to port `8066` so it can run next to the existing backend. It reads only
orchestration-owned records (agent prompt, chat-model configuration, and generated reports) from
the management database. Datasource credentials, schema metadata, SQL execution, embedding model
configuration, knowledge records, and Milvus access belong to the MCP server.

```bash
export DATA_AGENT_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/saa_data_agent?...'
export DATA_AGENT_DATASOURCE_USERNAME='root'
export DATA_AGENT_DATASOURCE_PASSWORD='root'
export DASHSCOPE_API_KEY='...'
export AGENTSCOPE_MCP_URL='http://127.0.0.1:8068/mcp'
mvn -pl data-agent-agentscope spring-boot:run
```

An active `CHAT` row in `model_config` takes precedence over environment model settings. DashScope
and OpenAI-compatible endpoints are supported. See `src/main/resources/application.yml` for all
environment variables.

AgentScope session state is stored under `.agentscope/state` by default. Context compaction starts
at 30 messages and preserves the latest 10; both values are configurable.

## Langfuse tracing

The module uses AgentScope's native OpenTelemetry middleware and exports OTLP/HTTP traces directly
to Langfuse. Each search contains a root request observation with nested AgentScope agent, model,
and tool observations. `conversationId` is mapped to the Langfuse session and `agentId` to the user;
model/tool inputs, outputs, token usage, latency, and failures are recorded.

```bash
export LANGFUSE_ENABLED=true
export LANGFUSE_HOST='http://localhost:13000'
export LANGFUSE_PUBLIC_KEY='pk-lf-...'
export LANGFUSE_SECRET_KEY='sk-lf-...'
export LANGFUSE_ENVIRONMENT='development'
```

`LANGFUSE_HOST` may be a Langfuse host, `/api/public/otel`, or the complete
`/api/public/otel/v1/traces` endpoint. The exporter adds the Langfuse v4 real-time ingestion header.
Tracing is disabled by default and requires no credentials when disabled.

## Dependency isolation

The module uses its own Spring Boot 4.0.4 dependency management to stay aligned with AgentScope
Java 2.0.1's Reactor/Jackson generation. Maven Enforcer fails the build if any `org.springframework.ai`
or Spring AI Alibaba artifact appears, including transitively.
