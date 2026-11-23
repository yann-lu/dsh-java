-- =====================================================================
-- 从 0 重建版 schema.sql
-- 与原项目的偏差：为所有字段补了列级 COMMENT 备注（原版只有表级 -- 注释），
-- 见 docs/rebuild-mapping.md Deviations。表结构、索引、种子数据与原版一致。
-- 说明：sql.init.mode=always 幂等重跑（CREATE TABLE IF NOT EXISTS / INSERT ... WHERE NOT EXISTS），
--       MySQL 与 standalone H2(MODE=MySQL) 共用本文件。
-- =====================================================================

-- ── 旧版任务轨（会话注册表：含审批状态机）────────────────────
CREATE TABLE IF NOT EXISTS harness_session (
    session_id     VARCHAR(64) PRIMARY KEY COMMENT '会话唯一标识（agentId 复用/恢复的入口）',
    workspace_id   VARCHAR(128) NOT NULL COMMENT '所属工作区',
    profile_code   VARCHAR(64) NOT NULL COMMENT '执行画像（headless/web 等，决定工具目录与审批面）',
    execution_mode VARCHAR(32) NOT NULL COMMENT '执行模式',
    resolved_model VARCHAR(128) NOT NULL COMMENT '解析后实际使用的模型',
    channel_code   VARCHAR(64) NULL COMMENT '模型渠道编码（关联 harness_model_setting）',
    status         VARCHAR(32) NOT NULL COMMENT '会话状态机：QUEUED / PENDING_APPROVAL / RUNNING / COMPLETED / FAILED',
    created_at     TIMESTAMP(3) NOT NULL COMMENT '创建时间'
);

CREATE TABLE IF NOT EXISTS harness_task (
    task_id            VARCHAR(64) PRIMARY KEY COMMENT '任务唯一标识',
    session_id         VARCHAR(64) NOT NULL COMMENT '关联会话（执行复用 Agent 链路）',
    prompt             TEXT NOT NULL COMMENT '任务提示词',
    enabled_tools_json TEXT NOT NULL COMMENT '本次提交允许的工具白名单（JSON 数组）',
    status             VARCHAR(32) NOT NULL COMMENT '任务状态：CREATED / PENDING_APPROVAL / QUEUED / RUNNING / COMPLETED / FAILED',
    submitted_at       TIMESTAMP(3) NOT NULL COMMENT '提交时间'
);

-- 旧轨事件表：无 seq，靠自增 id + occurred_at 排序；事件为扁平字符串（task.submitted 等）。
-- 历史演进产物，新轨见 harness_session_event_log；查询侧双轨合并（LegacyEventAdapter 桥接）。
CREATE TABLE IF NOT EXISTS harness_session_event (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键（旧轨无 seq，用它保证顺序）',
    session_id  VARCHAR(64) NOT NULL COMMENT '会话标识',
    event_type  VARCHAR(128) NOT NULL COMMENT '扁平字符串事件类型，如 task.submitted / task.approval.required',
    payload     TEXT NOT NULL COMMENT '事件载荷',
    occurred_at TIMESTAMP(3) NOT NULL COMMENT '发生时间',
    KEY idx_harness_session_event_session (session_id)
);

-- ── 插件（登记 / 运行绑定 / 分层配置）────────────────────────
CREATE TABLE IF NOT EXISTS harness_plugin_installation (
    plugin_id      VARCHAR(128) PRIMARY KEY COMMENT '插件唯一标识（小写短横线，进工具名与安装路径）',
    display_name   VARCHAR(255) NOT NULL COMMENT '展示名',
    plugin_version VARCHAR(64) NOT NULL COMMENT '插件版本',
    runtime_type   VARCHAR(64) NOT NULL COMMENT '运行时类型：JAVA_NATIVE（进程内）/ DSH_NODE_BRIDGE（sidecar 子进程）',
    install_mode   VARCHAR(64) NOT NULL COMMENT '安装方式（HTTP 上传 / preset 预装）',
    source_path    VARCHAR(512) NOT NULL COMMENT '制品源路径（jar 或插件目录）',
    entrypoint     VARCHAR(512) NOT NULL COMMENT '入口（Java 类全限定名，或 Node 入口脚本）',
    status         VARCHAR(32) NOT NULL COMMENT '登记状态机：REGISTERED → ACTIVE → DISABLED / FAILED / UNINSTALLED（运行态另查运行时，不能由本字段推断）',
    installed_at   TIMESTAMP(3) NOT NULL COMMENT '安装时间'
);

CREATE TABLE IF NOT EXISTS harness_plugin_runtime_binding (
    plugin_id         VARCHAR(128) PRIMARY KEY COMMENT '插件标识',
    package_type      VARCHAR(64) NOT NULL COMMENT '包型（识别自 plugin.json / package.json / cordis.yml 等）',
    manifest_path     VARCHAR(512) NULL COMMENT '清单文件路径',
    working_directory VARCHAR(512) NULL COMMENT '运行工作目录',
    runtime_command   VARCHAR(1024) NULL COMMENT '运行命令（java-in-process 或受限 node <entrypoint>）',
    bridge_ready      TINYINT(1) NOT NULL COMMENT '桥接探活是否就绪（true 才算 ACTIVE，工具可用）',
    message           TEXT NOT NULL COMMENT '探活/桥接结果消息',
    checked_at        TIMESTAMP(3) NOT NULL COMMENT '最近探活时间'
);

CREATE TABLE IF NOT EXISTS harness_plugin_config (
    plugin_id    VARCHAR(128) NOT NULL COMMENT '插件标识',
    config_key   VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value TEXT NULL COMMENT '配置值',
    config_layer VARCHAR(32) NOT NULL COMMENT '配置层级（默认/宿主基础/运行时覆盖，读取顺序为覆盖值→宿主→默认）',
    updated_at   TIMESTAMP(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (plugin_id, config_key, config_layer)
);

-- ── 模型渠道配置（权威来源；密钥只存引用，真实值在 CredentialsYamlStore）──
CREATE TABLE IF NOT EXISTS harness_model_setting (
    channel_code  VARCHAR(64) NOT NULL COMMENT '渠道编码，主键（如 deepseek / openai-compatible）',
    display_name  VARCHAR(128) NOT NULL COMMENT '展示名',
    provider_code VARCHAR(64) NOT NULL COMMENT '供应商标识',
    model_code    VARCHAR(128) NOT NULL COMMENT '模型编码',
    base_url      VARCHAR(255) NULL COMMENT 'API 地址（可空，走 harness.yml 兜底）',
    api_key_ref   VARCHAR(128) NULL COMMENT '凭据引用名（不存明文，真实密钥走 credentials 存储）',
    protocol      VARCHAR(32) NOT NULL DEFAULT 'openai' COMMENT '协议：openai / anthropic 等，ProtocolRoutingAdapter 据此路由',
    enabled       BOOLEAN NOT NULL COMMENT '是否启用',
    active        BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为全局唯一激活渠道（activate 时先清后置）',
    updated_at    TIMESTAMP(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (channel_code)
);

-- 开发阶段模型渠道表以 channel_code 为主键；channel_code 与 harness_session.channel_code
-- 均由 SchemaMigrationRunner 在启动时幂等补齐。

-- ── 目标状态（跨回合长命快照，每会话一条）────────────────────
CREATE TABLE IF NOT EXISTS harness_goal_state (
    session_id           VARCHAR(64) PRIMARY KEY COMMENT '会话标识（每会话一条活跃目标）',
    goal_id              VARCHAR(128) NOT NULL COMMENT '目标标识',
    revision             BIGINT NOT NULL COMMENT '乐观锁版本号（并发更新校验）',
    title                VARCHAR(255) NOT NULL COMMENT '目标标题',
    description          TEXT NULL COMMENT '目标描述',
    status               VARCHAR(32) NOT NULL COMMENT '目标状态',
    blocked_reason       TEXT NULL COMMENT '阻塞原因',
    max_rounds           INT NOT NULL COMMENT '最大轮数上限',
    rounds_started       INT NOT NULL COMMENT '已启动轮数',
    continuation_enabled BOOLEAN NOT NULL COMMENT '是否允许自动续轮',
    created_at           TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at           TIMESTAMP(3) NOT NULL COMMENT '更新时间'
);

-- ── 新一代事件溯源轨 ─────────────────────────────────────────
-- 会话头：可变热行（upsert + version 乐观锁）；列表页只扫本表，不碰事件表。
CREATE TABLE IF NOT EXISTS harness_session_header (
    session_id       VARCHAR(64) PRIMARY KEY COMMENT '会话唯一标识',
    version          INT NOT NULL COMMENT '乐观锁版本（每次 upsert 递增）',
    created_at       TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    cwd              VARCHAR(512) NULL COMMENT '会话工作目录（fs/shell 沙箱锚点）',
    parent_session   VARCHAR(64) NULL COMMENT '父会话（子代理委派场景）',
    seed_length      INT NOT NULL DEFAULT 0 COMMENT '种子消息长度',
    origin           VARCHAR(32) NULL COMMENT '会话来源',
    delegation_depth INT NOT NULL DEFAULT 0 COMMENT '委派深度',
    agent_preset     VARCHAR(128) NULL COMMENT 'Agent 预设标识（恢复时按它匹配会话）'
);

-- 事件溯源主表：追加不可变；UNIQUE(session_id, seq) 同时给出全序、幂等写与增量拉取（readAfter）。
-- 恢复 = 全量回放；surface_op 让前端断线重连可精确续播。
CREATE TABLE IF NOT EXISTS harness_session_event_log (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '物理自增 id',
    session_id        VARCHAR(64) NOT NULL COMMENT '会话标识',
    seq               BIGINT NOT NULL COMMENT '会话内单调递增序号（由 SessionLog 分配）',
    event_type        VARCHAR(64) NOT NULL COMMENT '事件类型（turn/start、user/message、tool/call 等 wire name）',
    event_time        TIMESTAMP(3) NOT NULL COMMENT '事件时间',
    payload_json      LONGTEXT NULL COMMENT '事件体（JSON）',
    surface_op        VARCHAR(16) NULL COMMENT '前端渲染意图：append / replace:start:end',
    source_event_seqs VARCHAR(512) NULL COMMENT '因果来源事件 seq 列表（如压缩摘要指向被压缩的事件）',
    UNIQUE (session_id, seq),
    KEY idx_harness_session_event_log_session (session_id)
);

-- 模型配置的权威来源是本表；默认模型种子由 ModelSyncRunner 按 application.yml 初始化，
-- 不在 schema 中重复写入可能过期的 base_url、api_key 或模型编码。

-- ── 工具目录与画像绑定 ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS harness_tool_catalog (
    tool_code         VARCHAR(128) PRIMARY KEY COMMENT '工具编码（fs.read / shell.exec / mcp.call 等）',
    display_name      VARCHAR(255) NOT NULL COMMENT '展示名',
    description       TEXT NULL COMMENT '工具说明',
    category          VARCHAR(64) NOT NULL COMMENT '分类（fs / shell / web / mcp）',
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否需要审批（提交期权限矩阵评估的依据）'
);

CREATE TABLE IF NOT EXISTS harness_tool_profile_binding (
    profile_code VARCHAR(64) NOT NULL COMMENT '画像编码（headless / web）',
    tool_code    VARCHAR(128) NOT NULL COMMENT '工具编码',
    PRIMARY KEY (profile_code, tool_code)
);

-- ── Tool catalog seed data ───────────────────────────────────
INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs.read', 'Read File', 'Read file contents from the workspace.', 'fs', FALSE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs.read');

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'fs.write', 'Write File', 'Write or overwrite file contents in the workspace.', 'fs', TRUE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'fs.write');

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'shell.exec', 'Execute Shell', 'Run a shell command in the sandbox.', 'shell', TRUE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'shell.exec');

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'web.search', 'Web Search', 'Search the web for information.', 'web', FALSE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'web.search');

INSERT INTO harness_tool_catalog (tool_code, display_name, description, category, requires_approval)
SELECT 'mcp.call', 'MCP Tool', 'Invoke an MCP server tool.', 'mcp', TRUE
WHERE NOT EXISTS (SELECT 1 FROM harness_tool_catalog WHERE tool_code = 'mcp.call');

-- Default: enable read-only tools for headless and web profiles
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('headless', 'fs.read');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('headless', 'web.search');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('web', 'fs.read');
INSERT IGNORE INTO harness_tool_profile_binding (profile_code, tool_code) VALUES ('web', 'web.search');

-- ── 数字人协作（第 15 站内容，room_event 镜像会话事件表的 (scope, seq) 设计）──
-- 数字人身份（展示与策略，与连接解耦）
CREATE TABLE IF NOT EXISTS digital_human (
    id                VARCHAR(64) PRIMARY KEY COMMENT '数字人标识',
    display_name      VARCHAR(128) NOT NULL COMMENT '展示名',
    avatar_ref        VARCHAR(255) NULL COMMENT '头像引用',
    purpose           VARCHAR(1024) NOT NULL COMMENT '定位/职责说明',
    role_tags         VARCHAR(512) NULL COMMENT '角色标签',
    theme_color       VARCHAR(16) NULL COMMENT '主题色',
    approval_policy   VARCHAR(32) NOT NULL DEFAULT 'WRITE_REQUIRES_APPROVAL' COMMENT '审批策略（写操作需审批等）',
    concurrency_limit INT NOT NULL DEFAULT 1 COMMENT '并发执行上限',
    project_path      VARCHAR(512) NULL COMMENT '关联项目路径',
    created_at        TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at        TIMESTAMP(3) NOT NULL COMMENT '更新时间'
);

-- 服务端点（连接配置）
CREATE TABLE IF NOT EXISTS digital_human_endpoint (
    id               VARCHAR(64) PRIMARY KEY COMMENT '端点标识',
    digital_human_id VARCHAR(64) NOT NULL COMMENT '所属数字人',
    endpoint_type    VARCHAR(24) NOT NULL COMMENT '端点类型',
    base_url         VARCHAR(512) NULL COMMENT '服务地址',
    credential_ref   VARCHAR(128) NULL COMMENT '凭据引用（不存明文）',
    protocol_version VARCHAR(16) NULL COMMENT '协议版本',
    agent_card       TEXT NULL COMMENT 'A2A agent card（JSON，能力自描述）',
    health_state     VARCHAR(16) NOT NULL DEFAULT 'unknown' COMMENT '探活状态',
    last_checked_at  TIMESTAMP(3) NULL COMMENT '最近探活时间',
    latency_ms       INT NULL COMMENT '最近延迟（毫秒）',
    KEY idx_dh_endpoint_human (digital_human_id)
);

-- 协作房间
CREATE TABLE IF NOT EXISTS collaboration_room (
    id                 VARCHAR(64) PRIMARY KEY COMMENT '房间标识',
    workspace_id       VARCHAR(128) NOT NULL COMMENT '所属工作区',
    title              VARCHAR(255) NOT NULL COMMENT '房间标题',
    objective          TEXT NULL COMMENT '协作目标',
    orchestration_mode VARCHAR(24) NOT NULL DEFAULT 'MANUAL' COMMENT '编排模式',
    run_state          VARCHAR(16) NOT NULL DEFAULT 'idle' COMMENT '运行状态',
    created_at         TIMESTAMP(3) NOT NULL COMMENT '创建时间'
);

-- 房间参与者
CREATE TABLE IF NOT EXISTS room_participant (
    id                VARCHAR(64) PRIMARY KEY COMMENT '参与者记录标识',
    room_id           VARCHAR(64) NOT NULL COMMENT '所属房间',
    digital_human_id  VARCHAR(64) NOT NULL COMMENT '参与的数字人',
    room_role         VARCHAR(16) NOT NULL DEFAULT 'member' COMMENT '房间角色（member 等）',
    presence          VARCHAR(24) NOT NULL DEFAULT 'idle' COMMENT '在线/忙闲状态',
    active_task_label VARCHAR(255) NULL COMMENT '当前任务简述',
    joined_at         TIMESTAMP(3) NOT NULL COMMENT '加入时间',
    UNIQUE KEY uk_room_participant (room_id, digital_human_id),
    KEY idx_participant_room (room_id)
);

-- 协作任务
CREATE TABLE IF NOT EXISTS collaboration_task (
    id                VARCHAR(64) PRIMARY KEY COMMENT '协作任务标识',
    room_id           VARCHAR(64) NOT NULL COMMENT '所属房间',
    parent_task_id    VARCHAR(64) NULL COMMENT '父任务（子任务拆解）',
    title             VARCHAR(255) NOT NULL COMMENT '任务标题',
    instruction       TEXT NOT NULL COMMENT '任务指令',
    assigned_to       VARCHAR(64) NULL COMMENT '指派给的数字人',
    requested_by      VARCHAR(64) NOT NULL COMMENT '发起方',
    state             VARCHAR(24) NOT NULL DEFAULT 'READY' COMMENT '任务状态',
    depends_on        VARCHAR(512) NULL COMMENT '依赖的其他任务标识列表',
    input_artifacts   VARCHAR(512) NULL COMMENT '输入产物标识列表',
    output_artifacts  VARCHAR(512) NULL COMMENT '输出产物标识列表',
    remote_session_id VARCHAR(128) NULL COMMENT '远端会话标识（A2A 委派）',
    trace_id          VARCHAR(64) NOT NULL COMMENT '链路追踪 id',
    error_summary     VARCHAR(1024) NULL COMMENT '失败摘要',
    created_at        TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at        TIMESTAMP(3) NOT NULL COMMENT '更新时间',
    KEY idx_task_room_state (room_id, state)
);

-- 协作产物
CREATE TABLE IF NOT EXISTS collaboration_artifact (
    id          VARCHAR(64) PRIMARY KEY COMMENT '产物标识',
    room_id     VARCHAR(64) NOT NULL COMMENT '所属房间',
    task_id     VARCHAR(64) NULL COMMENT '产生该产物的任务',
    producer_id VARCHAR(64) NULL COMMENT '生产者（数字人）',
    kind        VARCHAR(24) NOT NULL COMMENT '产物类型',
    title       VARCHAR(255) NOT NULL COMMENT '产物标题',
    version     INT NOT NULL DEFAULT 1 COMMENT '版本号',
    content     TEXT NULL COMMENT '产物内容',
    trace_id    VARCHAR(64) NULL COMMENT '链路追踪 id',
    created_at  TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    KEY idx_artifact_room (room_id)
);

-- 统一房间事件日志（一切展示与恢复的源头）
CREATE TABLE IF NOT EXISTS room_event (
    id              VARCHAR(64) PRIMARY KEY COMMENT '事件标识',
    room_id         VARCHAR(64) NOT NULL COMMENT '所属房间（scope）',
    seq             BIGINT NOT NULL COMMENT '房间内单调递增序号（镜像会话事件轨设计）',
    task_id         VARCHAR(64) NULL COMMENT '关联协作任务',
    participant_id  VARCHAR(64) NULL COMMENT '关联参与者',
    digital_human_id VARCHAR(64) NULL COMMENT '关联数字人',
    event_type      VARCHAR(48) NOT NULL COMMENT '事件类型',
    occurred_at     TIMESTAMP(3) NOT NULL COMMENT '发生时间',
    visibility      VARCHAR(16) NOT NULL DEFAULT 'room' COMMENT '可见性（room 等）',
    payload         TEXT NULL COMMENT '事件载荷',
    trace_id        VARCHAR(64) NULL COMMENT '链路追踪 id',
    UNIQUE KEY uk_room_event_seq (room_id, seq),
    KEY idx_event_room_seq (room_id, seq),
    KEY idx_event_task (task_id)
);
