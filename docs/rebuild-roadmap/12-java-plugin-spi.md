# 第 12 站 · Java 插件 SPI 与生命周期

> 阶段：P3 扩展与交付 · 体量：L（2–3 天） · 前置：06、07

## 这一站完成什么

让外部 JAR 在不改宿主代码的情况下，向 Agent 注册工具、提示词、Hook、事件：

```text
install → activate → run → disable → uninstall
```

插件工具最终以 `plugin__<pluginId>__<toolName>` 进入 `ToolRegistry`，和内置工具走同一条执行链。

## 前置检查

- 04 站工具系统可用
- 06 站 Hook/审批可用
- 07 站持久化可用
- 理解 Java SPI、URLClassLoader、ServiceLoader

## 任务清单

1. 在 `types` 定义插件 SPI：
   - `JavaHarnessPlugin`
   - `AbstractHarnessPlugin`
   - `PluginContext`：`registerTool`、`registerSystemPrompt`、`registerHook`、`subscribe`、`emit`、`registerDisposer`、`getConfig`
   - `PluginManifest`、`PluginHook`、`PluginHookResult`、`PluginEvent`、`PluginEventBus`、`PluginConfigStore`
2. 在 `domain.plugin` 实现三段：
   - Registry：`PluginRegistryService`（安装、加载、状态刷新、卸载）
   - Bridge：`PluginBridgeService`（识别 `JAVA_NATIVE` / `DSH_NODE_BRIDGE` / `CODEX` / `CORDIS`，生成 plan）
   - Runtime：`PluginRuntimeService`（start/stop）
3. 在 `case.plugin` 实现：
   - 安装 Pipeline：`InstallPluginNode`
   - 生命周期 Pipeline：`ResolvePluginNode → InspectBridgeNode → BindBridgeNode → ActivatePluginNode / RunPluginNode`
   - 管理/查询：disable/enable/uninstall/list/status
4. 在 `infrastructure.adapter.plugin.java` 实现：
   - `JavaPluginLoader`：`URLClassLoader` 隔离，读 `META-INF/plugin.yaml`，按 entrypoint 实例化，回退 ServiceLoader
   - `JavaPluginRuntimeManager`：创建 `SpringPluginContext`，注册工具/提示词/Hook/事件，失败回滚
   - `InProcessToolBridge` / `InProcessPluginHookRegistry` / `InProcessPluginEventBus`
   - `DatabasePluginConfigStore` / `InMemoryPluginConfigStore`
   - `PluginHotReloader`
5. 暴露 API：
   - `POST /api/harness/plugins/install`
   - `POST /api/harness/plugins/activate`
   - `POST /api/harness/plugins/run`
   - `POST /api/harness/plugins/{id}/disable|enable|uninstall`
   - `GET /api/harness/plugins`、`/inventory`、`/{id}/status`
6. 写一个最小样例插件：
   - `META-INF/plugin.yaml`：`id` / `name` / `version` / `entrypoint`
   - `META-INF/services/cn.xiaofuge.deepseek.harness.domain.spi.JavaHarnessPlugin`
   - 一个 `AbstractTool` 子类，返回固定 JSON
   - 可选：用 `plugins/deepseek-harness-plugin-archetype` 生成插件骨架，再替换工具实现
7. 验证插件工具确实经过 `ToolCallExecutor`：
   - Hook 能拦截
   - 审批矩阵能命中
   - 事件日志有 ToolCall/ToolResult

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `JavaHarnessPlugin` | 插件入口 SPI |
| `PluginContext` | 注册边界 |
| `JavaPluginLoader` | 隔离加载 |
| `PluginRegistryService` | 安装/状态 |
| `PluginBridgeService` | 运行时识别 |
| `PluginRuntimeService` | 启停 |
| `SpringPluginContext` | 宿主上下文实现 |

## 最小实现 vs 正式实现

- **最小实现**：只支持 JAVA_NATIVE；只注册工具；无热重载。
- **正式实现**：三种运行类型、提示词/Hook/事件/配置、失败逆序回收、JAR 热重载、插件配置持久化。

## 验收

### 1. 构建样例插件

```bash
cd plugins/sample-tools-plugin
mvn -q -DskipTests package
```

### 2. 安装并激活

```bash
curl -X POST http://localhost:8090/api/harness/plugins/install \
  -H 'Content-Type: application/json' \
  -d '{
    "pluginId":"sample-tools",
    "displayName":"Sample Tools",
    "pluginVersion":"1.0.0",
    "runtimeType":"JAVA_NATIVE",
    "sourcePath":"/absolute/path/sample-tools-plugin-1.0.0.jar",
    "entrypoint":"sample-tools-plugin-1.0.0.jar"
  }'

curl -X POST http://localhost:8090/api/harness/plugins/activate \
  -H 'Content-Type: application/json' \
  -d '{"pluginId":"sample-tools"}'
```

### 3. 调用工具

让 Agent 使用 `plugin__sample-tools__<toolName>`；或在控制台插件卡片查看工具列表。

### 4. 停用与热重载

停用后工具消失；替换 JAR 后触发热重载，旧工具注销、新工具注册。

## 对照源码

- `deepseek-harness-java-types/src/main/java/cn/xiaofuge/deepseek/harness/domain/spi/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/plugin/`
- `deepseek-harness-java-case/src/main/java/cn/xiaofuge/deepseek/harness/cases/plugin/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/plugin/java/`
- `plugins/sample-tools-plugin/`

## 常见坑

- **pluginId 三处必须一致**：`plugin.yaml.id`、SPI 实现的 `pluginId()`、安装请求的 `pluginId`。
- **entrypoint 含义不同**：`plugin.yaml.entrypoint` 是类全限定名；HTTP 安装请求里的 `entrypoint` 是 JAR 文件名。
- **ClassLoader 要隔离**：插件依赖打进自己的 JAR，`types` 用 `provided`，不要重复打包宿主。
- **停止要逆序回收**：工具、提示词、Hook、事件订阅、disposer 都要注销，再关闭 ClassLoader。
- **`configure()` 不要做长阻塞**：只做注册和轻量初始化。

## 完成标志

- [ ] 样例插件能安装/激活/运行
- [ ] Agent 能看到并调用插件工具
- [ ] 停用后工具消失
- [ ] 替换 JAR 能热重载
- [ ] 插件工具经过 Hook/审批链
- [ ] 打 tag：`stage-12`
