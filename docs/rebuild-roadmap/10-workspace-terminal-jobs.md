# 第 10 站 · 工作区、终端与后台任务

> 阶段：P2 平台能力 · 体量：M（1–2 天） · 前置：04、05

## 这一站完成什么

让 Agent 不只会“单次执行命令”，而是拥有：

- 多工作区（每个工作区一个 cwd）
- 可交互的终端会话（open / send / read / close）
- 后台 Job（run / list / output / kill）

## 前置检查

- 04 站工具执行链可用
- 05 站工作目录与 Agent 生命周期可用
- 06 站沙箱边界可用

## 任务清单

1. 工作区：
   - `WorkspaceRegistryService`
   - 工作区列表、创建、重命名、删除、排序
   - `cwd` 解析、存在性校验、路径穿越防护
   - API：`GET/POST /api/agent/workspaces`、`DELETE /api/agent/workspaces/{name}`
2. 终端：
   - `ITerminalPort` + `LocalTerminalPort`
   - 打开终端会话、发送输入、读取输出、关闭
   - `TerminalTools.Open/Send/Read/Close/ListSessions`
   - API：`POST /api/harness/terminal/sessions`、`/send`、`/close`，`GET /sessions`、`/read`
3. 后台 Job：
   - `IJobRegistryService` + `InMemoryJobRegistryRepository`
   - 任务启动、状态、输出缓冲、终止
   - `JobRunTool` / `JobListTool` / `JobOutputTool` / `JobKillTool`
4. 把新工具注册进 `AgentToolCatalog`：
   - 终端工具组
   - Job 工具组
5. 资源清理：
   - Agent 取消/结束时关闭终端与 Job
   - 进程超时 kill
6. 安全：
   - 终端 cwd 必须在工作区内
   - Job 输出限制长度，避免撑爆上下文

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `WorkspaceRegistryService` | 工作区与 cwd |
| `ITerminalPort` / `LocalTerminalPort` | 终端会话 |
| `TerminalTools` | Agent 终端工具 |
| `IJobRegistryService` | 后台 Job 注册表 |
| `JobRunTool` / `JobOutputTool` | Job 工具 |

## 最小实现 vs 正式实现

- **最小实现**：单工作区 + `pwd` 类终端命令 + 同步 Job。
- **正式实现**：多工作区 CRUD/排序、PTY 终端、异步 Job、输出截断、进程组清理。

## 验收

### 1. 工作区

```bash
curl -X POST http://localhost:8090/api/agent/workspaces \
  -H 'Content-Type: application/json' \
  -d '{"name":"learn-10","path":"/tmp/learn-10"}'

curl -s http://localhost:8090/api/agent/workspaces
```

让 Agent 在 `cwd=/tmp/learn-10` 下写文件，确认落到该目录。

### 2. 终端

```bash
curl -X POST http://localhost:8090/api/harness/terminal/sessions \
  -H 'Content-Type: application/json' \
  -d '{"cwd":"/tmp/learn-10"}'

curl -X POST http://localhost:8090/api/harness/terminal/sessions/{id}/send \
  -H 'Content-Type: application/json' \
  -d '{"input":"pwd\n"}'

curl -s 'http://localhost:8090/api/harness/terminal/sessions/{id}/read'
```

### 3. 后台 Job

让 Agent 执行一个 5 秒的 sleep，再查询 Job 输出；或直接调用 Job 工具。

## 对照源码

- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/service/workspace/WorkspaceRegistryService.java`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/terminal/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/jobs/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/terminal/LocalTerminalPort.java`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/jobs/InMemoryJobRegistryRepository.java`

## 常见坑

- **路径穿越**：`../`、绝对路径、符号链接都要校验，不能只看字符串前缀。
- **终端不是一次性命令**：它是有状态的会话，读输出要支持增量游标。
- **Job 输出会无限增长**：必须截断/环形缓冲。
- **Agent 结束要回收资源**：否则终端和后台进程会泄漏。
- **Job 仓储是 InMemory**：重启即丢，生产要换持久化实现。

## 完成标志

- [ ] 能创建/切换工作区
- [ ] Agent 写文件落到正确 cwd
- [ ] 终端能 open/send/read/close
- [ ] 后台 Job 能 run/list/output/kill
- [ ] 越界 cwd 被拒绝
- [ ] 打 tag：`stage-10`
