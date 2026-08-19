# AgentScope DataAgent

DataAgent 是一个基于 AgentScope Java 2 的数据分析 Agent。

运行时分为三个服务：

- `data-agent-agentscope`：AgentScope Harness 推理、会话状态、HITL、技能和流式事件（端口 8066）。
- `data-agent-mcp-server`：官方 MCP Java SDK 数据库与知识检索工具（端口 8068）。
- `data-agent-management`：智能体、数据源、知识、模型配置等管理 API（端口 8065）。

前端通过 `/api/stream/search` 连接 AgentScope 服务。AgentScope 通过 MCP 调用数据库查询、Schema、语义模型和知识检索工具。知识向量写入由管理服务的应用层实现负责，Milvus 与本地 JSON 两种格式均与 MCP 服务共享。

## 本地启动

```bash
mvn -f data-agent-management/pom.xml spring-boot:run
mvn -f data-agent-mcp-server/pom.xml spring-boot:run
mvn -f data-agent-agentscope/pom.xml spring-boot:run
```

默认需要 MySQL、Milvus 和一个已激活的模型配置。主要环境变量见各模块的 `application.yml`。

## 架构说明

AgentScope Java 负责所有 Agent 推理和编排。管理服务不再创建聊天模型或图工作流；模型连接测试、会话标题生成也委托给 AgentScope。向量检索通过 MCP 工具进入 AgentScope，避免管理服务与 Agent 运行时耦合。

当前 AgentScope Java 的 RAG API 已标记为弃用，因此文档解析、切分、嵌入和向量写入保留在应用层；检索仍由 AgentScope 通过 MCP 完成。
