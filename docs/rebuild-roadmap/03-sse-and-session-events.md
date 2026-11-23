# 第 03 站 · SSE 流式对话与会话事件日志

> 阶段：P0 可运行内核 · 体量：M（1–2 天） · 前置：02

## 这一站完成什么

把一次性对话升级成 `POST /api/agent/stream` 的 SSE 流；同时引入 `SessionEvent` / `SessionLog`，让“对话过程”变成可回放的事件序列。

## 前置检查

- 02 站能返回一次性文本
- 了解 SSE 基本格式：`event: name` + `data: json` + 空行

## 任务清单

1. 在 `domain.session.event` 定义：
   - `SessionEvent` sealed 接口
   - 先实现 8 个变体：`TurnStart`、`TurnEnd`、`StepStart`、`StepEnd`、`UserMessage`、`AssistantChunk`、`AssistantMessage`、`Generic`
   - 后续再加 `ToolCall`、`ToolResult`、`TodoWrite`、`RequestHeader`、`RequestContext`、`SessionEndSeed`、`PlanModeChange`、`AgentInboxSpliced`
2. 实现 `SessionLog`：
   - 内存追加式 WAL
   - `append(event)`、`events()`、`size()`、`sessionId()`
   - `seq` 单调递增
3. 实现 `SessionEventFactory`：
   - 统一创建事件，避免各处手写 `seq/time`
4. 实现 `SurfaceProjector`：
   - 把 `SessionEvent` 投影成 `SurfaceOp`（Append / Replace）
   - 先只处理 UserMessage、AssistantChunk、AssistantMessage、ToolCall、ToolResult
5. 定义 `ISessionEventStore` 端口 + `InMemorySessionEventStore` 实现：
   - `append`、`readAll`、`listHeaders`
   - 这一站先不落库，验证接口形状
6. 在 `trigger.service.stream` 实现 `AgentStreamApi`：
   - `SseEmitter(0)`，不设硬超时
   - 事件：`meta`、`chunk`、`reasoning`、`finish`、`done`、`error`
   - 15s `:keepalive` comment
   - 60ms `DeltaBatch` 合批
7. `AgentController` 增加 `POST /api/agent/stream`，返回 `SseEmitter`。
8. 在 `case.AgentUseCase` 增加流式方法：
   - 追加 `UserMessage` → `TurnStart` → 多次 `AssistantChunk` → `AssistantMessage` → `TurnEnd`
   - 每次 chunk 调 `deltaSink`
   - 最后返回完整 DTO

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `SessionEvent` | sealed 事件契约 |
| `SessionLog` | 内存事件 WAL |
| `SurfaceProjector` | 事件 → 前端可渲染操作 |
| `ISessionEventStore` | 事件持久化端口 |
| `AgentStreamApi` | SSE 协议适配 |
| `DeltaBatch` | 60ms 增量合批 |

## 最小实现 vs 正式实现

- **最小实现**：8 个事件、内存 Store、只处理文本 chunk。
- **正式实现**：16 个事件、JDBC Store、SurfaceProjector 处理工具卡片、`PersistingSessionLog` 逐事件落库、`AgentCollectNode` 从事件投影消息。

## 验收

```bash
curl -N -X POST http://localhost:8090/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{"agentId":"learn-03","message":"写一句关于春天的话"}'
```

预期事件顺序：

```text
event: meta
event: chunk        （多次）
event: finish
event: done         （含 messages）
```

再验证事件日志：

```bash
# 最小版可以先加一个 debug 接口，或看日志
GET /api/debug/sessions/{sessionId}/events
```

预期：事件 seq 递增，最后一条是 `TurnEnd`。

## 对照源码

- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/session/event/model/entity/SessionEvent.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/session/event/model/entity/SessionLog.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/session/event/service/SurfaceProjector.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/service/stream/AgentStreamApi.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/service/stream/DeltaBatch.java`

## 常见坑

- **不要把 `step_break` 拼进 chunk 文本**：它必须是独立事件，否则多步工具调用会把文本和工具卡片混在一个气泡里。
- **SSE 超时要设为 0 或足够大**：Agent 单轮可能远超 5 分钟；靠 15s 心跳保活。
- **chunk 要合批**：逐 token 推 SSE 会让前端逐帧重渲染，60ms 窗口是正式实现的选择。
- **事件 seq 必须单线程递增**：`SessionLog` 的追加要加锁或串行化。
- **投影不是消息表**：`SurfaceProjector` 只负责“如何显示”，不要在这里做业务判断。

## 完成标志

- [ ] `curl -N /api/agent/stream` 收到 meta/chunk/finish/done
- [ ] 事件顺序正确，seq 单调递增
- [ ] 有 `SessionLog` 单元测试
- [ ] 前端/curl 能区分 chunk 和 done
- [ ] 打 tag：`stage-03`
