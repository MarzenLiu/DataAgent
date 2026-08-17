# Langfuse 本地测试环境

> 仅供本地测试使用，所有凭据均为明文。

## Langfuse

- Web 地址：http://localhost:13000
- 登录邮箱：`admin@dataagent.local`
- 登录密码：`12345678`
- 组织：`DataAgent Local`
- 项目：`DataAgent`
- Public Key：`pk-lf-dataagent-local`
- Secret Key：`sk-lf-dataagent-local-20260731`

DataAgent 环境变量：

```bash
export LANGFUSE_ENABLED=true
export LANGFUSE_HOST=http://localhost:13000
export LANGFUSE_PUBLIC_KEY=pk-lf-dataagent-local
export LANGFUSE_SECRET_KEY=sk-lf-dataagent-local-20260731
```

## 启动 DataAgent

DataAgent、Langfuse 和本地 MySQL 测试变量已写入用户级 `~/.zshrc`，新终端中直接使用正常的 Maven 或 IDE 启动方式即可，无需专用启动脚本。

DataAgent 地址：http://localhost:8065

AgentScope 独立服务复用相同的四个 `LANGFUSE_*` 环境变量，默认地址为
http://localhost:8066：

```bash
mvn -pl data-agent-agentscope spring-boot:run
```

在 Langfuse 中，AgentScope 请求显示为 `data-agent.stream-search`，下层包含
`invoke_agent`、模型 `generation` 和工具调用；`conversationId` 对应 Session，
`agentId` 对应 User。

本地 DataAgent MySQL 连接账号：

```text
用户名：data_agent
密码：data_agent
数据库：saa_data_agent
端口：13307
```

应用启动日志出现以下内容即表示追踪导出器已启用：

```text
OpenTelemetry initialized with Langfuse OTLP HTTP exporter
```

发起 DataAgent 请求后，在 Langfuse 的 `DataAgent` 项目中查看 Traces。每次模型调用会记录：

- 节点/Agent 名称
- 完整 prompt 输入（`input.value`）
- 完整模型输出（`output.value`）
- Token 用量、耗时及异常

注意：只有实际执行到的模型调用才会产生记录；普通健康检查、数据库查询等非 LLM 操作不会生成 prompt 记录。流式响应结束后，数据可能需要数秒才会出现在界面中。

## ClickHouse

- HTTP：http://127.0.0.1:8123
- Native：`127.0.0.1:9000`
- 数据库：`default`
- 用户名：`langfuse`
- 密码：`f183448a1ba84cd87562d72d4166403bd29f682e77f37f9c`

## PostgreSQL

- 地址：`127.0.0.1:5432`
- 数据库：`langfuse`
- 用户名：`langfuse`
- 密码：`9c5f22fee71f2fdcb2b96aa689cfacbdcd27cba7c1216d2a`

## MinIO

- API：http://127.0.0.1:9090
- 控制台：http://127.0.0.1:9091
- 用户名：`langfuse`
- 密码：`fb4eaa07f8c814c68e8d3eee578b7c8c82feab426c519446`

## Redis

- 地址：`127.0.0.1:6379`
- 密码：`57006689f6028ab84d8c887d9a7c5175f685c883b0a24563`

## Docker Compose

目录：`/Users/liuchao/.local/share/langfuse-local`

```bash
cd /Users/liuchao/.local/share/langfuse-local
docker compose up -d
docker compose down
```
