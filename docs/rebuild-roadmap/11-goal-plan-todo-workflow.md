# 第 11 站 · 目标、计划、待办与工作流

> 阶段：P2 平台能力 · 体量：L（2–3 天） · 前置：05、07

## 这一站完成什么

让 Agent 从“单轮任务”升级为“有目标、有计划、有进度”的长期执行者：

- Goal：目标状态机
- Plan：计划模式
- Todo：待办清单
- Workflow：可启动/取消/流式输出的工作流
- Schedule：定时/周期任务（先讲清当前实现边界）

## 前置检查

- 05 站策略树与工具注册可用
- 07 站事件与持久化可用
- 11 站会大量用到 `SessionEvent` 和 `ToolCallExecutor`

## 任务清单

### 11.1 Goal

1. `GoalAggregate`、`GoalAction`、`GoalSnapshotVO`、`GoalStatus`
2. `IGoalRepository` + `GoalRepository`
3. `GoalTools.Get/Create/Update` 注册进 `AgentToolCatalog`
4. API：`GET/POST /api/harness/goals/{sessionId}`
5. 验收：Agent 通过工具创建目标，查询接口能读到

### 11.2 Plan

1. `PlanModeService` + `PlanModeChange` 事件
2. `ExitPlanModeTool`
3. Prompt 注入：计划模式下先出计划、再执行
4. 验收：进入计划模式后，Agent 先输出步骤而不是直接调工具

### 11.3 Todo

1. `TodoWriteService` + `TodoWriteTool`
2. `TodoWrite` / `TodoItemPayload` 事件
3. 前端/控制台能投影待办列表
4. 验收：Agent 写 3 条待办，事件日志出现 `TodoWrite`

### 11.4 Workflow

1. `IWorkflowService` + `WorkflowCaseImpl`
2. `IWorkflowEnginePort` + `LocalWorkflowEnginePort`
3. `WorkflowRun` 用 `CompletableFuture` 驱动
4. 复用 `ReactLoopAgent` 的 deltaSink / toolCallSink 推 SSE
5. API：`POST /api/workflow/start`、`/stream`、`/{runId}/cancel`
6. 验收：启动工作流，SSE 收到事件；取消后状态终止

### 11.5 Schedule

1. `IScheduleService` + `ScheduleService`
2. `InMemoryScheduleRepository`
3. `ScheduleTools.Create/ListAll/Delete`
4. **明确边界**：当前 `drive()` 没有 `@Scheduled`/Quartz 调用方，仓储是 InMemory，重启即丢
5. 验收：能创建/列出/删除 schedule；理解它不是生产级调度器

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `GoalAggregate` | 目标聚合 |
| `GoalTools` | Agent 目标工具 |
| `PlanModeService` | 计划模式 |
| `TodoWriteService` | 待办写入 |
| `WorkflowRun` | 工作流运行态 |
| `LocalWorkflowEnginePort` | 本地工作流引擎 |
| `ScheduleService` | 定时任务（内存） |

## 最小实现 vs 正式实现

- **最小实现**：Goal + Todo + 单步 Workflow；Schedule 只做 CRUD。
- **正式实现**：Goal 事件溯源、Plan 模式与提示词联动、Workflow 多步骤 DAG、Schedule 持久化与真实调度器。

## 验收

```bash
# 目标
curl -s http://localhost:8090/api/harness/goals/{sessionId}

# 工作流
curl -N -X POST http://localhost:8090/api/workflow/stream \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"分三步介绍这个项目"}'
```

预期：

- Goal 查询返回 `GoalSnapshotVO`
- 工作流 SSE 有开始/结束事件
- Todo 事件能在控制台/事件日志看到
- Schedule 能创建，但重启后消失（这是当前实现的已知边界）

## 对照源码

- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/goal/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/plan/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/todo/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/workflow/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/schedule/`
- `deepseek-harness-java-case/src/main/java/cn/xiaofuge/deepseek/harness/cases/goal/`
- `deepseek-harness-java-case/src/main/java/cn/xiaofuge/deepseek/harness/cases/workflow/`

## 常见坑

- **Goal 不是普通字段**：它有状态机，更新要走聚合方法，不要直接 set。
- **Plan 模式改变的是提示词与工具选择**：不是另开一条 Agent。
- **Todo 要发事件**：只存内存列表，刷新/回放就丢了。
- **Workflow 目前只推终态事件**：不要假设它和对话 SSE 一样细粒度。
- **Schedule 没有驱动方**：`drive()` 当前无人调用，别把它当生产定时器。

## 完成标志

- [ ] Goal 能创建/查询/更新
- [ ] Plan 模式能改变 Agent 行为
- [ ] Todo 写入事件可见
- [ ] Workflow 能启动、流式输出、取消
- [ ] Schedule CRUD 可用，并记录其内存边界
- [ ] 打 tag：`stage-11`
