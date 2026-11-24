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

## G2.2 · domain.llm 端口与流式值对象 ✅ 2026-11-24

### 一句话总纲

8 个类 = 三个问题：**向模型要什么**（GenerateOptions）、**模型怎么一点点回**（StreamChunk 全家）、**回完之后的三种元信息**（FinishReason / TokenUsage / LlmFailure）。另外两个接口回答"找谁要"：ILlmRuntimePort 是总闸，LlmAdapter 是各品牌插头。

### 一次生成的旅程

```text
Agent 要回答
→ GenerateOptions(provider/model/messages/maxTokens/apiKey…)   ← 要什么（渠道信息也打进去→热切换不重启）
→ ILlmRuntimePort.stream(options) : Flow.Publisher<StreamChunk> ← 领域唯一插座
→ 端口按 provider 路由到 LlmAdapter 实现                        ← 总闸选插头
→ adapter 发 HTTP SSE，一行 JSON 翻译成一个 StreamChunk 发布      ← 水滴
→ BlockAssembler（G2.3）把水滴攒回 Message                      ← 下一站
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
