# 第 15 站 · 数字人、协作与 A2A（可选）

> 阶段：P3 扩展与交付 · 体量：M（1–2 天） · 前置：08、12  
> 这一站不是核心 MVP 的必经路径；想完整覆盖项目能力时再做。

## 这一站完成什么

把单机 Agent 扩展为“多房间、多参与者、可被外部 Agent 调用”的协作形态：

- 数字人（Digital Human）与远端端点
- 协作房间（Room）、参与者、房间任务、产物
- A2A / AgentCard：让外部 Agent 发现并调用本 Harness
- 跨实例审批聚合

## 前置检查

- 08 站任务与审批可用
- 12 站插件/扩展可用
- 理解当前 `infrastructure/digitalhuman` 是近版本的高频改动区

## 任务清单

1. 数字人：
   - `DigitalHumanEntity`、`DigitalHumanService`
   - `DigitalHumanEndpointPO` / `IDigitalHumanEndpointDao`
   - API：`/api/harness/digital-humans`（以实际 Controller 为准）
2. 协作房间：
   - `CollaborationService`
   - 房间、参与者、房间事件、房间任务、产物
   - 表：`collaboration_room`、`collaboration_task`、`collaboration_artifact`、`room_event`、`room_participant`
3. A2A / AgentCard：
   - `A2AController`
   - `AgentCardController`
   - 返回 Agent 能力描述、端点、鉴权方式
4. 跨实例审批：
   - `AggregatingRuntimeApprovalGateway`
   - `RemoteEndpointCredentialRegistry`
   - 本地 Broker + 远端数字人端点待决审批聚合
5. 安全：
   - 远端端点凭据管理
   - 房间成员鉴权
   - 审批归属与超时
6. 观测：
   - 房间事件日志
   - 数字人运行状态

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `DigitalHumanService` | 数字人管理 |
| `CollaborationService` | 协作房间 |
| `A2AController` | A2A 协议入口 |
| `AgentCardController` | Agent 能力卡 |
| `AggregatingRuntimeApprovalGateway` | 跨实例审批 |

## 最小实现 vs 正式实现

- **最小实现**：单房间 + 固定数字人 + AgentCard 静态返回。
- **正式实现**：多房间、参与者权限、房间任务、产物、远端审批、凭据管理。

## 验收

```bash
# AgentCard
curl -s http://localhost:8090/.well-known/agent-card.json
# 或以实际路径为准

# 数字人列表
curl -s http://localhost:8090/api/harness/digital-humans

# 房间消息（以实际接口为准）
curl -X POST http://localhost:8090/api/harness/collaboration/rooms/{roomId}/messages \
  -H 'Content-Type: application/json' \
  -d '{"content":"你好"}'
```

预期：

- AgentCard 返回能力描述
- 数字人/房间能创建
- 房间消息能触发 Agent 并落事件
- 远端审批能在本地待审批列表看到

## 对照源码

- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/A2AController.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/AgentCardController.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/CollaborationController.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/DigitalHumanController.java`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/digitalhuman/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/config/AgentRunBeanConfig.java`

## 常见坑

- **不要把数字人当成核心 Agent**：它复用 Agent 链路，但有自己的端点、房间和鉴权。
- **跨实例审批要防重**：同一审批不能在两个实例同时被处理。
- **远端凭据不要落明文**：至少要做配置隔离和脱敏。
- **房间事件要有顺序**：并发消息需要序号/时间戳，否则回放会乱。
- **这一站可以跳过**：先把 01–14 跑通，再决定是否做协作形态。

## 完成标志

- [ ] AgentCard 可访问
- [ ] 数字人/房间能创建
- [ ] 房间消息能触发 Agent
- [ ] 远端审批能在本地看到
- [ ] 有至少一个协作链路集成测试
- [ ] 打 tag：`stage-15`
