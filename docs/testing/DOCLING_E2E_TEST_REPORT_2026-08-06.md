# Docling PDF/XLSX 知识库端到端测试报告

## 1. 测试目标

验证 DataAgent 当前 Docling 接入在真实文件上的完整链路：

1. 从 `FileStorageService` 获取文件资源；
2. 通过 Docling Serve v1.21.0 解析 PDF/XLSX；
3. 通过 PDF/XLSX 专用 mapper 生成 Spring AI `Document` 分块；
4. 补齐知识库 metadata；
5. 写入 `MetadataAwareSimpleVectorStore`；
6. 通过 metadata 精确回查和向量相似度查询验证入库结果。

## 2. 测试基线与环境

| 项目 | 值 |
| --- | --- |
| 测试日期 | 2026-08-06（Asia/Shanghai） |
| DataAgent 分支 | `codex/multi-agent-workflow` |
| DataAgent 基线提交 | `8cfc5b8`（`feat: add Docling parsing for PDF and Excel knowledge`） |
| Java | Microsoft OpenJDK 17.0.19 |
| 容器运行时 | Colima / Docker，arm64 |
| Docling Serve | `quay.io/docling-project/docling-serve:v1.21.0` |
| Docling Java client | `ai.docling:docling-serve-client:0.6.1` |
| 向量库 | `MetadataAwareSimpleVectorStore` |
| 嵌入模型 | 测试专用确定性 `KeywordEmbeddingModel`（不访问外部模型服务） |
| PDF 分块器 | 生产参数等价的 `TokenTextSplitter(1000, 400, 10, 5000, true)` |
| Excel 表格行分块 | 每块最多 2 个数据行（测试特意缩小阈值以覆盖多块映射） |

## 3. 真实测试文件

### 3.1 PDF

- 文件：Docling Java 官方测试资源 `story.pdf`
- 来源仓库：`https://github.com/docling-project/docling-java.git`
- 来源提交：`eafa5f2ec5dfaf04633ba3b367e05c2ffa3e2d2e`
- SHA-256：`d414537e9ca50b29a22ec4453d1ea8f34bba6137579f392461fc3247e6f8b27c`
- 大小：22,071 字节
- 页面：3 页 A4；第 1 页为标题，第 2-3 页为完整英文故事正文
- 源文件视觉检查：3 页均可正常渲染，无加密，无表单，无 JavaScript

### 3.2 XLSX

- 文件：`data-agent-management/src/main/resources/excel/semantic_model_template.xlsx`
- SHA-256：`42a9ad754e68fc962551707ff2fc7958226b95eb1826fe6c90c6821ee5d524f5`
- 工作表：`语义模型导入模板`
- 有效区域：`A1:H4`，31 个非空单元格
- 内容：1 行表头和 3 行 `orders` 字段数据（`order_id`、`customer_name`、`order_amount`）

## 4. 执行方式

测试类：
`data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/knowledge/docling/DoclingRealFilesE2ETest.java`

执行命令：

```bash
DOCLING_E2E=true \
DOCLING_E2E_PDF=/absolute/path/to/story.pdf \
DOCLING_E2E_XLSX=/absolute/path/to/semantic_model_template.xlsx \
DOCLING_E2E_URL=http://localhost:5001 \
DOCLING_E2E_API_KEY=data-agent-docling \
JAVA_HOME=/path/to/jdk-17 \
./mvnw -pl data-agent-management \
  -Dtest=DoclingRealFilesE2ETest test
```

## 5. Docling Serve 可用性

| 检查项 | 实测结果 |
| --- | --- |
| Docker context | `colima-docling-e2e` |
| 容器 | `data-agent-docling` |
| 容器状态 | `healthy` |
| 镜像 | `quay.io/docling-project/docling-serve:v1.21.0` |
| 本地镜像摘要 | `sha256:32b3de41f325f93c1dd35907cd9147fa35df9f7c5abc86eb2788b6bda7ce6d10` |
| 容器内健康检查 | HTTP 200，`{"status":"ok"}` |
| 宿主机健康检查 | HTTP 200，`{"status":"ok"}` |
| 服务日志 | Docling Server 正常启动；RapidOCR 三个 ONNX 模型和 770 个权重项加载完成 |

基础 Compose 的 `docling-serve` 没有发布宿主机端口，只能由 Compose 网络内的 backend 访问。本次测试通过临时 override 将端口绑定为 `127.0.0.1:5001:5001`，未暴露到外部网卡。

## 6. 自动化执行结果

### 6.1 真实文件 E2E

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Maven total time: 12.719 s
Wall-clock time: 13.54 s
```

该用例对每个文件执行两次真实 Docling 转换：第一次采集解析与分块证据，第二次从 `AgentKnowledgeResourceManager` 生产入口完成 metadata 补全和向量入库。

| 文件 | Docling 解析块数 | 向量入库数 | metadata 精确回查数 | 相似度召回数 |
| --- | ---: | ---: | ---: | ---: |
| `story.pdf` | 3 | 3 | 3 | 3（查询 `polar bear`） |
| `semantic_model_template.xlsx` | 2 | 2 | 2 | 2（查询 `order`） |

### 6.2 相关回归测试

覆盖 `DoclingDocumentReader`、PDF/XLSX mapper、`AgentKnowledgeResourceManager`、`MetadataAwareSimpleVectorStore` 和 `AgentVectorStoreServiceImpl`：

```text
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
Checkstyle violations: 0
BUILD SUCCESS
```

测试日志中的 `disk error` 和 `connection refused` 堆栈来自资源清理异常分支的预期模拟用例；对应断言通过，不是本次 E2E 运行故障。

## 7. PDF 最终读取与分块结果

### 7.1 分块清单

| 块 | Document ID | 页码 | 字符数 | elementType | sectionPath | sourceElementId |
| ---: | --- | ---: | ---: | --- | --- | --- |
| 0 | `88e884a2-b897-3d2c-b0cb-b3afcba855ea` | 1 | 66 | `text` | `Tales from the Animal Kingdom` | `#/texts/0` |
| 1 | `348c2b51-1a80-351c-91ed-f7f78db397d1` | 2 | 1,603 | `text` | `The Adventures of Iorek and Pingu` | `#/texts/1` 至 `#/texts/6` |
| 2 | `e61cde02-aa73-39ff-bf87-a38df66f7aab` | 3 | 1,624 | `text` | `The Adventures of Iorek and Pingu` | `#/texts/7` 至 `#/texts/11` |

三个块共有以下解析 metadata：

```json
{
  "parser": "docling",
  "parserVersion": "1.10.0",
  "sourceFilename": "story.pdf",
  "elementType": "text",
  "chunkIndex": "0..2",
  "splitChunkIndex": 0,
  "pageNumber": "1..3"
}
```

`parserVersion=1.10.0` 是返回的 Docling Document schema 版本；服务镜像版本仍为 v1.21.0。

### 7.2 最终读取文本

块 0：

```text
标题路径: Tales from the Animal Kingdom

Tales from the Animal Kingdom
```

块 1：

```text
标题路径: The Adventures of Iorek and Pingu

The Adventures of Iorek and Pingu
Iorek was a little polar bear who lived in the Arctic circle. He loved to explore the snowy landscape and dreamt of one day going on an adventure around the North Pole. One day, he met a penguin named Pingu who was on a similar quest. They quickly became friends and decided to embark on their journey together.
Iorek and Pingu set off early in the morning, eager to cover as much ground as possible before nightfall. The air was crisp and cold, and the snow crunched under their paws as they walked. They chatted excitedly about their dreams and aspirations, and Iorek told Pingu about his desire to see the Northern Lights.
As they journeyed onward, they encountered a group of playful seals who were sliding and jumping in the snow. Iorek and Pingu watched in delight as the seals frolicked and splashed in the water. They even tried to join in, but their paws kept slipping and they ended up sliding on their stomachs instead.
After a few hours of walking, Iorek and Pingu came across a cave hidden behind a wall of snow. They cautiously entered the darkness, their eyes adjusting to the dim light inside. The cave was filled with glittering ice formations that sparkled like diamonds in the flickering torchlight.
As they continued their journey, Iorek and Pingu encountered a group of walruses who were lounging on the ice. They watched in amazement as the walruses lazily rolled over and exposed their tusks for a good scratch. Pingu even tried to imitate them, but ended up looking more like a clumsy seal than a walrus.
```

块 2：

```text
标题路径: The Adventures of Iorek and Pingu

As the sun began to set, Iorek and Pingu found themselves at the edge of a vast, frozen lake. They gazed out across the glassy surface, mesmerized by the way the ice glinted in the fading light. They could see the faint outline of a creature moving beneath the surface, and their hearts raced with excitement.
Suddenly, a massive narwhal burst through the ice and into the air, its ivory tusk glistening in the sunset. Iorek and Pingu watched in awe as it soared overhead, its cries echoing across the lake. They felt as though they were witnessing a magical moment, one that would stay with them forever.
As the night drew in, Iorek and Pingu settled down to rest in their makeshift camp. They huddled together for warmth, gazing up at the starry sky above. They chatted about all they had seen and experienced during their adventure, and Iorek couldn't help but feel grateful for the new friend he had made.
The next morning, Iorek and Pingu set off once again, determined to explore every inch of the North Pole. They stumbled upon a hidden cave filled with glittering crystals that sparkled like diamonds in the sunlight. They marveled at their beauty before continuing on their way.
As they journeyed onward, Iorek and Pingu encountered many more wonders and adventures. They met a group of playful reindeer who showed them how to pull sledges across the snow, and even caught a glimpse of the mythical Loch Ness Monster lurking beneath the icy waters. In the end, their adventure around the North Pole had been an unforgettable experience, one that they would treasure forever.
```

## 8. XLSX 最终读取与分块结果

测试将 `maxTableRowsPerChunk` 设为 2，以明确覆盖表格跨块映射。

| 块 | Document ID | 工作表 metadata | 数据行范围 | 字符数 | elementType | tableIndex |
| ---: | --- | --- | --- | ---: | --- | ---: |
| 0 | `dfd1a0ee-6403-3afe-b60d-46f2a59a3630` | `Workbook` | 2-3 | 303 | `table` | 1 |
| 1 | `d72e536c-cfb0-3c0c-b6b8-c77be538814b` | `Workbook` | 4-4 | 221 | `table` | 1 |

块 0：

```markdown
工作表: Workbook
| 表名* | 字段名* | 业务名称* | 数据类型* | 同义词 | 业务描述 | 字段注释 | 创建时间 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| orders | order_id | 订单ID | bigint | 订单编号,订单号 | 订单的唯一标识符 | 主键 | 2024-01-01 10:00:00 |
| orders | customer_name | 客户姓名 | varchar(100) | 客户名,买家姓名 | 下单客户的姓名 |  | 2024-01-01 10:00:00 |
```

块 1：

```markdown
工作表: Workbook
| 表名* | 字段名* | 业务名称* | 数据类型* | 同义词 | 业务描述 | 字段注释 | 创建时间 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| orders | order_amount | 订单金额 | decimal(10,2) | 订单总额,金额 | 订单的总金额（元） | 单位：元 | 2024-01-01 10:00:00 |
```

## 9. 入库结果

生产入口在解析 metadata 之上补充了以下知识库身份字段：

| 字段 | PDF | XLSX |
| --- | --- | --- |
| `agentId` | `99` | `99` |
| `agentKnowledgeId` | `9001` | `9002` |
| `vectorType` | `agentKnowledge` | `agentKnowledge` |
| `concreteAgentKnowledgeType` | `DOCUMENT` | `DOCUMENT` |
| `parser` | `docling` | `docling` |
| 入库 Document 数 | 3 | 2 |
| metadata 精确回查数 | 3 | 2 |

入库后 Document ID 与解析阶段保持一致。精确回查同时验证 `agentKnowledgeId` 过滤有效；相似度回查验证查询 embedding、metadata filter 和向量相似度链路均可执行。

本次使用项目的内存回退实现 `MetadataAwareSimpleVectorStore` 和确定性测试 embedding，验证的是 DataAgent 实际入库接口及向量计算链路，不代表 Milvus/Elasticsearch 的持久化或真实语义模型召回质量。

## 10. 缺陷与风险

### 10.1 XLSX 工作表名丢失 - 建议优先修复

源工作表名为 `语义模型导入模板`，但 Docling 返回结构经过当前 mapper 后得到 `sheetName=Workbook`。表格内容、行范围和表头均正确，但工作表级定位信息不准确。多工作表文件可能因此无法区分表格来源。

建议补充一个真实多工作表 XLSX 用例，并根据 Docling 返回的 group/origin 信息修正 fallback table 到 sheet 的映射。

### 10.2 PDF 标题重复

PDF 的标题同时进入 `sectionPath` 前缀和正文，导致块 0、块 1 的标题出现两次。不会导致内容丢失，但会产生冗余 token 并影响 embedding 权重。

建议 mapper 在将 `TITLE`/`SECTION_HEADER` 写入标题路径后，不再把同一 text item 追加到正文。

### 10.3 本轮未覆盖项

- PDF 是文字型 PDF，不是扫描件；RapidOCR 已加载，但没有验证 OCR 识别质量。
- 没有覆盖 PDF 表格、图片描述和复杂版面。
- 没有覆盖多工作表、公式、合并单元格和隐藏工作表。
- 没有连接 Milvus/Elasticsearch，也没有调用线上 embedding 服务。

## 11. 结论

Docling Serve 可用，PDF/XLSX 从文件读取到向量入库、精确回查和相似度召回的端到端功能链路通过；相关回归测试 33/33 通过。

当前结论为“功能通过，映射质量有条件通过”：内容无丢失、分块和知识库 metadata 正确，但 XLSX 工作表名丢失与 PDF 标题重复应在生产验收前修复。修复后建议增加扫描 PDF、PDF 表格和多工作表 XLSX 三类真实样本，再执行 Milvus/Elasticsearch 提供方验收。
