# 第 05 站 · 策略树、提示词与工作目录

> 阶段：P1 平台化 · 体量：M（1–2 天） · 前置：04

## 这一站完成什么

把“在 UseCase 里手写创建 Agent”改成项目固定的用例编排方式：`factory + node + DynamicContext`。  
同时补上 Prompt 装配、意图识别、Agent 生命周期和工作目录。

## 前置检查

- 04 站 ReAct 与工具可用
- 理解责任链：`doApply` 返回 null 继续，返回结果终止

## 任务清单

1. 在 `case.orchestration` 实现：
   - `StrategyHandler<I,C,O>`
   - `AbstractStrategyRouter<I,C,O>`：`doApply` + `getNext` + `router`
   - `CasePipeline<I,C,O>`：`of(name, root, contextFactory)` + `execute`
   - `CasePipelineFactory` + `NoContext`
2. 实现 `AgentMessageDynamicContext`：
   - `agent`、`userMessage`、`intent`
   - `deltaSink`、`reasoningSink`、`toolCallEventSink`、`finishSink`
   - `attachSinks(agent)`
3. 实现四个节点：
   - `AgentResolveNode`：按 `agentId` 复用/创建 Agent；解析模型、cwd、审批模式；实例缺失时尝试从事件日志恢复
   - `AgentIntentNode`：纯正则分类 CHAT / CODE_QUESTION / TASK_EXECUTION / CLARIFICATION
   - `AgentDispatchNode`：构造 UserMessage，任务类消息注入“先用工具”指令，投递 `NEXT_TURN`
   - `AgentCollectNode`：`whenIdle().join()`，遍历 SessionLog，按 callId 合并 ToolCall/ToolResult，返回 DTO
4. 实现 `AgentMessageFactory`：
   - `pipeline()`：非流式
   - `streamingPipeline(...)`：绑定 sinks
5. 实现 `SystemPromptAssembler`：
   - 基础身份 + 角色规范
   - 运行时上下文（cwd）
   - 可注册的 PromptSection（后续插件用）
6. 实现 `AgentRunLifecycle`：
   - `create`、`resume`、`get`、`cancel`、`updateCwd`、`configureApprovalMode`
7. 实现 `WorkspaceRegistryService`：
   - 工作区列表、创建、重命名、删除、排序
   - `cwd` 解析与校验
8. 把 `AgentUseCase` 改为委托 `AgentMessageFactory`，不再自己拼 Agent。

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `AbstractStrategyRouter` | 节点基类 |
| `CasePipeline` | 统一执行入口 |
| `AgentMessageFactory` | 对话策略树工厂 |
| `AgentResolveNode` | Agent 生命周期 |
| `AgentIntentNode` | 意图分类 |
| `AgentDispatchNode` | 消息投递 |
| `AgentCollectNode` | 结果收集 |
| `SystemPromptAssembler` | 提示词装配 |
| `WorkspaceRegistryService` | 工作区与 cwd |

## 最小实现 vs 正式实现

- **最小实现**：四个节点 + 基础 Prompt + 单工作区。
- **正式实现**：Agent 事件恢复、sandboxRoots 注入、插件 PromptSection、工作区 CRUD/排序、`ScopedSystemPromptAssembler` 的上下文注册。

## 验收

1. 正常对话仍然是同一条 SSE 接口，但日志应出现：
   ```text
   [Resolve] ...
   [Intent] ... intent=TASK_EXECUTION
   [Dispatch] ...
   [Collect] ...
   ```
2. 同一 `agentId` 连续发两条消息，第二条复用同一个 Agent。
3. 不同 `cwd` 下让 Agent 写文件，文件落到对应目录。
4. 纯问候 `"你好"` 识别为 `CHAT`，不注入“先用工具”指令。

## 对照源码

- `deepseek-harness-java-case/src/main/java/cn/xiaofuge/deepseek/harness/cases/orchestration/`
- `deepseek-harness-java-case/src/main/java/cn/xiaofuge/deepseek/harness/cases/agent/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/service/ScopedSystemPromptAssembler.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/contract/inbound/AgentRunLifecycle.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/service/workspace/WorkspaceRegistryService.java`

## 常见坑

- **`getNext()` 不要写成循环**：每个节点只返回下一个节点，由 `apply` 递归推进。
- **上下文要能清理**：`CasePipeline` 对 `AutoCloseable` 上下文做 finally 清理。
- **流式 sink 是单实例字段**：同一 Agent 同时接两条流式请求会互相覆盖，Resolve 节点要拒绝。
- **cwd 同步**：同一 `agentId` 切换工作区时，必须更新 Agent 的 cwd。
- **意图分类只是启发式**：不要把它写成“必须准确”的分类器，它只影响提示词，不决定权限。

## 完成标志

- [ ] 对话走四节点管线
- [ ] 日志能看到 Resolve/Intent/Dispatch/Collect
- [ ] 同一 agentId 复用实例
- [ ] 不同 cwd 写文件正确
- [ ] 有 `CasePipeline` / Intent 分类单元测试
- [ ] 打 tag：`stage-05`
