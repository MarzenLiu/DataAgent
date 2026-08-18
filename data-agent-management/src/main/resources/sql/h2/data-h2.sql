-- 初始化数据文件
-- 只在表为空时插入示例数据

-- 智能体示例数据（必须先插入，因为其他表依赖它）
INSERT INTO agent (id, name, description, avatar, status, api_key, api_key_enabled, prompt, category, admin_id, tags, create_time, update_time) VALUES
(1, '电商订单分析智能体', '基于用户、商品、订单和分类数据进行查询与经营分析', NULL, 'draft', NULL, 0, '你是一个严谨的电商运营数据分析智能体。必须以配置的数据源为事实依据，不得编造表、字段或查询结果。问题涉及业务规则、SOP或能力边界时先调用 search_knowledge_base。处理数据问题时先调用 inspect_data_source，需要结果时调用 execute_read_only_sql。禁止写操作和DDL。MODE为NL2SQL_ONLY时只返回SQL。', '电商分析', 2100246635, '订单分析,商品分析,用户分析', NOW(), NOW()),
(2, '销售数据分析智能体', '专注于销售数据分析和业务指标计算的智能体', NULL, 'draft', NULL, 0, '你是一个严谨的销售数据分析智能体。使用 search_knowledge_base、inspect_data_source 和 execute_read_only_sql，必须基于真实数据，禁止写操作和DDL。MODE为NL2SQL_ONLY时只返回SQL。', '业务分析', 2100246635, '销售分析,业务指标,客户分析', NOW(), NOW()),
(3, '财务报表智能体', '专门处理财务数据和报表分析的智能体', NULL, 'draft', NULL, 0, '你是一个严谨的财务数据分析智能体。使用知识库和真实数据核实口径，不得臆造财务数据，只允许只读查询。MODE为NL2SQL_ONLY时只返回SQL。', '财务分析', 2100246635, '财务数据,报表分析,会计', NOW(), NOW()),
(4, '库存管理智能体', '专注于库存数据管理和供应链分析的智能体', NULL, 'draft', NULL, 0, '你是一个严谨的库存和供应链数据分析智能体。必须以真实数据为准，不得编造入出库、仓库或供应商数据，只允许只读查询。MODE为NL2SQL_ONLY时只返回SQL。', '供应链', 2100246635, '库存管理,供应链,物流', NOW(), NOW()),
(5, '商品购买智能体', '查询实时商品、价格和库存，并在用户确认后安全下单', NULL, 'published', NULL, 0, '你是一个谨慎的商品购买助手，只能使用 search_products 和 place_order。不得编造商品、价格、库存或订单。下单前必须确认 userId、productId、quantity 和明确购买意图；place_order 报错后不得再次调用。成功后告知订单详情，失败时明确告知事务已回滚。', '购物下单', 2100246635, '商品查询,下单,购物助手', NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), avatar=VALUES(avatar), prompt=VALUES(prompt), category=VALUES(category), tags=VALUES(tags);

INSERT INTO agent_tool (agent_id, tool_name, approval_mode, inject_agent_id, available_in_nl2sql_only, is_enabled, sort_order) VALUES
(1, 'search_knowledge_base', 'ALLOW', 1, 1, 1, 10), (1, 'inspect_data_source', 'ALLOW', 1, 1, 1, 20), (1, 'execute_read_only_sql', 'ASK_WHEN_HITL', 1, 0, 1, 30),
(2, 'search_knowledge_base', 'ALLOW', 1, 1, 1, 10), (2, 'inspect_data_source', 'ALLOW', 1, 1, 1, 20), (2, 'execute_read_only_sql', 'ASK_WHEN_HITL', 1, 0, 1, 30),
(3, 'search_knowledge_base', 'ALLOW', 1, 1, 1, 10), (3, 'inspect_data_source', 'ALLOW', 1, 1, 1, 20), (3, 'execute_read_only_sql', 'ASK_WHEN_HITL', 1, 0, 1, 30),
(4, 'search_knowledge_base', 'ALLOW', 1, 1, 1, 10), (4, 'inspect_data_source', 'ALLOW', 1, 1, 1, 20), (4, 'execute_read_only_sql', 'ASK_WHEN_HITL', 1, 0, 1, 30),
(5, 'search_products', 'ALLOW', 1, 1, 1, 10), (5, 'place_order', 'ALWAYS_ASK', 1, 0, 1, 20)
ON DUPLICATE KEY UPDATE approval_mode=VALUES(approval_mode), inject_agent_id=VALUES(inject_agent_id), available_in_nl2sql_only=VALUES(available_in_nl2sql_only), is_enabled=VALUES(is_enabled), sort_order=VALUES(sort_order);

INSERT INTO harness_skill (skill_name, description, content, is_enabled) VALUES
('result-validation-sop', '在输出分析结论前校验查询结果的完整性、粒度和口径。', '# 查询结果校验流程

1. 确认查询结果的时间范围、过滤条件和分组粒度与用户问题一致。
2. 检查空值、重复行、异常值以及合计与明细是否一致。
3. 区分数据库返回的事实、基于事实的计算和无法验证的推断。
4. 发现口径冲突或数据不足时明确说明，不要补造结果。', 1)
ON DUPLICATE KEY UPDATE description=VALUES(description), content=VALUES(content), is_enabled=VALUES(is_enabled);

INSERT INTO agent_skill (agent_id, skill_name, is_enabled, sort_order) VALUES
(1, 'data-analysis-sop', 1, 10), (1, 'result-validation-sop', 1, 20),
(2, 'data-analysis-sop', 1, 10), (2, 'result-validation-sop', 1, 20),
(3, 'data-analysis-sop', 1, 10), (3, 'result-validation-sop', 1, 20),
(4, 'data-analysis-sop', 1, 10), (4, 'result-validation-sop', 1, 20)
ON DUPLICATE KEY UPDATE is_enabled=VALUES(is_enabled), sort_order=VALUES(sort_order);

-- 数据源示例数据（必须先插入，因为其他表依赖它）
-- 示例数据源可以运行docker-compose-datasource.yml建立，或者手动修改为自己的数据源
INSERT INTO datasource (id, name, type, host, port, database_name, username, password, connection_url, status, test_status, description, creator_id, create_time, update_time) VALUES 
(1, '生产环境MySQL数据库', 'mysql', 'mysql-data', 3306, 'product_db', 'root', 'root', 'jdbc:mysql://mysql-data:3306/product_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true', 'inactive', 'unknown', '生产环境主数据库，包含核心业务数据', 2100246635, NOW(), NOW()),
(2, '数据仓库PostgreSQL', 'postgresql', 'postgres-data', 5432, 'data_warehouse', 'postgres', 'postgres', 'jdbc:postgresql://postgres-data:5432/data_warehouse', 'inactive', 'unknown', '数据仓库，用于数据分析和报表生成', 2100246635, NOW(), NOW()),
(3, 'product_db', 'h2', 'nl2sql_database', 0, 'product_db', 'root', 'root', 'jdbc:h2:mem:nl2sql_database;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE', 'inactive', 'unknown', 'h2测试数据库，包含核心业务数据', 2100246635, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 业务知识示例数据（依赖 agent）
INSERT INTO business_knowledge (id, business_term, description, synonyms, is_recall, agent_id, created_time, updated_time) VALUES
(1, '成交金额', '成交金额为 status = ''completed'' 的订单 total_amount 之和。', '销售额, 成交额, 已完成订单金额', 1, 1, NOW(), NOW()),
(2, '订单用户', '订单通过 orders.user_id = users.id 关联用户。', '客户, 买家, 下单用户', 1, 1, NOW(), NOW()),
(3, 'Customer Retention Rate', 'The percentage of customers who continue to use a service over a given period.', 'retention, customer loyalty', 0, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE business_term=VALUES(business_term);

-- 语义模型示例数据（依赖 agent 和 datasource）
INSERT INTO semantic_model (id, agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description, column_comment, data_type, created_time, updated_time, status) VALUES
(1, 1, 3, 'orders', 'total_amount', '订单金额', '销售额, 成交金额, 支付金额', '订单总金额，统计成交额时应过滤 status = completed。', '订单总金额', 'decimal(10,2)', NOW(), NOW(), 1),
(2, 1, 3, 'orders', 'status', '订单状态', '状态, order_status', '订单状态为 pending、completed 或 cancelled。', '订单状态', 'varchar(20)', NOW(), NOW(), 1),
(3, 2, 1, 'customer_metrics', 'retention_pct', 'customerRetentionRate', 'retention rate, loyalty rate', 'Percentage of retained customers', '客户保留率', 'decimal', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE business_name=VALUES(business_name);

-- 智能体知识示例数据（依赖 agent）
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, question, created_time, updated_time) VALUES
(1, 1, '电商示例数据库表结构说明', '核心表包括 users、products、orders、order_items、categories 和 product_categories。orders.user_id 关联 users.id，order_items 关联订单和商品。', 'QA', 1, 'PENDING', 'text', '电商示例数据库有哪些核心表和关联关系？', NOW(), NOW()),
(2, 1, '订单经营分析标准流程', '订单分析先按 status 过滤，再按 order_date 确定时间范围。成交金额使用 SUM(total_amount)，成交订单状态为 completed。', 'QA', 1, 'PENDING', 'text', '如何进行订单经营分析？', NOW(), NOW()),
(3, 1, '统计已完成订单金额', 'SELECT SUM(total_amount) FROM orders WHERE status = ''completed''。', 'QA', 1, 'PENDING', 'text', '如何统计已完成订单金额？', NOW(), NOW()),
(4, 2, '销售数据字段说明', '销售分析以真实 orders 字段为准：order_date、total_amount、status、user_id。商品维度通过 order_items 连接 products。', 'QA', 1, 'PENDING', 'text', '销售分析使用哪些真实字段？', NOW(), NOW()),
(5, 2, '客户分析指标体系', '客户价值可按已完成订单金额、订单数和最近下单时间分析，使用 orders.user_id 关联 users.id。', 'FAQ', 1, 'PENDING', 'text', '如何基于订单分析客户价值？', NOW(), NOW()),
(6, 3, '财务报表能力边界', '当前示例数据没有会计科目、凭证和现金流表，不能生成可靠财务报表；应先绑定包含财务数据的数据源。', 'FAQ', 1, 'PENDING', 'text', '当前示例库能否生成财务报表？', NOW(), NOW()),
(7, 4, '库存分析能力边界', '当前示例库可使用 products.stock 查看商品库存，但没有入库、出库、仓库和供应商流水，无法完成完整供应链分析。', 'FAQ', 1, 'PENDING', 'text', '当前示例库支持哪些库存分析？', NOW(), NOW())
ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content), type=VALUES(type), file_type=VALUES(file_type), question=VALUES(question);

-- 智能体数据源关联示例数据（依赖 agent 和 datasource）
INSERT INTO agent_datasource (id, agent_id, datasource_id, is_active, create_time, update_time) VALUES 
(1, 1, 3, 1, NOW(), NOW()),  -- 电商订单分析智能体使用H2示例数据库
(2, 2, 1, 0, NOW(), NOW()),  -- 销售数据分析智能体使用生产环境数据库
(3, 3, 1, 0, NOW(), NOW()),  -- 财务报表智能体使用生产环境数据库
(4, 4, 1, 0, NOW(), NOW()),  -- 库存管理智能体使用生产环境数据库
(5, 5, 3, 1, NOW(), NOW())  -- 商品购买智能体使用H2示例数据库
ON DUPLICATE KEY UPDATE agent_id=VALUES(agent_id);
