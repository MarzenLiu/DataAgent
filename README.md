# AgentScope DataAgent

DataAgent 是一个基于 AgentScope Java 2 的数据分析 Agent。

运行时分为三个服务：

- `data-agent-agentscope`：AgentScope Harness 推理、会话状态、HITL、技能和流式事件（端口 8066）。
- `data-agent-mcp-server`：官方 MCP Java SDK 数据库与知识检索工具（端口 8068）。
- `data-agent-management`：智能体、数据源、知识、模型配置等管理 API（端口 8065）。

所有服务通过 `data-agent-gateway`（端口 8060）统一对外提供访问，并注册到 Nacos。Gateway
通过 Nacos 服务发现进行负载均衡，不依赖固定的下游地址。

前端通过 `/data-agent-agentscope/api/stream/search` 连接 AgentScope 服务。AgentScope 通过 MCP 调用数据库查询、Schema、语义模型和知识检索工具。知识向量写入由管理服务的应用层实现负责，Milvus 与本地 JSON 两种格式均与 MCP 服务共享。

## 本地启动

```bash
docker compose -f docker-file/docker-compose-nacos.yml up -d

mvn -f data-agent-management/pom.xml spring-boot:run
mvn -f data-agent-mcp-server/pom.xml spring-boot:run
mvn -f data-agent-agentscope/pom.xml spring-boot:run
mvn -f data-agent-gateway/pom.xml spring-boot:run
```

默认需要 MySQL、Milvus 和一个已激活的模型配置。主要环境变量见各模块的 `application.yml`。

应用统一入口为 `http://127.0.0.1:8060`。Gateway 的默认路由如下：

| 请求路径 | 目标服务 |
| --- | --- |
| `/data-agent-agentscope/**` | `data-agent-agentscope` |
| `/data-agent-mcp-server/**` | `data-agent-mcp-server` |
| `/data-agent-management/**` | `data-agent-management` |

Gateway 根据第一段服务名选择目标服务，并在转发前去掉该前缀。例如 `/data-agent-management/api/agent/list` 会转发为管理服务的 `/api/agent/list`。

## Nacos 配置

- 控制台：`http://127.0.0.1:8080`
- 服务端地址：`127.0.0.1:8848`
- 开发环境账号：`nacos`
- 开发环境密码：`dataagent-nacos`
- 配置分组：`DATA_AGENT_GROUP`
- 命名空间：`public`（配置中的空 namespace）
- Data ID：`data-agent-gateway.yml`、`data-agent-management.yml`、
  `data-agent-agentscope.yml`、`data-agent-mcp-server.yml`

首次启动 `docker-compose-nacos.yml` 时，`nacos-init` 会初始化管理员密码并发布上述四份初始配置。
生产环境必须通过 `NACOS_PASSWORD`、`NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY` 和
`NACOS_AUTH_IDENTITY_VALUE` 替换开发默认值。应用侧可通过 `NACOS_SERVER_ADDR`、
`NACOS_USERNAME`、`NACOS_PASSWORD`、`NACOS_NAMESPACE`、`NACOS_CONFIG_GROUP` 和
`NACOS_DISCOVERY_GROUP` 连接其他 Nacos 环境。

## 核心版本

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Nacos Server | 3.2.4 | 最新 GA 服务端 |
| Spring Cloud Alibaba | 2025.0.0.0 | 四个服务统一版本 |
| Spring Cloud Gateway | 4.3.5 | Spring Boot 3.5 兼容线的最新稳定版 |
| Spring Boot | 3.5.16 | 四个服务统一版本 |
| Spring Cloud | 2025.0.0 | 四个服务统一版本 |
| MyBatis Spring Boot Starter | 3.0.5 | Spring Boot 3.5 兼容版本 |

## 架构说明

AgentScope Java 负责所有 Agent 推理和编排。管理服务不再创建聊天模型或图工作流；模型连接测试、会话标题生成也委托给 AgentScope。向量检索通过 MCP 工具进入 AgentScope，避免管理服务与 Agent 运行时耦合。

当前 AgentScope Java 的 RAG API 已标记为弃用，因此文档解析、切分、嵌入和向量写入保留在应用层；检索仍由 AgentScope 通过 MCP 完成。
