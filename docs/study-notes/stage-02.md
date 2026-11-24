# 第 02 站学习笔记 — 领域模型与 LLM

> 按 G2.x 小目标累积，关站时定稿。上一站：[stage-01](stage-01.md)（待补）

---

## G2.1 · 领域消息模型（types）✅ 2026-11-24

### 一句话总纲

**Message 是信封，ContentBlock 家族是信纸。** 信封管"谁在什么时候说的一句话"，信纸管"说的到底是什么"。

### 一条消息的一生（ReAct 视角）

用户问"查下今天天气"，系统里依次出现四条消息——这就是未来 ReactLoopAgent 的全部燃料：

```text
① Message(role=user,      content=[TextBlock("查下天气")],          source=UserSource())
② Message(role=assistant, content=[TextBlock("我查一下"),
                                        ToolCallBlock(callId=c1, name=web_search, args=…)],
                            source=ModelMessageSource("deepseek","deepseek-chat"))
③ Message(role=tool,      content=[ToolResultBlock(callId=c1, …)],  source=ToolMessageSource("c1"))
④ Message(role=assistant, content=[TextBlock("今天晴天，25 度")],    source=ModelMessageSource(…))
```

①喂给模型 → 产出② → 工具执行产生③ → 回填再喂 → 产出④。
`ToolCallBlock` ↔ `ToolResultBlock` 靠 **callId 配对**——这就是 ReAct 的 Act / Observe。

### 类职责速查

| 类 | 职责 | 关键点 |
| --- | --- | --- |
| `Message` | 一次发言的不可变快照 | 投递、模型请求、事件落库、回放全程唯一载体 |
| `MessageSource` | 谁产生的（领域视角） | **sealed 接口** + 4 个嵌套 record：User / Plugin(plugin) / Model(provider,model) / Tool(callId) |
| `ContentBlock` | 内容抽象（sealed 接口） | 一条消息 = 异构块的**有序列表**，不是字符串 |
| `TextBlock` | 给用户看的正文 | |
| `ReasoningBlock` | 模型思考过程 | 展示用，一般不回传下次请求 |
| `ToolCallBlock` | 模型发起的工具调用请求 | Act |
| `ToolResultBlock` | 工具执行结果 | Observe；`isError` 让失败也是合法内容 |

### 三个设计点（为什么这么设计）

1. **content 为什么是 `List<ContentBlock>` 而不是 `String`？**
   现代 LLM 一条消息是混合物：可以一边说"我查一下"一边发起工具调用。拍平成 String 丢结构，后面 SSE 分段渲染、工具执行、结果回填全得重新解析——**结构化保存在源头**。
2. **`role` 和 `source` 是同一句话的两个视角**
   `role`（system/user/assistant/tool）给**模型协议**看——LLM API 只认它；`source` 给**领域逻辑**看——渲染、审计、事件分类靠它。一个对外，一个对内。
3. **`sealed` 的穷尽性**
   将来加 `ImageBlock`，所有 `switch (block)` 编译报错，逼你逐个检查——用类型系统替你记住业务约定。

### 踩坑实录（比答对更值钱）

| 坑 | 教训 |
| --- | --- |
| Message 少了 `source` 字段——javadoc 里写了 `@param source`，record 里却没有 | **javadoc 会撒谎（或过时），签名不会。** 侦察用 `rg "..." -A 6` 看 record 头，别看注释 |
| 字段命名 `contents` vs 原版 `content` | 能同名就同名——命名偏差会让 diff 对照永远飘红 |
| 想当然把 MessageSource 写成 enum | **别猜形状，看原版。** 它是 sealed interface + 嵌套 records（允许插件扩展） |

### 与架构的连接

这 7 个类放 **types** 而非 domain：types 是"边界上的通用语"——插件只被允许依赖它；稳定依赖原则（最稳定的契约放最里层）；零 Spring，插件工程不用拉 Spring 就能编译。domain 依赖 types，永不反向。

### 自测（合上笔记能答出才算过）

1. role 和 source 各给谁看？去掉 source 会丢什么能力？
2. ToolCallBlock 和 ToolResultBlock 怎么关联？对应 ReAct 的哪两步？
3. sealed 接口比普通接口多了什么编译期能力？
4. 为什么这 7 个类零 Spring 依赖也必须零？

---

## G2.3 · BlockAssembler（水滴→水桶）✅ 2026-11-24

### 职责

无状态拼装器：消费 StreamChunk 溪流，攒出结构化 assistant Message。`push(chunk)` 按 index 累积，`finish()` 后产出 `blocks()/usage()/finish()/message(source)`。

### 内部结构（自己设计的）

- `PartialBlock`（text 累积 + toolCallId/name + 已冻结的成品 block）
- `LinkedHashMap<index, PartialBlock>` + order 列表保序
- `ensure(index, type)`：delta 先于 BlockStart 到达也能接（乱序防御）
- BlockEnd "first close wins"：冻结后忽略迟到分片
- MaxTokens：丢弃 ToolCallBlock（残缺的调用不可执行）

### 关键设计决定（亮点）

**空流不抛异常**：只有 Usage/Finish 时 blocks() 返回空表、finish() 保留语义（如 ToolCalls）。把"异常与否"编码进 FinishReason 值对象，让上层按"为什么空"分流——事件溯源风格：**可判断的状态优于抛出的异常**。

### 单元测试 7 个

正常文本流 / ToolCall 分片重组 / 空流元数据保留 / 无 Finish 默认 Stop / 乱序 / MaxTokens 丢工具块 /（第 7 个自己加的）

### 教训

javadoc 模板占位句（"处理push/表示BlockAssembler"）不如不写——注释要写"为什么"。

---

## G2.4 · LLM 适配器（进行中）

## G2.2 · domain.llm 端口与流式值对象 ✅ 2026-11-24

### 一句话总纲

8 个类 = 三个问题：**向模型要什么**（GenerateOptions）、**模型怎么一点点回**（StreamChunk 全家）、**回完之后的三种元信息**（FinishReason / TokenUsage / LlmFailure）。另外两个接口回答"找谁要"：ILlmRuntimePort 是总闸，LlmAdapter 是各品牌插头。

### 8 个类逐个说

| 类 | 形状 | 干什么 | 为什么需要它 |
| --- | --- | --- | --- |
| `GenerateOptions` | record | 一次生成的全部入参 | **连 provider/baseUrl/apiKey 都打进去** → 切换模型渠道=换参数，不是改代码重启（模型热切换的原理） |
| `StreamChunk` | sealed + 8 record | 流式分片 | LLM 的回答是 token 溪流，不是一整块 |
| `FinishReason` | sealed | 为什么停了 | `Stop`=说完；`MaxTokens`=被截断（第 04 站续写 4 次的依据）；`ToolCalls`=模型请求调工具；`Error`=失败。ReactLoop 的路标 |
| `TokenUsage` | record | 输入/输出 token 数 | 上下文压缩（保留 1/4）、压力阈值 100k 的计量单位 |
| `LlmFailure` | record | 归一化的失败 | 各家 API 错误格式五花八门 → 统一翻译，上层不认识任何一家的协议 |
| `ILlmRuntimePort` | interface | 领域唯一模型插座 | domain 只 import 它，永不 import HttpClient——六边形的"里侧" |
| `LlmAdapter` | interface | 单厂商适配契约 | DeepSeek/OpenAI/Anthropic 各一个实现——六边形的"外侧插头" |
| `ResolvedRetryPolicy` | sealed | 重试策略解析结果 | `retryPolicy()` 的返回值：重试几次/间隔/对哪些错误生效 |

### StreamChunk 的 8 种水滴

| 变体 | 含义 |
| --- | --- |
| `BlockStart` / `BlockEnd` | 一段内容的左右括号（带 index 配对） |
| `TextDelta` | 正文增量 |
| `ReasoningDelta` | 思考过程增量（R1 类模型） |
| `ToolCallDelta` | **工具调用的参数 JSON 也是流式逐片吐的**——最反直觉的一块，BlockAssembler 负责拼完整 |
| `Usage` / `Finish` | 元信息帧（花多少 / 为什么停） |
| `Retry` | 上游要求重试的信号（推给前端显示"重试中"） |

### 一次生成的旅程

```text
Agent 要回答
→ GenerateOptions(provider/model/messages/maxTokens/apiKey…)   ← 要什么（渠道信息也打进去→热切换不重启）
→ ILlmRuntimePort.stream(options) : Flow.Publisher<StreamChunk> ← 领域唯一插座
→ 端口按 provider 路由到 LlmAdapter 实现                        ← 总闸选插头
→ adapter 发 HTTP SSE，一行 JSON 翻译成一个 StreamChunk 发布      ← 水滴
→ BlockAssembler（G2.3）把水滴攒回 Message                      ← 水桶
→ Finish 帧(为什么停) / Usage 帧(花多少) / 失败归一为 LlmFailure
```

### 为什么返回 Flow.Publisher 而不是 String

1. **SSE 直通**：第 03 站要把增量实时推给浏览器——Publisher 是"推"语义，来一片推一片；String 要攒一桶才给
2. **背压**：Flow.Subscriber 可 `request(n)` 按消费能力拉取，2000 token 的大回答不会灌爆内存
3. **惰性**：订阅才发起 HTTP 请求

### port 与 adapter 两级的关系

`ILlmRuntimePort` 稳定不变（领域只认识它）；`LlmAdapter` 可插拔（每家协议一个实现）。**新增厂商 = 新增一个 adapter 实现 + registerAdapter 注册，领域零改动**——这就是"新增渠道零新 Bean"的根基。

### 踩坑实录

| 坑 | 教训 |
| --- | --- |
| `mvn -pl domain compile` 报"types 包不存在" | `-pl` 单模块模式下依赖从 ~/.m2 解析；没 install 过必须 `-am`（also-make） |
| 工具调用参数也是流的 | `ToolCallDelta` 存在的原因：模型吐工具参数 JSON 也是一片一片的，G2.3 的 BlockAssembler 要负责把它拼完整 |
