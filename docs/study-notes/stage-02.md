# 第 02 站学习笔记 — 领域模型与 LLM（完整版）

> 站点：第 02 站 · 日期：2026-11-24 ~ 11-27 · 状态：G2.1~G2.5 ✅（G2.6 收站中）
> 上一站：[stage-01](stage-01.md) · 架构总览：[technical-architecture](../technical-architecture.md) §2.3

---

## 全站地图：第 02 站在造什么

**终点**：`POST /api/agent/message` → 大模型 → 返回 assistant 文本。
**不造**（后续站的）：SSE 流（03）、会话持久化（03）、ReAct 循环（04）、工具（04）、策略树（05）。

```text
浏览器/curl
   │ POST /api/agent/message {agentId, message, channelCode?}
   ▼
AgentController        (trigger/http)     ── HTTP 协议层：@PostMapping + @RequestBody
   ▼
AgentApi               (trigger/service)  ── IAgentApi 实现：try/catch 包出 Response 语义
   ▼
IAgentUseCase          (case 接口)        ── case 层契约
   ▼
AgentUseCase           (cases/agent)      ── 薄用例：校验→组消息→流式调用→收流→拼DTO
   │
   ├── GenerateOptions  ── "要什么"的打包
   ▼
ILlmRuntimePort        (domain 端口)      ── 领域唯一模型插座（接口）
   ▼
InMemoryLlmRuntimePort (infra)           ── Map<provider, LlmAdapter> 路由
   ▼
DeepSeekAdapter        (infra, 920行)    ── HTTP+SSE → StreamChunk 溪流
   ▼
BlockAssembler         (domain/llm/...)  ── 水滴攒回水桶 = Message
   ▼
AgentMessageResponseDTO (api/dto)        ── 翻译回协议格式
```

**依赖方向铁律**：trigger → api/case → domain ← infrastructure；types 是所有人脚下的契约层。

---

## G2.1 · types 消息模型 ✅ 2026-11-24

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

### 三个设计点

1. **content 为什么是 `List<ContentBlock>` 而不是 `String`？** 现代 LLM 一条消息是混合物（一边说"我查一下"一边发起工具调用）。拍平成 String 丢结构，后面每一步都得重新解析——**结构化保存在源头**。
2. **`role` 和 `source` 是同一句话的两个视角**：`role` 给**模型协议**看（LLM API 只认它）；`source` 给**领域逻辑**看（渲染、审计、事件分类）。一个对外，一个对内。
3. **`sealed` 的穷尽性**：加 `ImageBlock` 时所有 `switch (block)` 编译报错——类型系统替你记住业务约定。

### 为什么放 types 不放 domain

types 是"边界上的通用语"：插件只被允许依赖它；稳定依赖原则（最稳定的契约放最里层）；零 Spring，插件工程不拉 Spring 也能编译。domain 依赖 types，永不反向。

---

## G2.2 · domain.llm 端口与流式值对象 ✅ 2026-11-24

### 一句话总纲

8 个类 = 三个问题：**向模型要什么**（GenerateOptions）、**模型怎么一点点回**（StreamChunk 全家）、**回完之后的三种元信息**（FinishReason / TokenUsage / LlmFailure）。两个接口回答"找谁要"：ILlmRuntimePort 是总闸，LlmAdapter 是各品牌插头。

### 8 个类逐个说

| 类 | 形状 | 干什么 | 为什么需要它 |
| --- | --- | --- | --- |
| `GenerateOptions` | record | 一次生成的全部入参 | **连 provider/baseUrl/apiKey 都打进去** → 切换模型渠道=换参数，不是改代码重启（模型热切换的原理） |
| `StreamChunk` | sealed + 8 record | 流式分片 | LLM 的回答是 token 溪流，不是一整块 |
| `FinishReason` | sealed | 为什么停了 | `Stop`=说完；`MaxTokens`=被截断（第 04 站续写 4 次的依据）；`ToolCalls`=模型要调工具；`Error`=失败。ReactLoop 的路标 |
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

`ILlmRuntimePort` 稳定不变（领域只认识它）；`LlmAdapter` 可插拔（每家协议一个实现）。**新增厂商 = 新增一个 adapter 实现 + registerAdapter 注册，领域零改动**——"新增渠道零新 Bean"的根基。

---

## G2.3 · BlockAssembler（第一个真逻辑类，水滴→水桶）✅ 2026-11-24

### 职责

无状态拼装器：消费 StreamChunk 溪流，攒出结构化 assistant Message。`push(chunk)` 按 index 累积，`finish()` 后产出 `blocks()/usage()/finish()/message(source)`——**token 流 → assistant Message 的唯一路径**。

### 内部结构（自己设计的）

- `PartialBlock`：text 累积（StringBuilder）+ toolCallId/name + 已冻结的成品 block
- `LinkedHashMap<index, PartialBlock>` + order 列表保序（index 乱序到达也按 BlockStart 顺序输出）
- `ensure(index, type)`：**delta 先于 BlockStart 到达也能接**（乱序防御——真实 SSE 可能这样）
- BlockEnd "first close wins"：冻结后忽略迟到分片（stragglers）
- MaxTokens 终止：丢弃 ToolCallBlock（残缺调用不可执行）

### 关键设计决定（本站亮点）

**空流不抛异常**：只有 Usage/Finish 时 blocks() 返回空表、finish() 保留语义（如 ToolCalls）。把"异常与否"编码进 FinishReason 值对象，让上层按"为什么空"分流——**事件溯源风格：可判断的状态优于抛出的异常**。

### 单元测试 7 个（全绿）

正常文本流 / ToolCall 分片重组（id/name 第一片 + arguments 三片）/ 空流元数据保留 / 无 Finish 默认 Stop / 乱序 delta / MaxTokens 丢工具块 / 补充用例。**同包测试 + 手工构造小 chunk 直接喂，不用起任何网络服务**——这就是"测试可以放同包访问包私有成员，不必为此破坏封装"。

### 教训

javadoc 模板占位句（"处理push/表示BlockAssembler"）不如不写——注释要写"为什么"。

---

## G2.4 · LLM 适配器 ✅（有条件，桩测试欠账进 G2.6 还）

### 本站产物

`DeepSeekAdapter`（920 行，对齐原版规模）：HTTP POST `/chat/completions`（stream=true）→ 逐行读响应 → `translateSse` 解析 → `SubmissionPublisher<StreamChunk>` 发布；含 `[DONE]` 终止检测、非流式兜底（`doNonStreamFallback`）、错误归一化（`publishError`）。`OpenAiCompatibleAdapter` 继承差异化。`InMemoryLlmRuntimePort` 做 Map 路由（+unregister/fallback 工厂）。

### SubmissionPublisher 在链里的角色（口试答错的，这里记牢）

**生产者-消费者桥，协议无关的流式枢纽**：

```text
translateSse 每解析一行 JSON → 构造 StreamChunk → publisher.submit(chunk)
                    ↓
stream() 把 publisher 作为 Flow.Publisher 返回给调用方
                    ↓
订阅它的是用例层收流逻辑（G2.5 的 CountDownLatch 订阅者；第 04 站换成 ReactLoopAgent）
```

它和"各类 Adapter"**无关**——adapter 是生产者，它是桥。记住：**桥不关心水从哪来（哪家协议），只关心把水送给谁（订阅者）**。

### 正确但未还的账

SSE 解析的同包桩测试：喂 5 行假 SSE 文本 → 断言 chunk 序列（G2.6 必还）。

---

## G2.5 · api/case/trigger 三层薄壳 ✅ 2026-11-27

### channelCode 是什么（模型路由第一课）

**一个渠道 = 一家可用的模型服务**（供应商+协议+凭据+网关地址的组合），`channelCode` 是它的名字。分工：

- `channelCode`：**业务视角**（面向请求方："我要用哪个渠道"）
- `provider`：**协议适配视角**（面向端口路由，Map 的 key）
- G2.5 最简处理：两者相等；**第 09 站才让它们分化**（harness_model_setting 表接管）

provider/model 的来路：请求带 channelCode → 用它；没带 → 回落 `harness.llm.deepseek.*` 配置的默认渠道。**API key 永远不在请求里**——渠道细节在配置/数据库，这就是"换模型=切渠道码，代码零改动"。

### 三层各自的薄法（为什么绕三层）

| 层 | 文件 | 薄在哪 | 为什么必须存在 |
| --- | --- | --- | --- |
| trigger/http | `AgentController` | `@PostMapping` + 一行委托 | 只管 HTTP 协议；换 gRPC/WS 只换这层 |
| trigger/service | `AgentApi` | try/catch 包出 Response 语义 | 实现 api 模块的**门面接口**；异常→`invalidArgument`/`internalError` 的翻译站 |
| case | `AgentUseCase` | 校验→组消息→调端口→收流→拼 DTO | 用例编排；第 05 站进化为策略树入口，**方法名不变内脏进化** |

**不直接调端口的三层理由**（口试题）：协议解耦（HTTP 换 gRPC）、门面隔离（trigger 不 import domain 内部）、用例可进化（策略树/事务/审计挂载点）。每层薄到几乎透明——**薄不是缺陷，是可替换性的代价**。

### AgentUseCase 的收流模板（本站核心代码模式，背下来）

```java
var assembler = new BlockAssembler();
var done = new CountDownLatch(1);
var error = new AtomicReference<Throwable>();   // 跨线程带回异常

publisher.subscribe(new Flow.Subscriber<>() {
    onSubscribe → subscription.request(Long.MAX_VALUE)   // 拉全部
    onNext      → assembler.push(item)                    // 水滴进桶
    onError     → error.set(t); done.countDown()          // 记异常+放行
    onComplete  → done.countDown()
});
done.await();                    // ★ 订阅是异步的！不等就 return 空对象
if (error.get() != null) → 可读错误进 DTO.error 字段（不抛 500）
else if (blocks 为空)    → "模型未返回任何内容"（roadmap 点名的坑）
```

**踩过的坑**：第一版忘 `await()`，订阅完直接拼 DTO → 返回永远是空的。**"subscribe 返回 ≠ 数据到了"是流式编程第一课**。（await 要处理 InterruptedException：`Thread.currentThread().interrupt()` + 记入 error——你终版处理对了。）

### DTO 与领域对象之间永远隔一道翻译

`AgentMessageResponseDTO.messages` 是 `List<Map<String,Object>>`（wire 格式），**不是** `List<Message>`。用例里 `toMessageMap()`：遍历 content blocks → `instanceof TextBlock` → 拼纯文本 → `{"role", "content"}`。**roadmap 常见坑第一条：DTO 是协议对象，Message 是领域对象，两者混用会让 case 和 trigger 互相污染。**

### 异常分流表

| 异常 | HTTP 出口 | 语义 |
| --- | --- | --- |
| IllegalArgumentException（校验失败） | `Response.invalidArgument` | 请求方的错 |
| 其他 RuntimeException | `Response.internalError` | 系统的错 |
| 模型流失败 | 不抛，进 `DTO.error` 字段 | 业务失败可携带部分结果 |

### 装配（infrastructure.config）

`HarnessApplicationConfig`：`DeepSeekAdapter` Bean（base-url/api-key/model/context-window 从 `harness.llm.deepseek.*` 读）+ `ILlmRuntimePort` Bean（**注入 `List<LlmAdapter>` 自动收集容器里全部适配器**，逐个 registerAdapter——这个写法本身就是"新增渠道零改装配"）。

---

## 本站踩坑总表（比答对更值钱）

| # | 坑 | 教训 |
| --- | --- | --- |
| 1 | Message 少 source 字段（javadoc 写了、record 没有） | **javadoc 会撒谎，签名不会**——侦察看 record 头 `rg -A 6` |
| 2 | `contents` vs 原版 `content` | 能同名就同名，命名偏差让 diff 永远飘红 |
| 3 | MessageSource 想当然写成 enum | 别猜形状看原版：sealed interface + 嵌套 records |
| 4 | `mvn -pl` 编译报"types 包不存在" | `-pl` 依赖走 ~/.m2；没 install 过必须 `-am`（also-make） |
| 5 | 订阅后忘 await，返回空对象 | subscribe 返回 ≠ 数据到了；CountDownLatch 是流式转同步的桥 |
| 6 | `IAgentUserCase` 拼写（Use/User）连错三次 | 接口名从原版抄准；重构趁早（Refactor→Rename），拖一次成本翻倍 |
| 7 | AgentApi 直接依赖实现类 | 接口隔离断在最后一米：trigger 依赖 case 契约，不依赖实现 |
| 8 | 920 行适配器零测试就宣称"完成" | **"写完了"和"验证过了"之间隔的那道坎，叫测试** |
| 9 | javadoc 模板占位句（"处理push"） | 注释要写"为什么"；模板句不如不写 |
| 10 | 失败路径 `info:"provider"`（异常消息碎渣当用户文案） | "可读错误"= 用户能看懂下一步做什么，不是异常 toString |

---

## 自测题（合上笔记能答才过关）

1. 一条用户消息"查天气"在 types 里长什么样？四条消息怎么循环起来？（画出来）
2. `ILlmRuntimePort.stream()` 为什么返回 `Flow.Publisher<StreamChunk>`？（三个理由）
3. BlockAssembler 的空流为什么**不**抛异常？上层怎么区分"正常空"和"异常空"？
4. SubmissionPublisher 是谁、桥接了谁和谁？（G2.4 口试错的，G2.6 必考）
5. channelCode 和 provider 的区别与分工？什么时候开始分化？
6. 为什么要绕 Controller→AgentApi→UseCase 三层而不直接调端口？
7. 收流模板五要素？（assembler / latch / error ref / request(MAX) / await）
8. DTO 的 messages 为什么是 `List<Map>` 不是 `List<Message>`？
9. 无 key 时"可读错误"应该长什么样？在哪一层拦截最合适？

---

## 未竟事项 → 后续站

| 事项 | 去向 |
| --- | --- |
| SSE 实时推流（sendMessageStreaming） | 第 03 站 |
| 会话恢复 / 事件落库（SessionLog） | 第 03 站 |
| ReactLoopAgent / Inbox / turn 循环 | 第 04 站 |
| 策略树四节点（`AgentMessageFactory` 已就位未启用） | 第 05 站 |
| channelCode ≠ provider（模型设置路由表） | 第 09 站 |
| ImageBlock | 有图片需求时 |
| ResolvedRetryPolicy 的真实使用 | 第 09 站 |
| 无 key 前置校验的可读错误优化 | G2.6 收站 |
