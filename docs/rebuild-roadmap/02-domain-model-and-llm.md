# 第 02 站 · 领域模型与 LLM 一次性对话

> 阶段：P0 可运行内核 · 体量：M（1–2 天） · 前置：01

## 这一站完成什么

让 `POST /api/agent/message` 真正调用一次大模型并返回文本。  
这一站只做“一次性对话”，不接 SSE、不接工具、不接会话持久化。

## 前置检查

- 01 站能启动
- 有一个 OpenAI 兼容的模型服务（DeepSeek、通义、GLM、本地网关均可）
- 能通过环境变量注入 `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_DEFAULT_MODEL`

## 任务清单

1. 在 `types` 模块建立最小领域消息模型：
   - `Message`、`MessageSource`（User / Assistant / Tool）
   - `ContentBlock` 及 `TextBlock`、`ReasoningBlock`、`ToolCallBlock`、`ToolResultBlock`
   - `ToolDefinition`、`ToolExecutionResult`、`ToolRunContext`
   > 这些类放在 `types` 的 `domain.model` 包下，保持零 Spring。
2. 在 `domain.llm` 定义端口与值对象：
   - `ILlmRuntimePort`：`registerAdapter`、`listModels`、`resolveModel`、`stream(GenerateOptions)`
   - `LlmAdapter` SPI：`stream`、`listModels`、`resolveModel`、`retryPolicy`
   - `GenerateOptions`、`StreamChunk`、`FinishReason`、`TokenUsage`、`LlmFailure`
   - `BlockAssembler`：把 `StreamChunk` 拼成 `Message`
   - `LlmGenerationService`：把流式 `Publisher` 转成阻塞结果（先用于一次性对话）
3. 在 `infrastructure.adapter.llm` 实现：
   - `InMemoryLlmRuntimePort`：provider → adapter 路由
   - `OpenAiCompatibleAdapter`：请求 `/chat/completions`，解析 SSE `data:` 行
   - `LlmAdapterFactory` + `ProtocolRoutingAdapter`：按 `openai` / `anthropic` / `ollama` 选适配器
4. 在 `api` 定义：
   - `AgentMessageRequestDTO`（`agentId`、`message`、`channelCode`、`maxTokens`、`cwd`）
   - `AgentMessageResponseDTO`（`agentId`、`sessionId`、`status`、`messages`）
   - `IAgentApi`
5. 在 `case` 实现薄用例 `AgentUseCase`：
   - 校验 `agentId` / `message`
   - 组装一条 User `Message`
   - 调 `ILlmRuntimePort.stream`
   - 用 `BlockAssembler` 收成 Assistant `Message`
   - 返回 DTO
6. 在 `trigger` 实现：
   - `AgentApi implements IAgentApi`
   - `AgentController`：`POST /api/agent/message`
7. 在 `infrastructure.config` 装配 `ILlmRuntimePort`、`LlmAdapterFactory`、`LlmGenerationService`。

## 关键接口 / 类

| 类 | 职责 |
| --- | --- |
| `ILlmRuntimePort` | 领域唯一模型插座 |
| `LlmAdapter` | 厂商协议适配契约 |
| `OpenAiCompatibleAdapter` | OpenAI 兼容协议实现 |
| `BlockAssembler` | chunk → Message |
| `AgentUseCase` | 一次性对话用例 |
| `AgentController` | HTTP 入口 |

## 最小实现 vs 正式实现

- **最小实现**：只支持 OpenAI 兼容协议；只处理文本块；不做重试。
- **正式实现**：`InMemoryLlmRuntimePort` 带 `ResolvedRetryPolicy` 重试；`LlmAdapterFactory` 支持 Anthropic / Ollama；`LlmGenerationService` 还负责 token 统计与错误归一化。

## 验收

```bash
export LLM_API_KEY='your-key'
export LLM_BASE_URL='https://api.deepseek.com/v1'   # 按实际网关填
export LLM_DEFAULT_MODEL='deepseek-chat'

curl -s -X POST http://localhost:8090/api/agent/message \
  -H 'Content-Type: application/json' \
  -d '{
    "agentId": "learn-01",
    "message": "用一句话介绍你自己",
    "cwd": "."
  }'
```

预期：返回 JSON，`messages` 里有一条 `role=assistant` 的文本。

再验证失败路径：

```bash
unset LLM_API_KEY
# 重启后请求，应返回明确错误，而不是空回复或 500 堆栈
```

## 对照源码

- `deepseek-harness-java-types/src/main/java/cn/xiaofuge/deepseek/harness/domain/model/`
- `deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/llm/`
- `deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/llm/`
- `deepseek-harness-java-case/src/main/java/cn/xiaofuge/deepseek/harness/cases/agent/AgentUseCase.java`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/http/AgentController.java`

## 常见坑

- **不要把 `Message` 放在 `api.dto`**：DTO 是协议对象，`Message` 是领域对象；两者混用会让 case 和 trigger 互相污染。
- **`BlockAssembler` 要处理空流**：模型可能只返回 `[DONE]`，要给出明确错误。
- **domain 不要 import Spring**：端口和值对象保持纯 Java；装配放 `infrastructure.config`。
- **API Key 不要写进 yml**：用 `${LLM_API_KEY}` 环境变量。

## 完成标志

- [ ] `/api/agent/message` 返回助手文本
- [ ] 未配置 Key 时返回可读错误
- [ ] `BlockAssembler` 有单元测试
- [ ] `types` 仍然零 Spring
- [ ] 打 tag：`stage-02`
