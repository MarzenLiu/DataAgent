# 逻辑模块: graph

## 模块描述

通过 SSE 消费 AgentScope 原生 `AgentEvent`。请求使用 `runId` 恢复暂停的 HITL 运行，
`confirmation` 只表达批准范围或拒绝，不携带拒绝意见。

前端直接处理 `TEXT_BLOCK_DELTA`、`TOOL_CALL_START`、`TOOL_RESULT_END`、
`REQUIRE_USER_CONFIRM` 和 `AGENT_RESULT`。未知 AgentScope 事件会被忽略。

服务端生命周期通过 `CUSTOM` 事件传递：`run_started`、`stream_completed`、
`stream_error`。本模块不再定义或解析节点名、step、textType 或时间线块。
