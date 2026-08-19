# AgentScope DataAgent

DataAgent is a data-analysis agent built on AgentScope Java 2.

The runtime consists of three services:

- `data-agent-agentscope`: AgentScope Harness reasoning, state, HITL, skills, and streaming events (port 8066).
- `data-agent-mcp-server`: database and knowledge tools implemented with the official MCP Java SDK (port 8068).
- `data-agent-management`: management APIs for agents, data sources, knowledge, and model configuration (port 8065).

The frontend connects to AgentScope at `/api/stream/search`. AgentScope invokes database, schema, semantic-model, and knowledge-retrieval tools through MCP. Knowledge-vector ingestion is an application-layer concern and shares either the Milvus or local JSON storage format with the MCP service.

AgentScope Java owns all agent reasoning and orchestration. Its current RAG API is deprecated, so parsing, splitting, embedding, and vector writes remain framework-neutral application code while retrieval is exposed to AgentScope through MCP.
