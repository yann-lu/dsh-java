# 第 09 站 · 模型设置与渠道路由

> 阶段：P2 平台能力 · 体量：M（1–2 天） · 前置：02、07

## 这一站完成什么

把“模型配置”从 yml 提升为数据库里的渠道：

- 可保存多个渠道
- 可切换 active 渠道
- 新增 provider 不需要改 Bean
- 支持从上游同步模型列表
- 失败时回退到文件配置

## 前置检查

- 02 站 `ILlmRuntimePort` / `LlmAdapter` 可用
- 07 站 MyBatis 持久化可用
- 有一个可用的 OpenAI 兼容网关

## 任务清单

1. 实现模型设置持久化：
   - `ModelSettingEntity`
   - `IModelSettingRepository` + `ModelSettingRepository`
   - `IHarnessModelSettingDao` + `HarnessModelSettingPO`
2. 实现 `ModelSettingService`：
   - `save` / `deleteByChannelCode` / `activate` / `findActive` / `listAll`
   - 同一时间只有一个 active 渠道
3. 实现 `AgentModelSettingResolver`：
   - 优先级：请求显式 `channelCode` → 数据库 active 渠道 → `harness.llm.deepseek.*` 文件兜底
   - 返回 `AgentOptions`（channelCode、model、maxTokens、reasoningEffort）
4. 实现渠道模板：
   - `ChannelPreset`、`ChannelProtocol`、`AuthScheme`
   - `ChannelPresets` 内置 OpenAI / DeepSeek / 通义 / GLM / 豆包 / Kimi / Anthropic / Ollama
   - `ChannelPresetQueryService`
5. 实现协议路由：
   - `LlmAdapterFactory.create(provider)`
   - `ProtocolRoutingAdapter`：openai/ollama → OpenAiCompatibleAdapter；anthropic → AnthropicAdapter
   - 新增 provider 只需写模型设置，不必注册新 Bean
6. 实现模型发现：
   - `UpstreamModelSyncService`
   - OpenAI 兼容走 `/models`，Ollama 走 `/api/tags`
   - 失败不清空已保存配置
7. 实现重试：
   - `ResolvedRetryPolicy`（Normal / Always）
   - `InMemoryLlmRuntimePort` 在流未转发任何 chunk 时重试
   - 发出 `StreamChunk.Retry` 事件
8. 暴露 API：
   - `GET/POST /api/harness/settings/models`
   - `POST /api/harness/settings/models/active`
   - `DELETE /api/harness/settings/models/{channelCode}`
   - `POST /api/harness/settings/models/discover`
   - `GET /api/harness/channels/presets`

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `ModelSettingService` | 渠道 CRUD / 激活 |
| `AgentModelSettingResolver` | 请求 → 渠道 → 模型 |
| `ChannelPresets` | 内置渠道模板 |
| `LlmAdapterFactory` | 协议 → 适配器 |
| `ProtocolRoutingAdapter` | 运行时协议路由 |
| `UpstreamModelSyncService` | 模型发现 |
| `InMemoryLlmRuntimePort` | 路由 + 重试 |

## 最小实现 vs 正式实现

- **最小实现**：只支持 OpenAI 协议；手工填 baseUrl/apiKey/model；不做发现。
- **正式实现**：多渠道、协议路由、模型发现、重试、`apiKeyRef` 脱敏、active 唯一性、文件兜底。

## 验收

### 1. 保存并激活

```bash
curl -X POST http://localhost:8090/api/harness/settings/models \
  -H 'Content-Type: application/json' \
  -d '{
    "channelCode": "default",
    "providerCode": "deepseek",
    "modelCode": "deepseek-chat",
    "baseUrl": "https://api.deepseek.com/v1",
    "apiKeyRef": "your-key",
    "protocol": "openai",
    "enabled": true
  }'

curl -X POST http://localhost:8090/api/harness/settings/models/active \
  -H 'Content-Type: application/json' \
  -d '{"channelCode":"default"}'
```

然后发一条 `/api/agent/message`，确认使用的是该渠道。

### 2. 切换渠道

保存第二个渠道并激活，再发消息；日志里的 `channelCode` / `model` 应变化。

### 3. 发现模型

```bash
curl -X POST http://localhost:8090/api/harness/settings/models/discover \
  -H 'Content-Type: application/json' \
  -d '{"baseUrl":"https://api.deepseek.com/v1","apiKeyRef":"your-key","protocol":"openai"}'
```

### 4. 回退

删除 active 渠道后重启，确认回退到 `harness.llm.deepseek.*` 文件配置。

## 对照源码

- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/runtime/setting/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/channel/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/llm/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/repository/ModelSettingRepository.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/command/HarnessModelSettingCommandController.java`

## 常见坑

- **active 渠道要唯一**：激活一个时把其他置为 false，最好在事务里做。
- **协议与 provider 解耦**：provider 可以是任意编码，协议决定用哪个适配器。
- **API Key 不要回显**：查询接口只返回 `apiKeyRef` 的脱敏值。
- **模型发现失败不要清空配置**：上游抖动时保留旧值。
- **Ollama 允许空 Key**：鉴权方案为 NONE 时不要强行填 Key。

## 完成标志

- [ ] 能保存/激活/删除渠道
- [ ] 切换渠道后对话使用新模型
- [ ] 能发现上游模型
- [ ] 删除 active 后能回退文件配置
- [ ] 有 resolver / 路由单元测试
- [ ] 打 tag：`stage-09`
