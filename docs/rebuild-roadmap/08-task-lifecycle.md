# 第 08 站 · 任务生命周期

> 阶段：P1 平台化 · 体量：M（1–2 天） · 前置：07

## 这一站完成什么

把“对话”扩展成“可提交、可审批、可排队、可执行”的任务：

```text
CREATED → PENDING_APPROVAL → QUEUED → RUNNING → COMPLETED / FAILED
```

这一站的重点不是新增模型能力，而是**状态机与事务边界**。

## 前置检查

- 06 站审批能力可用
- 07 站会话与任务表可持久化
- 理解“提交期审批”与“运行期审批”是两道不同的门

## 任务清单

1. 实现提交策略树：
   - `SubmissionRootNode`：校验请求、生成 submission
   - `ProfileResolutionNode`：解析 execution profile
   - `ToolResolutionNode`：解析可用工具
   - `PermissionCheckNode`：`PermissionPolicyService.assess` + `ApprovalPolicyService.decide`
   - `EnqueueNode`：事务内创建会话 + 入队 + 记录日志；事务外执行
2. 实现 `TaskSubmissionPolicyService` + 过滤器：
   - `PromptNotBlankFilter`
   - `ProfileCodeFilter`
   - `ToolSelectionFilter`
3. 实现任务队列：
   - `HarnessTaskEntity`
   - `IHarnessTaskQueueRepository`
   - `HarnessTaskQueueService.createQueuedTask`
4. 实现执行：
   - `HarnessExecutionService.executeSession(sessionId)`
   - `PENDING_APPROVAL` 直接抛异常
   - RUNNING → COMPLETED / FAILED
   - 执行阶段复用 Agent 链路
5. 实现审批恢复：
   - `POST /api/harness/approvals/{sessionId}/approve`
   - 状态改为 QUEUED → 调 `executeSession`
6. 暴露 API：
   - `POST /api/harness/tasks/submit`
   - `GET /api/harness/approvals/pending`
   - `POST /api/harness/approvals/{sessionId}/approve`
7. 加状态机测试：
   - 免审批任务直接 RUNNING → COMPLETED
   - 需审批任务停在 PENDING_APPROVAL
   - 对 PENDING_APPROVAL 调 executeSession 抛异常
   - 审批后恢复执行

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `SubmitHarnessTaskFactory` | 提交策略树 |
| `PermissionCheckNode` | 权限/审批决策点 |
| `EnqueueNode` | 事务边界与执行触发 |
| `HarnessTaskQueueService` | 入队 |
| `HarnessExecutionService` | 执行与状态流转 |
| `HarnessTaskEntity` | 任务状态 |

## 最小实现 vs 正式实现

- **最小实现**：单队列、内存执行、只有 submit/approve 两个接口。
- **正式实现**：任务表、执行画像、工具目录、审批原因、失败原因、任务列表/详情查询、与 AgentRun 共用执行链。

## 验收

### 1. 免审批任务

```bash
curl -X POST http://localhost:8090/api/harness/tasks/submit \
  -H 'Content-Type: application/json' \
  -d '{
    "profileCode": "web",
    "prompt": "用一句话说明什么是 ReAct",
    "workspaceId": "default"
  }'
```

预期：返回 `status=COMPLETED` 或先 `QUEUED` 后 `COMPLETED`。

### 2. 需审批任务

提交一个包含 `shell_execute` 的任务；预期返回 `PENDING_APPROVAL`。

```bash
curl -s http://localhost:8090/api/harness/approvals/pending
curl -X POST http://localhost:8090/api/harness/approvals/{sessionId}/approve
```

预期：审批后状态进入 `QUEUED/RUNNING`，最终 `COMPLETED`。

### 3. 防绕过

直接对 `PENDING_APPROVAL` 的 session 调 `executeSession`，预期抛 `IllegalStateException`。

## 对照源码

- `deepseek-harness-java-case/src/main/java/cn/xiaofuge/deepseek/harness/cases/task/submit/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/task/`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/HarnessTaskController.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/command/HarnessApprovalCommandController.java`

## 常见坑

- **`executeSession` 不要在事务里**：模型调用可能几分钟，不能占着数据库连接。
- **状态流转要幂等**：重复审批、重复执行要有明确结果。
- **审批通过不等于执行成功**：执行失败要落 FAILED 和失败原因。
- **提交期审批与运行期审批都要保留**：前者决定任务是否入队，后者决定某个工具是否放行。

## 完成标志

- [ ] 免审批任务能执行完成
- [ ] 需审批任务停在 PENDING_APPROVAL
- [ ] 审批后能恢复执行
- [ ] 直接执行 PENDING_APPROVAL 被拒绝
- [ ] 有状态机单元测试
- [ ] 打 tag：`stage-08`
