# AI Agent 开发面试 QA 与项目复盘

> 本文根据当前会话中的问答、项目代码检查和补充讨论整理。它不是对用户原回答的逐字转录，而是将“原回答要点、存在的不足、补充后的参考回答”合并为一份可复习、可用于面试表达的材料。

## 一、会话背景与当前能力画像

本次会话最初围绕简历和 DataAgent 项目展开，主要完成了以下工作：

- 在简历中补充最近工作经历，以及“东莞农商银行信贷管理系统—零售授信模块”。
- 将当前 DataAgent 作为个人项目写入简历，并避免过度夸大。
- 补充 AI Agent 开发能力，包括 AgentScope Java、HarnessAgent、StateGraph、ReAct、MCP、RAG、Memory、WebFlux/SSE、HITL 和可观测性。
- 明确 Nuxt 只是当前项目已有的前端技术栈，不应包装成个人熟练掌握的能力。
- 通过连续问答检查 Agent 开发知识，并针对回答中的薄弱点补充完整。
- 检查 AgentScope 2.0.1 和当前项目的会话持久化实现。
- 讨论 Tool 返回格式、公开 MCP Server 的设计差异，以及子 Agent 的适用场景和治理问题。

当前比较扎实的部分：

- 能解释固定 Graph 与 ReAct/HarnessAgent 的核心差异。
- 能结合 DataAgent 说明为什么从单任务固定流程迁移到更自由的 Agent Loop。
- 理解 MCP、Function Calling、Memory、会话历史和 RAG 的基本边界。
- 对无限循环、工具失败、超时未知结果、SSE 取消和 HITL 已建立工程化意识。

仍需重点深入的部分：

- 将概念落实为完整状态机、数据结构和恢复链路。
- 对幂等、未知执行结果、补偿事务、重试风暴等分布式问题形成统一设计。
- 深入理解 AgentScope 的 Checkpoint、StateStore、PermissionContext 和子 Agent 生命周期。
- 为 Agent 构建评测体系，而不仅依靠手工体验判断效果。

---

## 二、核心 QA

### Q1：StateGraph 和 ReAct 分别适合什么场景？

### 原回答要点

- StateGraph 是固定编排流程，由 Node、Edge 和 Dispatcher/路由组成。
- Node 可以是 LLM 调用、本地方法或远程方法。
- StateGraph 适合执行路径明确、流程固定的业务。
- ReAct 是 Reasoning + Acting，核心是推理和工具调用循环。
- ReAct 更自由，但执行路线有概率性，可能出现死循环。
- 可以通过最大 Loop 次数避免无限执行。

### 回答中的不足

- “StateGraph 不支持多轮问答”不是框架的必然限制，更准确地说，是原项目的 Graph 按单次任务设计，没有建立跨轮状态和 Checkpoint。
- Graph 不一定自由度低。Graph 的某个节点也可以是 ReAct Agent，因此可以采用 Graph + ReAct 的混合设计。
- ReAct 的风险不只有死循环，还包括路径漂移、重复工具调用、成本不可控和难以复现。

### 综合参考回答

StateGraph 适合执行路径相对明确、需要强约束和可审计的业务流程。它通过节点、边和条件路由显式表达状态变化，例如：

```text
需求理解 → 元数据准备 → SQL 生成 → SQL 审核 → 查询执行 → 报表生成
```

它的优势是流程可视、节点职责清晰、容易加入人工确认和失败补偿；不足是分支增加后 Graph 容易复杂化，对开放式追问和临时调整不够自然。

ReAct 适合任务路径无法预先穷举、需要模型根据现场信息动态选择工具的场景。例如用户可能连续提出查数据、修改口径、补充图表等要求，Agent 可以根据当前上下文决定下一步调用哪个 Tool。

ReAct 的优势是灵活和多轮体验自然；不足是执行路径具有不确定性，需要增加最大轮次、时间、Token、费用、工具调用次数、重复调用检测和无进展检测等保护。

二者不是互斥关系。严格业务流程可以使用 StateGraph 作为外层控制，在某个开放式节点内使用 ReAct；也可以由 ReAct 主 Agent 调用一个固定 Graph 完成高风险流程。

---

### Q2：为什么从固定 Graph 迁移到 HarnessAgent？

### 原回答要点

- 原 Graph 对多轮追问和微调支持较弱。
- 用户每次追问都可能重新走完整 Graph，部分节点已经没有必要。
- 为 Graph 增加多轮和微调能力会显著增加复杂度。
- HarnessAgent 在配置 System Prompt 和 Tool List 后，也能完成数据报表任务，交互体验更自然。

### 回答中的不足

- 不能表述为“StateGraph 本身不支持多轮”，应限定为“原 Graph 按一次性任务设计”。
- 迁移理由除了交互体验，还应包含维护成本、能力扩展、工具复用和状态管理方式。
- 需要承认迁移后的代价：确定性降低，需要重新建设权限、循环控制、持久化、评测和可观测性。

### 综合参考回答

原 DataAgent Graph 更适合一次性生成报表：用户输入问题后，系统依次完成意图识别、元数据准备、SQL 生成、执行和可视化。但用户进入多轮追问后，可能只希望修改时间范围、增加一个指标或更换图表，原 Graph 仍可能重新执行不必要的节点。

虽然可以继续为 Graph 增加状态判断、跳转和恢复节点，但随着追问类型增加，分支和状态组合快速膨胀，维护成本提高。

迁移到 HarnessAgent 后，系统将数据库查询、知识检索、图表生成等能力暴露为 Tool，由 Agent 根据会话状态决定调用顺序，能够更自然地处理追问和局部调整。同时可以复用 HarnessAgent 提供的 Memory、Compaction、Permission、MCP 和 StateStore 能力。

迁移并不是简单地认为 Agent 一定优于 Graph，而是在当前场景下用更灵活的交互模型替代过度复杂的流程分支。相应地，需要补充最大 Loop、Tool 权限、HITL、持久化、错误分类和评测机制。对必须严格执行的流程，仍然可以保留固定 Graph 或封装成 Tool。

---

### Q3：MCP 和普通 Function Calling 有什么区别？

### 原回答要点

- MCP 可以向 Agent 暴露 Tool、Resource 等外部能力。
- Function Calling 是模型返回工具调用请求，Agent Runtime 根据工具定义执行本地或远程方法。
- MCP Server 可以使用不同语言实现，适合分布式、多语言系统。
- MCP 引入远程依赖后会带来网络、黑盒执行和安全风险。

### 回答中的不足

- MCP 标准原语通常表述为 Tools、Resources、Prompts，不应把 Skill 直接说成 MCP 的标准原语。
- MCP 和 Function Calling 不在同一个层次：Function Calling 是模型与宿主运行时的调用机制；MCP 是宿主与外部能力提供方之间的标准协议。
- MCP 不只解决“远程调用”，也包含能力发现、Schema、传输、生命周期、资源读取和协议协商。

### 综合参考回答

Function Calling 主要描述模型与 Agent Runtime 的交互：模型根据工具 Schema 生成工具名和参数，Runtime 执行对应方法，然后把结果返回模型。

```text
LLM ← Function Calling → Agent Runtime
```

MCP 主要描述 Agent Runtime 如何发现和调用外部能力：MCP Client 连接 MCP Server，通过统一协议获取 Tools、Resources 和 Prompts，并调用具体工具。

```text
LLM ← Function Calling → Agent Runtime/MCP Client ← MCP → MCP Server
```

因此，MCP Tool 可以成为 Function Calling 的工具来源之一，本地 Java Tool、硬编码 Tool 和其他插件机制也可以是工具来源。

MCP 的价值是标准化能力发现和接入，降低语言与框架耦合；代价是引入认证、权限、网络超时、协议版本、工具命名冲突、结果可信度和供应链安全问题。

---

### Q4：Memory、会话历史和 RAG 知识有什么区别？

### 原回答要点

- Memory 分为长期记忆和短期记忆。
- 长期记忆保存客观事实或用户要求记住的信息；短期记忆保存会话中的重要信息。
- 会话历史是同一会话的上下文，过长时需要压缩。
- RAG 用于召回模型未知的私有知识，并作为上下文提供给模型。
- 可以通过固定 RAG、Agentic RAG 或混合方式提供知识。

### 回答中的不足

- Redis、数据库等是存储介质，不是 Memory 类型的定义依据。
- 会话摘要是会话历史的压缩表示，不应自动等同于长期记忆。
- 长期 Memory 通常来自用户偏好、稳定事实和跨会话任务状态；RAG 知识来自外部文档或业务知识库，两者来源和生命周期不同。
- 不应每轮无条件携带完整历史，应根据上下文窗口、相关性和成本进行截断或压缩。

### 综合参考回答

三者的核心区别如下：

| 类型 | 主要来源 | 生命周期 | 主要作用 | 示例 |
|---|---|---|---|---|
| 会话历史 | 当前会话消息和工具结果 | 当前会话 | 保持多轮连续性 | 用户刚刚把时间范围改成 2026 年 |
| Memory | 从交互中提炼的稳定事实、偏好或任务状态 | 可跨会话 | 个性化和长期连续性 | 用户偏好中文回答、默认使用含税金额 |
| RAG 知识 | 外部文档、数据库、制度和知识库 | 独立于会话 | 补充领域事实 | 指标口径文档、银行授信制度 |

会话过长时，可以保留最近若干轮消息，并将更早历史压缩为摘要。压缩会损失细节，也可能影响 Prompt Cache，因此应保留关键约束、未完成事项、重要工具结果引用和用户最新纠正。

Memory 可以由用户主动要求保存，也可以由系统在适当时机提炼，但自动写入长期记忆需要过滤临时信息、敏感信息和未经确认的推断。

RAG 则通过检索将外部知识按需召回。固定的重要规则可以预加载，长尾知识可以暴露为检索 Tool 让 Agent 按需查询。

---

### Q5：Agent 如何避免无限循环？

### 原回答要点

- 设置最大 Loop 次数。
- 对部分 Tool 设置调用次数限制。
- 用户可以观察 Tool 调用过程并主动取消。
- 达到限制后输出已完成内容，引导用户细化问题或基于已有进展继续。

### 回答中的不足

- 只有最大轮次限制属于“强行停止”，不能识别低质量循环。
- 需要识别重复调用、状态无变化、工具相互循环和持续失败。
- 不应向用户暴露模型内部完整思维链，可以展示任务步骤、工具调用和可审计摘要。

### 综合参考回答

无限循环通常来源于目标不明确、工具持续失败、返回信息不足、重复获得相同结果，或者模型一直认为任务尚未完成。

防护应包含多层预算：

- 最大推理轮次。
- 最大执行时间。
- 最大 Token 和费用。
- 全局及单 Tool 最大调用次数。
- 连续失败次数。
- 重复工具名和相同参数检测。
- 状态无变化检测。
- 调用链环路检测，例如 A → B → A → B。

达到限制后，不应只返回“执行失败”，而应输出结构化的部分完成结果：

```json
{
  "status": "PARTIAL",
  "completed": ["已识别相关表", "已生成候选 SQL"],
  "blockedBy": "查询工具连续超时",
  "retryable": true,
  "nextAction": "确认数据源状态后继续执行"
}
```

同时可以允许用户取消，但取消是最后保护措施，不能代替 Runtime 自身的循环检测。

---

### Q6：工具调用失败、重复或超时时如何处理？

### 原回答要点

- Tool 需要返回执行结果、状态和明确的错误信息。
- 不同 Tool 可以配置不同重试次数，同时提供全局默认值。
- 有副作用的 Tool 应尽量幂等；非幂等 Tool 不能盲目重复执行。
- 远程调用超时不代表执行失败，可能只是客户端没有收到结果。
- 最终失败时应明确告诉模型失败原因，由模型组织用户可读信息。

### 回答中的不足

- 需要区分瞬时错误和永久错误。
- 重试应使用指数退避和随机抖动，并避免框架、HTTP Client 和 Tool 三层同时重试。
- 超时后的核心不是立即“回调”，而是判断执行结果是否已知；未知时优先通过幂等键或状态查询确认。
- 模型可见错误和日志中的内部异常需要分离并脱敏。

### 综合参考回答

首先对错误分类：

```text
TRANSIENT：网络抖动、限流、临时不可用，可重试
PERMANENT：参数错误、权限不足、业务规则失败，不应原样重试
UNKNOWN_OUTCOME：请求超时，但不能确认远程操作是否成功
```

无副作用的只读 Tool 可以按策略重试；写操作必须携带稳定的幂等键。发生超时时，应优先使用相同幂等键重试或调用状态查询接口，避免创建第二次业务操作。

重试策略应由一个层级统一负责，采用最大次数、指数退避、抖动和熔断。最终失败后向模型返回安全、可决策的信息，详细堆栈、连接地址、SQL 和密钥只写入日志。

---

### Q7：SSE 如何取消正在执行的 Agent？

### 原回答要点

- WebFlux 支持 Cancel Signal。
- 可以通过会话标识找到正在执行的流并发出取消信号。
- 取消到 Agent 停止有时间差，Agent 需要在 Loop 边界检查取消状态。
- HTTP Tool 即使连接中断，远程执行方也可能已经完成操作。
- 如果已产生副作用，需要补偿或修正。

### 回答中的不足

- 不应把 `Flux` 实例本身作为主要运行句柄长期缓存，应该维护 `runId → CancellationHandle/Disposable`。
- `sessionId` 粒度过大，同一会话可能存在多个 Run，应至少使用 `conversationId + runId`。
- SSE 断开不一定等于用户明确取消，应根据产品策略区分连接中断和任务取消。
- “回滚所有 Tool”通常不可行，应使用幂等、状态查询和针对性补偿。

### 综合参考回答

每次 Agent 执行应创建独立 `runId`，并注册取消句柄：

```text
conversationId + runId → CancellationHandle
```

用户调用取消接口时：

1. 将 Run 状态原子更新为 `CANCEL_REQUESTED`。
2. 触发 Reactor 订阅取消或取消令牌。
3. Agent Loop、模型流式请求和本地 Tool 在安全点检测取消状态。
4. 将尚未执行的 Tool 阻止在调用前。
5. 对远程调用尽量传播取消，但不能假定远程业务已经撤销。
6. 最终记录 `CANCELLED`、`CANCELLED_WITH_SIDE_EFFECTS` 或 `UNKNOWN_OUTCOME`。

对已产生的副作用，应根据 Tool 类型执行状态查询或补偿操作，而不是做通用“全量回滚”。

---

### Q8：HITL 恢复后如何继续原工具调用？

### 原回答要点

- 暂停时需要持久化会话历史。
- Tool Call 有自己的 ID 和 Metadata，可以记录调用者、Tool 和参数。
- 通过 conversationId 找到原会话继续。
- 可以按 conversation + tool 或 conversation 维度批准。
- 如果已持久化，应用重启后可以恢复。

### 回答中的不足

- 只保存会话历史不够，还需要保存 Agent Checkpoint、Run、ToolCall、权限上下文和暂停原因。
- 不能只依赖 conversationId，应使用 `conversationId + runId + toolCallId` 精确定位。
- 恢复不是重新生成一次 Tool Call，而是向原调用写入确认结果并从 Checkpoint 继续。
- 需要考虑审批范围、过期、参数是否改变以及幂等问题。

### 综合参考回答

暂停时至少持久化：

```text
conversationId
runId
agentState/checkpoint
toolCallId
toolName
toolArguments
toolCallState = ASKING
permissionContext
approvalScope
createdAt / expiresAt
```

用户确认后：

1. 使用 `conversationId + runId + toolCallId` 定位原调用。
2. 校验用户身份、审批范围、参数摘要和审批是否过期。
3. 将 `ConfirmResult` 写入原 Tool Call。
4. 从保存的 Agent Checkpoint 恢复执行。
5. Tool 使用同一 `toolCallId` 或幂等键，防止重复副作用。
6. 执行完成后更新 ToolCall 和 Run 状态。

批准整个会话风险较高，默认应优先批准单次 Tool Call 或明确的 Tool + 参数范围。

---

### Q9：AgentScope 有没有默认持久化？当前项目使用什么？

### 检查结论

AgentScope 2.0.1 中需要区分两类 Agent：

- 普通 `ReActAgent`：只有显式配置 `AgentStateStore` 才会持久化；未配置时为内存状态。
- `HarnessAgent`：未显式配置 StateStore 时，会默认使用 `JsonFileAgentStateStore`。

默认文件位置为：

```text
~/.agentscope/state/<agentId>/
```

其中可能包含：

```text
agent_state.json
memory_messages.jsonl
memory_messages.hash
```

可以通过 JVM 属性修改根目录：

```text
agentscope.state.home
```

### 当前 DataAgent 配置

当前项目没有使用 HarnessAgent 默认的 JSON 文件存储，而是显式配置了：

```java
new MysqlAgentStateStore(
    dataSource,
    currentDatabase,
    "agentscope_sessions",
    true
)
```

最后一个 `true` 表示允许自动创建表。该 StateStore 被传入 HarnessAgent：

```java
.stateStore(stateStore)
```

运行时使用：

```text
userId = "1"
sessionId = conversationId
```

MySQL StateStore 最终使用类似下面的 Slot：

```text
1:<conversationId>
```

主要状态 Key 为：

```text
agent_state
```

长期 Memory/Workspace 是另一套机制，由 `DatabaseWorkspaceStore` 和 `agent_workspace_store` 表承载，不应与会话 AgentState 混为一谈。

### 检查中发现的潜在问题

当前删除 Session State 的逻辑使用：

```text
data-agent-<agentId>:<sessionId>
```

但运行时实际 userId 是固定的 `"1"`，实际 Slot 更可能是：

```text
1:<sessionId>
```

因此删除接口可能无法删除真实的会话状态，需要统一 userId 的生成逻辑并增加集成测试。

---

### Q10：不同来源、不同规格的 Tool 是否需要统一输出格式？

### 讨论结论

需要统一，但统一的是控制面和错误语义，不是所有业务数据结构。

不同 Tool 的业务结果天然不同：

- SQL Tool 返回字段、行和分页信息。
- 文件 Tool 返回路径、大小或资源引用。
- 搜索 Tool 返回文档列表。
- 写操作返回业务 ID 和状态。

可以统一最小外层协议：

```json
{
  "status": "SUCCESS",
  "code": "OK",
  "message": "查询完成",
  "data": {},
  "retryable": false,
  "outcomeKnown": true,
  "toolCallId": "call_xxx"
}
```

但是 `data` 应保留 Tool 自己的 Schema，不能为了形式统一而抹平业务语义。

对于无法控制的第三方或 MCP Tool，应增加适配层：

```text
MCP/第三方原始结果
        ↓
ToolResultAdapter
        ↓
Agent 内部统一 ToolResult
        ↓
模型可见结果 / 用户可见结果 / 内部日志
```

不建议直接复用 HTTP 状态码作为 Agent Tool 错误分类。更适合使用：

```text
INVALID_ARGUMENT
PERMISSION_DENIED
RATE_LIMITED
UPSTREAM_TIMEOUT
UNKNOWN_OUTCOME
BUSINESS_REJECTED
```

---

### Q11：MCP 对 Tool 输出有没有相关标准设计？

### 综合参考回答

MCP 统一了工具定义和调用结果的协议外壳，核心字段包括：

```json
{
  "name": "tool_name",
  "title": "Tool Title",
  "description": "...",
  "inputSchema": {},
  "outputSchema": {},
  "annotations": {}
}
```

Tool Result 可以包含：

```json
{
  "content": [],
  "structuredContent": {},
  "isError": false
}
```

最新协议还支持 `resultType=complete/input_required`。其中：

- `content` 可以是文本、图片、音频、ResourceLink 或 EmbeddedResource。
- `structuredContent` 是程序可处理的 JSON。
- 声明 `outputSchema` 后，结构化结果必须符合它。
- `isError=true` 表示模型能够理解并尝试修正的 Tool 执行错误。
- JSON-RPC Error 用于未知 Tool、请求结构错误等协议问题。

MCP 没有规定统一的业务错误码、`retryable`、幂等键、耗时和结果确定性，因此 Agent Runtime 仍然需要适配层。

### 常见公开 MCP 的设计差异

#### Filesystem MCP

- 使用输入/输出 Schema。
- 同时返回 `content` 和 `structuredContent`。
- 使用 `readOnlyHint`、`idempotentHint`、`destructiveHint` 和 `openWorldHint`。
- `write_file` 被标记为幂等但可能覆盖文件，`edit_file` 则是非幂等且具有破坏性。

#### GitHub MCP

- 将能力拆为 repos、issues、pull_requests、actions 等 Toolset。
- 可以启用只读模式或只暴露指定 Tool。
- Tool 结合 OAuth Scope 控制权限。
- 文件读取可能返回 Text、EmbeddedResource 或 ResourceLink，业务输出并不统一。

#### Playwright MCP

- 使用 Browser Context 保存有状态的浏览器会话。
- `browser_snapshot` 返回带引用的可访问性树。
- 后续 `browser_click`、`browser_type` 等 Tool 使用元素引用继续操作。
- 需要解决引用过期、并发会话、Profile 隔离和网页提示词注入问题。

#### Fetch MCP

- 使用 Pydantic Schema 描述 URL、最大长度、起始位置和 Raw 模式。
- 主要返回纯文本。
- 内容截断信息和下一页位置也写在文本中，结构化程度较低。

#### Redis MCP

- 使用 Python 类型和 Docstring 自动生成 Schema。
- 部分 Tool 将 Redis 异常直接作为普通字符串返回。
- 不一定具有明确的 `isError`、错误码和重试语义，说明公开 MCP Server 的工程质量并不完全一致。

---

### Q12：子 Agent 需要注意什么？

### 什么是子 Agent

子 Agent 是主 Agent 委派出去的独立执行单元。它通常拥有自己的 System Prompt、上下文、工具、Workspace、状态和 ReAct Loop。

它不同于普通 Tool：Tool 通常执行一次确定方法；子 Agent 自己还会继续推理和调用工具，因此能力更强，也更难治理。

### 适用场景

- 子任务相对独立，可以并行执行。
- 需要专业角色或独立 Prompt。
- 需要读取大量资料，不希望污染主上下文。
- 结果有明确格式，并可以被主 Agent 验收。

不适合：

- 只有一两个简单工具调用。
- 步骤严格前后依赖。
- 涉及同一个数据库事务。
- 多个 Agent 会写同一资源。
- 委派成本高于主 Agent 直接执行。

### 任务契约

主 Agent 委派时需要明确：

```text
目标
输入
允许和禁止的操作
输出 Schema
完成条件
时间、Loop 和费用预算
```

### 上下文与权限

- 只传递完成子任务所需的最小上下文。
- 不默认复制完整会话历史和长期 Memory。
- 使用 Tool Allowlist，而不是默认继承父 Agent 全部工具。
- 子 Agent 必须继承父 Agent 的拒绝规则，不能通过委派实现权限升级。
- 高风险操作仍需由用户确认，子 Agent 不能自行批准。

### 生命周期和状态

需要记录：

```text
rootRunId
parentRunId
childRunId
subagentType
taskId
toolCallId
status
```

同步等待超时不代表任务已停止。AgentScope 中，同步 Spawn 超时后可能将任务提升为后台任务，因此不能因为等待超时就重复创建相同子任务。

取消根任务时，需要向所有 Child Run 传播取消请求，但仍不能假定远程 Tool 的副作用已撤销。

### 递归、并发和 Workspace

- 限制最大 Spawn 深度和每个 Agent 的子 Agent 数量。
- 限制全局并发、Loop、Token、时间和费用。
- 默认使用隔离 Workspace；共享 Workspace 需要版本控制、命名空间和单写者原则。
- 主 Agent 必须验证子 Agent 的结果，而不是直接拼接。

### AgentScope 2.0.1 的相关默认值

- 最大 Spawn 深度：3。
- 子 Agent 默认最大推理轮次：10。
- 同步等待默认 30 秒，最大 600 秒。
- Workspace 默认 `ISOLATED`。
- `persistSession` 默认关闭。
- 默认继承父 Agent 的 DENY 权限。
- Tool Allowlist 为空时继承父 Agent 全部工具。

### 当前 DataAgent 状态

当前项目明确关闭了：

```java
.disableSubagents()
.disableDynamicSubagents()
```

因此目前仍是单 HarnessAgent + Tool + Skill + Memory 模式。现阶段不启用子 Agent 是合理的，因为应优先完善 Tool 错误、权限、取消、HITL、持久化和评测。

如果后续尝试，建议先引入一个只读的 SQL Review Agent：

```text
最大深度：1
最大 Loop：6
独立 Workspace
只允许 SQL 解析和审核工具
禁止执行写 SQL
禁止再次创建子 Agent
输出结构化审核结果
```

---

## 三、建议继续深入的问题

### Agent Runtime

- Agent Loop 的完整状态机如何设计？
- 如何检测无进展、重复 Tool Call 和循环调用链？
- 如何对一次 Run 设置统一的时间、Token 和费用预算？

### Tool 治理

- Tool Schema 如何版本化并保持兼容？
- 如何避免多层重试叠加？
- 如何通过幂等键和查询接口解决 `UNKNOWN_OUTCOME`？
- 如何实现 Tool 级权限、脱敏、审计和限流？

### 状态与恢复

- AgentState、会话历史、Memory 和业务任务状态分别保存什么？
- Checkpoint 在什么时候保存，失败时恢复到哪里？
- HITL、服务重启和多实例部署下如何恢复同一 Tool Call？

### 多 Agent

- 主 Agent 如何评估是否值得委派？
- 子 Agent 如何继承最小权限而不发生权限提升？
- 并行子任务完成后如何进行冲突检测和结果验证？
- 如何取消整个父子调用树？

### 评测与可观测性

- 如何建立 NL2SQL 正确率、Tool 选择准确率和任务完成率指标？
- 如何记录 Root Run、Child Run 和 Tool Call 的完整 Trace？
- 如何对相同测试集进行可重复回归，避免只凭主观体验判断 Agent 效果？

---

## 四、面试表达建议

回答 Agent 设计问题时，可以统一使用以下结构：

1. 先定义概念和边界。
2. 说明适用场景。
3. 结合 DataAgent 给出实际例子。
4. 主动指出风险和代价。
5. 给出工程上的保护措施。
6. 说明当前项目已经实现到哪里、尚未实现什么。

例如：

> 我在 DataAgent 中将原来偏单任务的固定 Graph 迁移为 HarnessAgent + Tool 的 ReAct 模式，主要是为了改善多轮追问和局部调整体验。迁移后路径更灵活，但执行确定性下降，因此我补充了最大 Loop、Tool 权限、HITL、MySQL AgentStateStore、SSE 取消和可观测性。子 Agent 目前仍然关闭，因为 Tool 治理和恢复链路需要先稳定；后续如果验证多 Agent，我会先从只读 SQL Review Agent 开始，而不是一次拆出大量角色。

这种回答既能体现实际项目经验，也能避免把尚未实现或尚未验证的能力包装成成熟成果。
