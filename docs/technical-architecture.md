# 技术架构方案 — 从 0 重建的底稿

> **性质**：这不是凭空设计的需求书，而是从原项目（`deepseek-harness-java`）提取的**架构读书笔记**。
> 依据：原项目 `README.md` 第 3~12 章、`docs/md/domain-design.md`、`docs/md/plugin-design-implementation.md`，以及对源码的直接核对（2026-11，三个方向：请求主流程 / 插件机制 / 数据模型）。
> **用法**：先通读建立空间感 → 每完成一站回来修订 → 最终目标是合上文档能白板画出第 2 节的图。
> 路径约定：下文 `DOM:` `CASE:` `INF:` `TRG:` 均指原项目对应模块的 `src/main/java/cn/xiaofuge/deepseek/harness/` 下的路径。

---

## 0. 这个项目是什么

**一句话**：单实例可运行的 **Agent Harness（智能体运行时）**——让 LLM 驱动的智能体通过 ReAct 循环自主调用工具完成任务，全过程事件溯源落库，能力通过插件扩展，风险通过审批/沙箱治理。

它不是聊天机器人（聊天只是退化形态），核心是让模型**动手**：操作文件系统、执行 Shell、搜索网页、调用插件与 MCP 工具。

两条业务主线：

| 主线 | 链路 |
| --- | --- |
| **对话主线** | 用户消息 → 策略树编排 → ReactLoopAgent → LLM 流式 → 工具执行 → 结果回填 → SSE 推流 |
| **任务主线** | 任务提交 → 权限矩阵评估 → 审批 → 入队 → 执行（复用对话主线的 Agent 链路）→ 完成/失败 |

---

## 1. 五条核心设计决策（整个架构的"为什么"）

1. **六边形架构，依赖严格单向**
   `app → trigger → case → domain ← infrastructure`，`domain → types`。
   Domain 定义端口（Port），Infrastructure 落地适配器（Adapter）。所以 LLM 换供应商、存储换 MySQL/H2/JSONL、沙箱换本地/E2B，都不动领域逻辑。Domain 不依赖 Spring 具体能力（仅 provided 级别的注解），可独立测试（各模块有 ArchUnit 测试守护依赖方向）。

2. **事件溯源：会话的真相是一条追加事件流**
   `SessionLog` 是内存 WAL，异步镜像到 `harness_session_event_log` 表（每会话单调 `seq`，UNIQUE 约束给全序+幂等+增量拉取）。UI 渲染、会话恢复、审计、todo 状态——全都是事件流的投影。**不存"当前状态"，只存"发生了什么"**。

3. **编排 = 策略树（责任链），而不是大 Service**
   case 层每个用例是一棵 `CasePipeline`：`*Factory` 建树，`*Node` 做一步并 `getNext()` 路由，`DynamicContext` 传状态。两种固定形态：薄用例（单步，直接 `*CaseImpl`）和流程用例（跨领域分支，走 factory+node）。避免"所有东西都套策略树"和"所有东西都写大方法"两个极端。

4. **Agent 是有状态的运行时对象，不是一次函数调用**
   `ReactLoopAgent` 是聚合根级服务：持有 Inbox（收件箱）、Phase 状态机、SessionLog、工具注册表。消息投进 Inbox，异步 kick 驱动 turn/step 循环。所以才能支持：会话复用（agentId）、恢复（从事件重建）、取消（协作式中断）、运行中再投递消息。

5. **扩展面统一收口到"工具"这一个抽象**
   内置工具（fs/shell/web）、Java 插件工具（`plugin__<id>__<name>`）、MCP 工具（`mcp__<server>__<name>`）最终都是 `ToolDefinition`，进同一个注册表，走同一条 `ToolCallExecutor` 链（Hook → 审批 → 执行 → 事件回填）。**扩展的是工具，不是循环**——ReactLoopAgent 永远不知道工具从哪来。

---

## 2. 分层架构与一次流式对话的完整链路

### 2.1 模块职责

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `types` | 插件 SPI 契约 + 工具/消息值对象。**不依赖 Spring**，外部插件只依赖它 | 无 |
| `domain` | 领域核心：agent 运行时、LLM 端口、会话事件溯源、工具执行器、task/goal/plugin 等子域。只定义端口 | types |
| `api` | 纯协议层：门面接口 + DTO + Response 包装 | 无 |
| `case` | 用例编排（策略树），调 domain 服务 | domain、api |
| `infrastructure` | 端口落地 + 组合根：LLM 适配器、JDBC 事件存储、FS/Shell/MCP、全部 `@Bean` 装配 | domain |
| `trigger` | HTTP 入口：Controller（REST/SSE）、api 实现、SSE 推送、鉴权 | api、case、infrastructure |
| `app` | Spring Boot 启动器、配置、schema、预置插件装载 | trigger、infrastructure |

### 2.2 `POST /api/agent/stream` 全链路（背下这张图）

```text
浏览器 SSE 请求
  → AgentController                (TRG:/trigger/http)
  → AgentStreamApi                 (TRG:/trigger/service/stream，建 SseEmitter + 守护线程池)
  → AgentUseCase.sendMessageStreaming   (CASE:/cases/agent，四个 sink 塞进上下文)
  → AgentMessageFactory.streamingPipeline → CasePipeline.execute
      ① AgentResolveNode   按 agentId 复用 Agent；没有则先尝试从事件存储恢复，再不行才新建（AgentRunFactory.wireAgent）
      ② AgentIntentNode    纯正则分类 CHAT / CODE_QUESTION / TASK_EXECUTION / CLARIFICATION
      ③ AgentDispatchNode  按意图增强文本，Message.createUser → agent.send(msg, NEXT_TURN, true) 投递 Inbox 并唤醒
      ④ AgentCollectNode   agent.whenIdle().join() 等待完成，把 SessionLog 展平成响应 DTO
  ‖（另一条线程）
  ReactLoopAgent.kick() 异步驱动：
      turn()：写 TURN_START → 压缩检查 → 循环 step（上限 50），只有首步从 Inbox claim 消息
      step()：SystemPromptAssembler 组提示词 → buildRequest（128K 估算裁剪历史）
              → ILlmRuntimePort.stream() → Flow.Publisher<StreamChunk>
              → TextDelta 实时推 SSE chunk + 攒批落 assistantChunk 事件
              → BlockAssembler 把 token 流拼成结构化 blocks → 写 ASSISTANT_MESSAGE
              → 无工具调用 → Completed；有 → ToolCallExecutor（Hook→审批→执行→TOOL_CALL/TOOL_RESULT 事件）→ 返回 null 续步
      收尾：TurnEnd → Phase 回 Idle → 唤醒 Collect
  → Collect 组装响应 → SSE 推 done（权威全量消息）→ emitter.complete()
```

关键机制速记：

- **SSE 实时推送不走事件表**——它是 `step()` 里 sink 回调的旁路；事件表负责持久真相，SSE 负责低延迟渲染。done 帧带全量消息供前端对账兜底。
- **事件落库是异步单线程保序镜像**（`DOM:/agent/service/run/PersistingSessionLog.java`）——同步写 MySQL（单 turn 9+ 次写 × 数百 ms）会拖垮流式收尾；代价是崩溃可能丢毫秒级尾部事件。
- **SSE 增量 60ms 聚合**（DeltaBatch）+ 15s keepalive 心跳，防逐 token 推送闪烁与看门狗掐断。
- **恢复优先于新建**：Resolve 节点先 `listHeaders` 匹配 → `readAll` 事件 → 重建 SessionLog → `resume`；失败才 create。
- **取消是协作式**：`AtomicBoolean` abort 标记 + 100ms 轮询，各安全点自行退出，不暴力杀线程。

### 2.3 LLM 侧（端口与多协议路由）

- 端口：`DOM:/llm/adapter/port/ILlmRuntimePort.java` → `stream(GenerateOptions) : Flow.Publisher<StreamChunk>`
- `StreamChunk` 8 个变体：`BlockStart / TextDelta / ReasoningDelta / ToolCallDelta / BlockEnd / Usage / Finish / Retry`
- `BlockAssembler`（`DOM:/llm/service/BlockAssembler.java`）：无状态拼装器，按 index 把 delta 累积成 text/reasoning/tool-call 块，`finish()` 产出结构化消息——**token 流 → assistant 消息的唯一路径**
- 适配器：`DeepSeekAdapter`（JDK HttpClient + SSE 解析 + 重试 + 非流式兜底）→ `OpenAiCompatibleAdapter` 继承之 → `AnthropicAdapter` 独立协议；`LlmAdapterFactory` + `ProtocolRoutingAdapter` 按渠道 `protocol` 动态路由，**新增渠道零新 Bean**

---

## 3. 哪些进底层，哪些做插件（判定标准）

### 3.1 三问判定法

对任何能力问三个问题：

1. **删掉它，ReAct 循环还能跑吗？** 能 → 插件候选（如天气查询）；不能 → 底层（如工具执行器本身）。
2. **它带业务语义吗？** 带（查订单、操作 MySQL）→ 插件；通用机制（超时、审批、沙箱）→ 底层。
3. **它需要被统一治理吗（审计/审批/生命周期回收）？** 需要且是"治理框架本身" → 底层；需要且是"被治理的能力" → 插件（挂上框架即可）。

### 3.2 原版的实际分法

| 进底层（domain + infrastructure） | 做成插件 / 扩展面 |
| --- | --- |
| ReAct 循环、Inbox、Phase 状态机 | 业务性工具（示例：天气查询、文件摘要） |
| 会话事件溯源 + 回放 | Node 生态能力（sidecar JSON-RPC） |
| LLM 端口 + 各协议适配器 | 第三方工具服务（MCP Server，零代码接入） |
| **内置基座工具**：fs_*、shell_execute、web_search/fetch、ask_user_question、TodoWrite | 企业内部系统能力（Java 插件） |
| ToolCallExecutor + Hook + 审批门 + 沙箱（治理框架） | —— |
| 任务/审批/目标等子域 | —— |
| **插件框架本身**（加载、生命周期、桥接、注册表、回收） | —— |

### 3.3 两个关键洞察

1. **"支持插件"本身是底层能力，"具体插件"才是外围。** 插件框架（loader/lifecycle/bridge/registry）完整地住在 domain+infrastructure 里，是第 12 站才建的核心模块。
2. **基座工具不做成插件**（fs/shell 直接内置），因为它们是"Agent 能动手"的最小集合，且需要最深的沙箱/审批集成。分界线不是"是否可扩展"，而是**是否为循环与治理的依赖项**。

---

## 4. 插件机制怎么实现（一个 jar 的旅程）

### 4.1 三种扩展方式

| 方式 | 机制 | 适合 |
| --- | --- | --- |
| **Java Native Plugin** | 进程内 `URLClassLoader` 隔离加载，直调 | 类型安全、企业内网 Java 能力 |
| **Node Bridge Plugin** | sidecar 子进程 + stdin/stdout JSON-RPC 2.0 | 复用 Node 生态 |
| **MCP Server** | stdio/HTTP 客户端直连，**零插件代码**，纯配置 | 第三方现成工具 |

### 4.2 Java 插件全链路

```text
① 安装 install    HTTP /api/harness/plugins/install 或 harness.yml preset（启动时 PresetPluginLoader 扫描+状态对账）
                  → PluginRegistryService.installPlugin → ArtifactInstallerPort 拷贝 jar 到 install-root
                  → 落库 harness_plugin_installation，状态 REGISTERED
② 激活 activate   PluginRuntimePipeline：Resolve → InspectBridge（识别 runtimeType，生成桥接计划）→ BindBridge → Run
                  → JAVA_NATIVE：JavaPluginLoader.load()
                      · 每插件独立 URLClassLoader（parent=宿主 context loader：契约共享、实现隔离）
                      · 读 META-INF/plugin.yaml（id/name/version 必填）
                      · 入口实例化：plugin.yaml entrypoint 反射优先 → 回退 ServiceLoader（META-INF/services/…JavaHarnessPlugin）
                  → plugin.configure(PluginContext)：注册工具/系统提示词/Hook/事件订阅/资源释放
③ 注册            工具包装为 PluginToolDefinition，命名 plugin__<pluginId>__<toolName>，进共享 ToolRegistry
                  + 向系统提示词注册 order=150 的能力说明段（让模型知道有这工具）
④ 调用            Agent 循环按名称 lookup → 与内置工具同一条 ToolCallExecutor 链（Hook→审批→执行→事件）
⑤ 回收            disable/uninstall → onStop() → 逆序回收全部注册项（工具/提示词/Hook）→ 关 ClassLoader
                  热重载：WatchService 监听 install-root，jar 变更触发 stop→start 重建
```

### 4.3 契约清单（types 模块，插件作者看到的世界）

| 类 | 作用 |
| --- | --- |
| `JavaHarnessPlugin` / `AbstractHarnessPlugin` | SPI 接口/基类：`pluginId()` `tools()` `configure(ctx)` `onStart/onStop` |
| `PluginContext` | 注册边界：`registerTool / registerSystemPrompt / registerHook / subscribe / emit / registerDisposer / getConfig`。**宿主持有全部注册项，停止时逆序回收** |
| `AbstractTool` | 工具基类：子类只写 `name/description/parameters/run`；基类兜底参数转换、异常、Schema 构建器、`ok()/fail()` |
| `ToolDefinition` / `ToolExecutionResult` | 工具契约：执行返回 `CompletableFuture`，结果 sealed（Success/Failure） |
| `PluginHook` / `PluginHookResult` | Hook 回调，`block()/deny()` 可阻断动作 |
| `PluginManifest` / `PluginLifecycleState` | plugin.yaml 解析 / 进程内状态机 |

### 4.4 双状态机（容易忽略的设计）

| 层 | 状态 | 载体 |
| --- | --- | --- |
| 持久化登记 | `REGISTERED → ACTIVE → DISABLED / FAILED / UNINSTALLED` | `harness_plugin_installation` 表 |
| 进程内运行 | `LOADING → ACTIVE → UNLOADING → DISPOSED / FAILED` | 内存，重启即重建（preset 对账重新拉起） |

铁律：**运行状态必须来自运行时适配器查询，不能由登记状态推断**；激活要求"状态+进程双确认"（run 失败则登记状态回写 FAILED），避免"登记 ACTIVE 但工具实际不可用"。

### 4.5 两级工具注册表

- **共享级单例**：插件工具注册进它，对全部 Agent 可见（生命周期跟插件走）。
- **会话级**：每次 `wireAgent` 新建，装该 Agent 的**内置工具**（按 agentId+cwd 定制），`CompositeToolRegistry(local, shared)` 组合——lookup 时 local 优先，同名时内置覆盖插件。
- 为什么这么分：插件是全局资源（装一次处处可用），内置工具是每-Agent 资源（依赖会话 cwd、权限）。

### 4.6 隔离与治理的真相

- Java 插件是**类隔离不是安全沙箱**：代码就在宿主 JVM 里跑。真正的管控是审批门（`harness.approval.required-tools` 含 `plugin.run`）+ Hook + 权限矩阵。
- Node 插件是进程隔离；MCP 是服务隔离。**进程边界 = 安全边界的大致分级**。
- 插件执行的隔离陷阱与治理边界是第 06/12 站的重点实操内容。

### 4.7 最小 Java 插件 = 5 个文件

```text
pom.xml                                    ← 只依赖 deepseek-harness-java-types（provided）
<PluginClass>.java                         ← extends AbstractHarnessPlugin
<ToolClass>.java                           ← extends AbstractTool
META-INF/plugin.yaml                       ← id/name/version + entrypoint(类全限定名)
META-INF/services/cn.xiaofuge.deepseek.harness.domain.spi.JavaHarnessPlugin   ← 一行：实现类全限定名
```

`pluginId` 字符集 `[A-Za-z0-9][A-Za-z0-9._-]*`（它要进工具名和安装路径）；`pluginId`、plugin.yaml 的 `id`、SPI 返回的 `pluginId()` 三者必须一致。

---

## 5. 数据模型：要设计哪些表

原版 `schema.sql` 共 **19 张表**。从 0 写时按域分组建（第 07 站起逐个补）：

### 5.1 会话与事件（核心）

| 表 | 用途 | 关键点 |
| --- | --- | --- |
| `harness_session_header` | 会话头（可变元数据快照） | upsert + version；列表页只扫它 |
| `harness_session_event_log` | **事件溯源主表** | `UNIQUE(session_id, seq)`；payload JSON；`surface_op` 记录渲染意图（append/replace）；`source_event_seqs` 记事件因果 |
| `harness_session` | 会话注册表 + 审批状态机 | status 承载 `QUEUED/PENDING_APPROVAL/...` |
| `harness_task` | 任务队列 | submit 入队，执行服务回写状态 |
| `harness_session_event` | 旧轨事件表（无 seq，字符串事件） | 历史演进产物；**从 0 写可不做**（登记 Deviation） |

### 5.2 配置与插件

| 表 | 用途 | 关键点 |
| --- | --- | --- |
| `harness_model_setting` | 模型渠道配置 | `api_key_ref` 存引用不存明文，真密钥走 CredentialsYamlStore；`active` 全局唯一 |
| `harness_plugin_installation` | 插件登记 | runtime_type / source_path / entrypoint / status |
| `harness_plugin_runtime_binding` | 运行绑定探活 | bridge_ready / runtime_command |
| `harness_plugin_config` | 插件分层配置 | PK (plugin_id, key, layer) |
| `harness_tool_catalog` / `harness_tool_profile_binding` | 工具目录 + 按 profile 启用 | `requires_approval` 开关落在这里 |

### 5.3 其他

`harness_goal_state`（目标快照，带 revision 乐观锁）；数字人协作域 7 张（`digital_human*`、`collaboration_room/task/artifact`、`room_event`——第 15 站，`room_event` 完全镜像会话事件表的 (scope, seq) 设计）。

### 5.4 为什么这么设计（面试级答案）

| 设计 | 原因 |
| --- | --- |
| 事件单独成表带 seq | 追加不可变事件流 = 全序 + 幂等 + 增量拉取（readAfter）；恢复=全量回放，快照只是优化 |
| header 与 event 分表 | 头是可变热行，事件是冷追加流；列表查询不扫事件表 |
| **审批不建表（刻意）** | 持久事实已由 session 状态机 + 事件记录；"正在等人点按钮"是进程内回调（`RuntimeApprovalBroker` 的 `ConcurrentMap<String, CompletableFuture>`），落库只会引入跨进程唤醒复杂度 |
| **todo 不建表（刻意）** | todo 短小且总随会话回放，`TodoWrite` 事件即可，状态由回放得出 |
| goal 建快照表 | 跨回合长命状态，需要 revision 乐观锁语义 |
| AgentRun 运行态不落库 | 进程内执行上下文；可观察步骤已全部事件化，对象可随时重建 |
| jobs/schedule 用 InMemory | standalone 轻量实现，端口化设计天然可换 JDBC（原版已知欠账） |
| 双轨事件并存 | 绞杀者模式：旧轨承载任务流，新轨承载对话流，`ConversationQueryService` 合并、`LegacyEventAdapter` 渐进迁移——**这是演进痕迹，不是理想设计** |

---

## 6. 安全与治理（从 0 写时必须知道的原版现状）

- 沙箱三档：`READ_ONLY / WORKSPACE_WRITE / DANGER_FULL_ACCESS`，本地默认 WORKSPACE_WRITE（shell cwd 与写操作限制在工作区根）。
- 审批链路：提交期走权限矩阵（`PermissionCheckNode`）+ 审批决策；运行期有 `MatrixRuntimeApprovalGate`，但**原版 AgentRunFactory 默认 approvalBroker=null、工具执行器 allowAll——运行期拦截实际未启用**（README §14 自述的坑）。从 0 写到第 06 站时，把这条真正接上是超越原版的改进点。
- `shell_execute` 原版零白名单直接 `/bin/sh -c`，仅靠审批兜底——写的时候要意识到这是已知风险位。
- Hook 链：插件 Hook 先执行、Shell Hook 后执行，限制性合并（任一 BLOCK/DENY 即阻断）。

---

## 7. 从 0 写的推进顺序 = 架构依赖序

15 站就是"把架构图一块块变成可运行代码"，每一站都在回答一次"什么进底层"：

| 站 | 建的架构块 | 对应本档章节 |
| --- | --- | --- |
| 01 | 组合根：boot + 配置 | §2.1 |
| 02 | 领域模型 + LLM 端口与适配 | §1 决策1/2、§2.3 |
| 03 | 事件溯源 + SSE 推送 | §1 决策2、§2.2 |
| 04 | **ReactLoopAgent + 工具执行器**（全项目核心） | §1 决策4/5 |
| 05 | case 策略树 + 提示词装配 | §1 决策3 |
| 06 | 审批 + Hook + 沙箱 | §6 |
| 07 | JDBC 落库 + 会话恢复回放 | §5 |
| 08 | 任务生命周期（任务主线） | §0 |
| 09 | 模型设置 + 多协议路由 | §2.3 |
| 10 | workspace / terminal / jobs | §3 基座工具 |
| 11 | goal / plan / todo / workflow | §5.4 |
| 12 | **Java 插件 SPI**（插件框架进底层） | §4 |
| 13 | Node Bridge / MCP / skills / 子代理 | §4.1 |
| 14 | Web 控制台 | §0 接入层 |
| 15 | 数字人协作 + A2A | §5.3 |

---

## 8. 这份文档怎么用 + 自测题

**用法**：它是你写代码前的地图，不是替换思考的答案。每站动手前重读对应节；写完 diff 后把"我理解错了什么"批注回来；文档随你的理解升级。

**自测题（能口头回答才算过关，答案都在原文里）**：

1. 为什么 SSE 推送不走事件存储，而是 step() 里的旁路？两条通道各自的职责是什么？
2. 事件落库为什么异步？付出了什么代价？如果要求强持久，你会怎么改、代价是什么？
3. 为什么审批不建表、todo 不建表，goal 却建表？
4. 插件工具为什么进共享注册表，内置工具为什么进会话级？如果内置工具也进共享级会坏在哪？
5. `UNIQUE(session_id, seq)` 一条约束同时买到了哪三样能力？
6. "恢复 Agent"的完整判定顺序是什么？为什么先恢复后新建？
7. Java 插件的 ClassLoader 隔离防住了什么、防不住什么？
8. 如果这个单实例运行时要扩到多实例，哪三处先撑不住？（提示：README §13/§14）

**我的疑问（读到哪填到哪）**：

- （空——自己的问题自己记，记下来的问题就是下一站的方向）
