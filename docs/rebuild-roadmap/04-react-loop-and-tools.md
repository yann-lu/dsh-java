# 第 04 站 · ReAct 循环与工具系统

> 阶段：P0 可运行内核 · 体量：L（2–3 天） · 前置：03

## 这一站完成什么

让 Agent 真正“动手”：模型请求工具 → 执行工具 → 结果回填 → 模型继续推理。  
这一站完成后，Harness 才从“聊天机器人”变成“Agent 运行时”。

## 前置检查

- 03 站 SSE 与事件日志可用
- 本地有 `/bin/sh`（macOS/Linux）或 `cmd`（Windows）
- 理解 ReAct：Reason → Act → Observe → 下一步

## 任务清单

1. 实现 `ToolRegistry`：
   - `register(ToolDefinition)`、`unregister(name)`、`lookup(name)`、`list()`
   - `InMemoryToolRegistry` + `CompositeToolRegistry`（会话级 + 共享级）
2. 实现 `ToolCallExecutor`：
   - 参数解析（先做 identity parser，后换 Jackson）
   - PRE_TOOL_USE Hook（先留空实现）
   - 单工具默认 300s 超时
   - 每步最多 10 个并行调用
   - `ToolLifecycleEvent`：call / result 两个相位
   - 结果回填 `SessionLog`：`ToolCall` + `ToolResult`
   - 收集本回合写文件产物
3. 实现最小 `AgentToolCatalog`：
   - `fs_read`、`fs_write`、`fs_search`
   - `str_replace_editor`（可后置）
   - `shell_execute`
   - `ask_user_question`（可先返回固定答案）
   - 每个工具继承 `AbstractTool`，实现 `name/description/parameters/run`
4. 实现 `ReactLoopAgent`：
   - `send(message, NEXT_TURN, wakeup)` 投递 Inbox
   - `kick()` 异步驱动
   - turn 循环：最多 50 步
   - 每步：组装请求 → LLM 流 → 收集 `ToolCallBlock` → 有工具则执行 → `midTurnContinuation=true` → 下一步
   - `max_tokens` 截断最多续写 4 次
   - `Phase` = Idle / Running / Maintenance
   - 取消：`AtomicBoolean` + 100ms 轮询
5. 实现 `AgentRunFactory`：
   - 创建 `ReactLoopAgent` + `ToolCallExecutor`
   - 注入 `ILlmRuntimePort`、`ToolRegistry`、`SessionLog`
6. SSE 增加工具事件：
   - `step_break`：工具调用开始
   - `tool_result`：工具结束（含 status/result/duration）
7. 给 `ToolCallExecutor` 加单元测试：
   - 正常执行
   - 超时
   - 工具不存在
   - 结果事件顺序

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `ToolRegistry` | 工具目录 |
| `ToolCallExecutor` | 统一执行链 |
| `AgentToolCatalog` | 按会话装配内置工具 |
| `ReactLoopAgent` | ReAct 驱动器 |
| `AgentRunFactory` | 运行时组合根 |
| `ToolLifecycleEvent` | SSE 工具事件来源 |

## 最小实现 vs 正式实现

- **最小实现**：fs_read / fs_write / shell_execute + 串行工具调用。
- **正式实现**：10 个并行、300s 超时、Hook、审批门禁、产物收集、`fs_grep` / `fs_edit` / `fs_read_image` / web / terminal / job / goal / todo / schedule / skill / subagent 工具族。

## 验收

准备一个文件：

```bash
echo "DeepSeek Harness 是一个 Agent 运行时。" > /tmp/dsh-learn.txt
```

请求：

```bash
curl -N -X POST http://localhost:8090/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{"agentId":"learn-04","message":"读取 /tmp/dsh-learn.txt 并总结成一句话","cwd":"/tmp"}'
```

预期：

1. 出现 `step_break`，`toolName=fs_read`
2. 出现 `tool_result`，`status=success`
3. 继续出现 `chunk`，最后 `done`
4. 回复内容引用了文件里的信息

再验证 Shell：

```bash
curl -N -X POST http://localhost:8090/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{"agentId":"learn-04b","message":"用 shell 执行 pwd 并告诉我结果","cwd":"/tmp"}'
```

## 对照源码

- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/service/run/ReactLoopAgent.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/tool/service/ToolCallExecutor.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/service/run/tool/AgentToolCatalog.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/service/run/AgentRunFactory.java`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/shell/LocalShellExecutor.java`

## 常见坑

- **工具结果必须回到模型上下文**：执行完要 append `ToolCall` + `ToolResult`，并设置 `midTurnContinuation=true`，否则下一步会以为 inbox 为空而结束。
- **不要无限循环**：50 步上限是安全阀；每步都要检查取消标记。
- **工具超时要在线程池里执行**：`shell_execute` 可能阻塞，直接在驱动线程里跑会让超时失效。
- **工具名不要冲突**：内置工具直接用 `fs_read`；插件工具加 `plugin__` 前缀。
- **工具失败不要抛异常终止 turn**：返回 `ToolExecutionResult` 失败，让模型看到结构化错误。

## 完成标志

- [ ] Agent 能调用 `fs_read` 并基于文件内容回答
- [ ] Agent 能调用 `shell_execute`
- [ ] SSE 能看到 `step_break` / `tool_result`
- [ ] 工具超时/不存在有明确错误
- [ ] `ToolCallExecutor` 有单元测试
- [ ] 打 tag：`stage-04`
