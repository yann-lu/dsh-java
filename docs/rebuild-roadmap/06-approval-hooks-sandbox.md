# 第 06 站 · 审批、Hook 与沙箱

> 阶段：P1 平台化 · 体量：L（2–3 天） · 前置：05

## 这一站完成什么

让 Agent 的“危险动作”可控：

- 高风险工具执行前**挂起等待人工裁决**
- Hook 可以在工具调用前后做审计/拦截
- Shell 与文件写入被限制在**工作区边界**内

这一站是 Harness 从“能跑”走向“敢用”的分水岭。

## 前置检查

- 04 站工具执行链可用
- 05 站策略树与 Agent 生命周期可用
- 理解“提交期审批”和“运行期审批”的区别

## 任务清单

1. 实现权限矩阵：
   - `IPermissionMatrixPort` + `PermissionMatrixVO`
   - `PermissionMatrixPort` 读取 `harness.approval.required-tools`
   - 默认集合：`shell_execute`、`fs_write`、`plugin.run`、`subprocess.spawn`
2. 实现审批策略：
   - `IApprovalPolicyPort` + `ApprovalPolicyVO` / `ApprovalDecisionVO`
   - `ApprovalPolicyService.decide(profile, assessment)`
   - `PermissionPolicyService.assess(tools)`
3. 实现运行期审批：
   - `MatrixRuntimeApprovalGate`：会话放行集合 + 审批模式 + askHandler
   - `RuntimeApprovalBroker`：`requestApproval` 阻塞等待；`resolve` 提交裁决；`listPending` 查询
   - 裁决四分支：allow-once / allow-session / deny / cancel
   - 默认等待 10 分钟，超时 DENY
4. 把 Gate 接到 `ToolCallExecutor`：
   - `checkApproval` 先发 `requires_approval` 生命周期信号
   - DENY 时记录一条失败 ToolResult，不执行工具
   - `AgentRunFactory` 在权限矩阵和审批网关都存在时装配 Gate
5. 实现 Hook：
   - `IHookService`、`HookPoint`（PRE_TOOL_USE / POST_TOOL_USE / USER_PROMPT_SUBMIT / SESSION_START / STOP / NOTIFICATION）
   - `ToolCallExecutor.shouldBlock`：PRE Hook 返回 BLOCK/DENY 则跳过执行
   - POST Hook 只观察，不改变结果
6. 实现沙箱：
   - `SandboxedShellExecutor`：READ_ONLY 拒绝 shell；WORKSPACE_WRITE 校验 cwd + 越界前缀；DANGER_FULL_ACCESS 放行
   - `SandboxedFsService`：读写限制在 workspace roots
   - `DangerousCommandGuard`：文本级危险命令黑名单（与沙箱互补）
7. 暴露审批 API：
   - `GET /api/harness/approvals/pending`
   - `POST /api/harness/approvals/{sessionId}/approve`
   - `RuntimeApprovalController` 轮询/裁决
8. 配置：
   ```yaml
   harness:
     approval:
       required-tools: shell_execute,fs_write,plugin.run,subprocess.spawn
       runtime-timeout-ms: 600000
     sandbox:
       default-mode: WORKSPACE_WRITE
       shell-enabled: true
       fs-enabled: true
   ```

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `PermissionPolicyService` | 工具权限矩阵评估 |
| `ApprovalPolicyService` | 审批决策 |
| `MatrixRuntimeApprovalGate` | 运行期门禁 |
| `RuntimeApprovalBroker` | 挂起/唤醒审批 |
| `IHookService` | 生命周期 Hook |
| `SandboxedShellExecutor` | Shell 空间边界 |
| `SandboxedFsService` | 文件读写边界 |

## 最小实现 vs 正式实现

- **最小实现**：只拦 `shell_execute`；只做 WORKSPACE_WRITE；审批用内存 Broker。
- **正式实现**：权限矩阵 + 提交期审批 + 运行期审批 + Hook + 沙箱 + 数字人远程审批聚合（最后一项留到第 15 站）。

## 验收

### 1. 运行期审批

```bash
curl -N -X POST http://localhost:8090/api/agent/stream \
  -H 'Content-Type: application/json' \
  -d '{"agentId":"learn-06","message":"执行 ls -la","cwd":"/tmp"}'
```

预期：SSE 出现 `step_break`，`status=requires_approval`；查询待审批：

```bash
curl -s http://localhost:8090/api/harness/approvals/pending
```

审批：

```bash
curl -X POST http://localhost:8090/api/harness/approvals/{sessionId}/approve \
  -H 'Content-Type: application/json' \
  -d '{"decision":"allow-once"}'
```

然后工具继续执行，出现 `tool_result`。

### 2. 沙箱边界

让 Agent 执行 `cd / && ls`，预期被拒绝并返回沙箱错误，而不是真的切到根目录。

### 3. Hook

注册一个 PRE_TOOL_USE Hook，对 `shell_execute` 返回 BLOCK；预期工具不执行，事件里记录 block 结果。

## 对照源码

- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/task/permission/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/task/approval/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/tool/service/MatrixRuntimeApprovalGate.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/service/run/RuntimeApprovalBroker.java`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/sandbox/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/port/PermissionMatrixPort.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/command/RuntimeApprovalController.java`

## 常见坑

- **审批等待不要占用数据库事务**：Broker 是阻塞等待，事务应在此之前结束。
- **DENY 也要写 ToolResult**：否则会话回放里会缺一条工具结果，前端卡片状态不完整。
- **FULL_OPEN 是显式绕过**：请求里的 `approvalMode=FULL_OPEN` 会跳过审批；生产环境要限制。
- **沙箱不是命令白名单**：`SandboxedShellExecutor` 主要管 cwd 与越界前缀，危险命令还要靠 `DangerousCommandGuard` 和容器隔离。
- **Hook 返回 BLOCK 与 DENY 都阻止执行**：不要只处理其中一种。

## 完成标志

- [ ] `shell_execute` 会挂起审批
- [ ] 审批通过后工具继续执行
- [ ] `cd /` 被沙箱拒绝
- [ ] PRE Hook 能阻止工具
- [ ] 有审批 Gate / 沙箱边界单元测试
- [ ] 打 tag：`stage-06`
