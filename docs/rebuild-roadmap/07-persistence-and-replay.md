# 第 07 站 · 持久化、恢复与上下文压缩

> 阶段：P1 平台化 · 体量：L（2–3 天） · 前置：06

## 这一站完成什么

让会话“不丢”：

- 事件逐条落库
- 应用重启后按 `agentId` 恢复会话
- 控制台能查询会话、消息、事件
- 长会话超过阈值时自动压缩

## 前置检查

- 03 站 `SessionEvent` / `SessionLog` 可用
- 05 站 Agent 生命周期可用
- 06 站审批与工具链稳定

## 任务清单

1. 设计持久化表（先做最小 5 张，后续再补）：
   - `harness_session`
   - `harness_session_header`
   - `harness_session_event`
   - `harness_session_event_log`
   - `harness_task`
2. 写 DAO + PO + Repository：
   - `IHarnessSessionDao` / `ISessionEventDao` / `ISessionEventRowDao`
   - `SessionEventPO` / `SessionEventRowPO` / `SessionHeaderRowPO`
   - `HarnessSessionRepository` / `SessionEventRepository`
   - 注解 SQL 即可，不必上 XML
3. 实现 `JdbcSessionEventStore implements ISessionEventStore`：
   - `append(sessionId, event)`
   - `readAll(sessionId)`
   - `listHeaders(limit)`
   - `seq` 由存储层保证连续
4. 实现 `PersistingSessionLog extends SessionLog`：
   - 内存追加 + 同步落库
   - 恢复时 `restore(events)`
5. 实现 `SurfaceProjector` 完整投影：
   - `Append` / `Replace`
   - UserMessage / AssistantMessage / AssistantChunk / ToolCall / ToolResult
6. 实现 `ConversationQueryService`：
   - 会话列表、会话详情、消息列表、事件查询
   - 对应 `HarnessConsoleQueryController`
7. 实现 `BasicCompactionEngine`：
   - 压力阈值 `harness.agent.compaction.pressure-threshold-tokens`（默认 100000）
   - 最少消息数 `min-messages-to-compact`（默认 6）
   - 保留最近 `messages.size() / 4`
   - 其余由 LLM 总结成摘要事件
8. 在 `AgentResolveNode` 接恢复：
   - 实例缺失 → `listHeaders(200)` 找 `agentPreset == agentId`
   - `readAll` → `PersistingSessionLog.restore`
   - 恢复失败则走全新会话
9. 加 `HarnessConfigValidator`：检查数据源、端口、模型配置的基本合法性。

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `ISessionEventStore` | 事件存储端口 |
| `JdbcSessionEventStore` | JDBC 实现 |
| `PersistingSessionLog` | 内存 + 落库 |
| `SurfaceProjector` | 事件 → SurfaceOp |
| `BasicCompactionEngine` | 上下文压缩 |
| `ConversationQueryService` | 控制台查询 |

## 最小实现 vs 正式实现

- **最小实现**：H2 + 5 张表 + JSON 字符串存 payload。
- **正式实现**：19 张表、MySQL/H2 双兼容、`JsonlSessionEventStore` 备选、事件表归档、token 计量。

## 验收

### 1. 落库

```bash
curl -N -X POST http://localhost:8090/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{"agentId":"learn-07","message":"记住我的名字叫小傅"}'
```

查询：

```bash
curl -s 'http://localhost:8090/api/harness/console/sessions'
curl -s 'http://localhost:8090/api/harness/console/sessions/{sessionId}/messages'
```

### 2. 重启恢复

```bash
# Ctrl-C 停掉应用，再启动
curl -N -X POST http://localhost:8090/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{"agentId":"learn-07","message":"我刚才说我叫什么？"}'
```

预期：模型能回答“小傅”，说明事件日志恢复成功。

### 3. 压缩

把 `pressure-threshold-tokens` 调到很小（如 2000），连续发 10 条长消息；日志应出现压缩记录，会话仍可继续。

## 对照源码

- `deepseek-harness-java-app/src/main/resources/schema.sql`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/dao/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/eventstore/JdbcSessionEventStore.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/service/run/PersistingSessionLog.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/service/compaction/BasicCompactionEngine.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/session/conversation/`

## 常见坑

- **事件表不要只存最终消息**：要存 `AssistantChunk`，否则无法回放流式过程。
- **`seq` 不能由客户端生成**：存储层要保证同一会话内连续。
- **恢复要找最近会话**：`listHeaders` 按 `created_at` 倒序，取第一个匹配。
- **压缩不是删消息**：保留最近 1/4，其余变成摘要事件，原文仍可审计。
- **H2 与 MySQL 方言差异**：尽量用标准 SQL；`schema.sql` 用 `CREATE TABLE IF NOT EXISTS`。

## 完成标志

- [ ] 事件落库，重启后能恢复
- [ ] 控制台能查会话和消息
- [ ] 压缩阈值调小后能触发
- [ ] 有 Repository / 恢复流程测试
- [ ] 打 tag：`stage-07`
