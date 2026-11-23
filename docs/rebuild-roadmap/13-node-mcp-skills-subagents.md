# 第 13 站 · Node Bridge、MCP、Skills 与 Subagent

> 阶段：P3 扩展与交付 · 体量：L（2–3 天） · 前置：12

## 这一站完成什么

把“扩展能力”从 Java 插件扩展到四种外部形态：

1. **Node Bridge**：独立 sidecar 进程 + JSON-RPC
2. **MCP**：stdio / sse / streamable-http 三种传输
3. **Skills**：文件系统技能包
4. **Subagent**：Claude Code / Codex / ACP / 进程内 Fork/Spawn

## 前置检查

- 12 站 Java 插件与 `ToolRegistry` 可用
- 本机有 Node.js 18+（Node Bridge）
- 有一个可运行的 MCP Server（或先用示例）
- Claude Code / Codex 可选，没有就先用进程内 Subagent

## 任务清单

### 13.1 Node Bridge

1. `IPluginProcessPort` + `PluginProcessPort`
2. `JsonRpcPluginToolBridge`：stdin/stdout 换行分隔 JSON-RPC 2.0
3. 识别入口：`package.json`、`.codex-plugin/plugin.json`、`cordis.yml`
4. 限制入口在插件安装目录内的 `.js/.mjs/.cjs`，不拼 Shell 命令
5. 方法：`initialize`、`notifications/initialized`、`tools/list`、`tools/call`
6. 工具名：`plugin__<pluginId>__<toolName>`

### 13.2 MCP

1. `IMcpClient`、`StdioMcpClient`、`HttpMcpClient`
2. `McpServerConfig`、`McpBootstrap`、`McpRuntime`
3. `McpToolAdapter`：远端工具 → `ToolRegistry`
4. 配置：
   ```yaml
   harness:
     extensions:
       mcp:
         servers:
           - name: filesystem
             transport: stdio
             command: npx
             args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
   ```
5. 工具名：`mcp__<serverName>__<toolName>`

### 13.3 Skills

1. `ISkillService` + `FilesystemSkillProviderPort`
2. `SkillTool`：让 Agent 读取/使用技能
3. 配置 `harness.extensions.skills.roots`，支持 `<name>/SKILL.md` 和 `<name>.md`
4. 示例：`skills/baidu-ai-search/SKILL.md`

### 13.4 Subagent

1. `SubagentRegistry`
2. `SubagentProvider` 接口
3. `SpawnInProcessProvider` / `ForkInProcessProvider`
4. `ClaudeCodeSubagentProvider` / `CodexSubagentProvider` / `AcpSubagentProvider`
5. `SubagentTool`：把子 Agent 当成一个工具调用
6. 注意进程清理、超时和输出截断

### 13.5 扩展管理

1. `ExtensionManagementService`
2. `ExtensionAgentToolRegistrar`：`extension_skill_install`、`extension_skill_list`、`extension_skill_manage`、`extension_mcp_list`、`extension_mcp_configure`、`extension_mcp_remove`
3. 配置存储：`~/.dsh/extensions.json`

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `JsonRpcPluginToolBridge` | Node sidecar 工具桥 |
| `StdioMcpClient` / `HttpMcpClient` | MCP 传输 |
| `McpToolAdapter` | MCP 工具适配 |
| `FilesystemSkillProviderPort` | 技能发现 |
| `SubagentRegistry` | 子 Agent 注册 |
| `SubagentTool` | 子 Agent 工具化 |

## 最小实现 vs 正式实现

- **最小实现**：Node 插件只支持 tools/list + tools/call；MCP 只支持 stdio；Subagent 只做进程内 Spawn。
- **正式实现**：三种 MCP 传输、Claude Code/Codex/ACP 外部子 Agent、技能安装/管理工具、扩展配置持久化。

## 验收

### 1. Node 插件

```bash
# 安装 plugins/dsh-demo-plugin 目录
curl -X POST http://localhost:8090/api/harness/plugins/install \
  -H 'Content-Type: application/json' \
  -d '{"pluginId":"dsh-demo","runtimeType":"DSH_NODE_BRIDGE","sourcePath":"/abs/path/dsh-demo-plugin","entrypoint":"index.js"}'

curl -X POST http://localhost:8090/api/harness/plugins/activate \
  -H 'Content-Type: application/json' \
  -d '{"pluginId":"dsh-demo"}'

curl -X POST http://localhost:8090/api/harness/plugins/run \
  -H 'Content-Type: application/json' \
  -d '{"pluginId":"dsh-demo"}'
```

让 Agent 调用 `plugin__dsh-demo__<toolName>`。

### 2. MCP

配置一个 stdio MCP Server，启动后：

```bash
curl -s http://localhost:8090/api/harness/plugins/inventory
```

预期能看到 `mcp__...` 工具；让 Agent 调用一次。

### 3. Skill

确认 `skills/baidu-ai-search/SKILL.md` 被发现；让 Agent 使用该技能。

### 4. Subagent

让主 Agent 调 `subagent` 工具，返回子 Agent 结果；确认没有残留进程。

## 对照源码

- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/plugin/JsonRpcPluginToolBridge.java`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/mcp/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/tool/mcp/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/skill/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/agent/service/subagent/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/extension/`

## 常见坑

- **Node 入口不能越界**：只能执行插件目录内的 `.js/.mjs/.cjs`，不要用 `node -e` 拼接。
- **JSON-RPC 要按行分帧**：stdout 里可能有日志，必须只解析合法 JSON 行。
- **MCP 工具名冲突**：统一 `mcp__<server>__<tool>`，server 名要稳定。
- **Subagent 输出可能很长**：要截断并落事件，不能直接塞回主上下文。
- **进程泄漏**：插件停止/应用关闭时一定要 kill sidecar 和子 Agent。

## 完成标志

- [ ] Node 插件能安装并调用工具
- [ ] stdio MCP Server 工具可用
- [ ] Skill 能被发现并使用
- [ ] Subagent 能返回结果且进程被清理
- [ ] 有至少一个扩展链路的集成测试
- [ ] 打 tag：`stage-13`
