# AgentScope Java 迁移说明

## 当前架构

- Agent 推理、ReAct 编排、流式事件、会话状态、HITL、Skills 和模型适配由 `data-agent-agentscope` 承担。
- 数据库查询、Schema 获取和知识检索由 `data-agent-mcp-server` 通过官方 MCP Java SDK 暴露。
- `data-agent-management` 只负责管理 API，以及框架无关的文档解析、切分、Embedding 和向量写入。
- 三个模块均用 Maven Enforcer 禁止旧 AI 框架依赖（含传递依赖）。

## 配置变更

- 管理服务配置前缀由旧前缀改为 `data-agent`。
- AgentScope 地址：`data-agent.agentscope.base-url`，默认 `http://127.0.0.1:8066`。
- 向量存储：`data-agent.vector-store.type=milvus|simple`。
- Elasticsearch 混合检索配置已移除；Milvus 和本地 JSON 是当前受支持的共享存储。

## 无法 1:1 平替的部分

1. 原固定 StateGraph 节点、节点级事件及其时间线不能映射为 AgentScope Harness 的动态 ReAct 迭代；API 已改为 AgentScope 原生运行事件。
2. AgentScope Java 2.0.1 的 RAG API 已标记弃用，因此文档摄取没有强行套用该 API，而是保留为框架无关的应用层代码；检索仍通过 MCP 进入 AgentScope。
3. 原 Elasticsearch + 向量混合检索没有 AgentScope 原生等价实现，当前改为 Milvus/simple 的语义检索。
4. 原独立 Python 执行图没有直接平替。当前 AgentScope 运行时禁用了 Shell/文件系统工具，尚未暴露 Python 沙箱 MCP 工具，因此 Python 深度分析能力暂不可用。
5. 旧模型配置中的逐模型代理字段仍保留在数据结构中，但 AgentScope 2.0.1 的模型 Builder 没有同等的逐模型代理配置入口，目前不会生效。

## 运维注意

- 启动顺序建议为 management、MCP、AgentScope；模型连接测试和会话标题生成要求 AgentScope 服务在线。
- Chat 与 Embedding 模型配置仍来自同一数据库。AgentScope 会按配置指纹自动重建 Chat Agent；Embedding 由管理服务的 OpenAI-compatible 客户端调用。
- 删除管理侧会话会同步删除 AgentScope 文件状态。
