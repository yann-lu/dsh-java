# 学习日志 — 从 0 重建进度存档

> **这是本项目的存档点（checkpoint）文件。**
> 用途：对话上下文丢失（新会话/被压缩）时，AI 先读本文件即可无缝接续；用户说"继续"即表示"读日志、接着教"。
> 维护规则：**每完成一个小目标，AI 负责更新本文件**（勾选状态 + 写下一步）；用户随时可以在"卡点笔记"里补自己的话。
> 配套惯例：**每站关站后写一份复习笔记** `docs/study-notes/stage-0X.md`（知识点卡片 + 踩坑实录 + 自测题），回顾学习过程看那里。

---

## ⚡ 当前进度（新会话从这里接手）

- **更新时间**：2026-11-27
- **所在阶段**：第 02 站 · G2.1~G2.5 全部完成，**G2.6 收站中**
- **里程碑**：2026-11-27 端到端首次打通——`POST /api/agent/message` 真调 DeepSeek 返回 assistant 文本
- **完整笔记**：`docs/study-notes/stage-02.md`（已扩写为完整版：全站地图/收流模板/三层薄法/踩坑总表 10 条/自测 9 题）
- **G2.6 待办**：①无 key 可读错误（当前 info:"provider" 不可读）②补 G2.4 SSE 桩测试 ③types 零 Spring 复查 ④mapping 登记 + tag `stage-02` + push
- **给 AI 的接手指令**：收 G2.6 三项交付 → 全过则打 tag 关站 → 开第 03 站（SSE 与会话事件，读 `docs/rebuild-roadmap/03-sse-and-session-events.md`）

---

## 学习协议（双方约定）

**用户**：
1. 按小目标自己写代码；**只看原版类的签名和注释，不看实现体**
2. 卡住超过 ~15 分钟才允许翻原版实现；看懂后**关掉，重写**
3. ❌ 硬性禁令：禁止复制任何实现体（原版或 personal 版）进本仓库

**AI（教练）**：
1. **不替用户写实现体**；可以：讲思路、指方向、解释概念、review 用户代码、陪跑 diff、答疑
2. 每个小目标固定输出四件套：**目标 / 涉及文件 / 步骤 / 验收标准**（提示不剧透实现）
3. 每完成一个小目标 → 更新本文件 → 布置下一个
4. 原项目路径：`/Users/luyan/workspace/luyan_workspace/deepseek-harness-java`（只读参考）；personal 版同样是"答案"，规则同

**配置文件的特殊约定**：`application.yml` / `harness.yml` / `schema.sql` 等已整体拷入（管道性质，不练手写），但每个用到它们的站要完成对应的**对照阅读任务**——能回答"这里面每个 key 为什么存在"。

---

## 站点进度

| 站 | 主题 | 状态 |
| --- | --- | --- |
| 01 | skeleton-and-boot | ✅ 已关站（tag `stage-01`） |
| 02 | domain-model-and-llm | 🔨 进行中（G2.1 ✅，G2.2 布置中） |
| 03~15 | （见 roadmap） | ⬜ 未开始 |

---

## 小目标清单

### 第 01 站 · 骨架与启动（POM/包目录/配置已预置，本站只写 Java + 验收）

- [x] **G1.1 启动类** — 写 `Application.java`，standalone profile 能启动 ✅ 2026-11-23（踩坑：IDEA 直跑未激活 profile → 连到原作者库 → 学会读日志：先看最早 WARN，再看 Caused by 底部）
  - 文件：`deepseek-harness-java-app/src/main/java/cn/xiaofuge/deepseek/harness/app/Application.java`
  - 验收：日志出现 `Started Application`，Tomcat 占 8090；能说出 `scanBasePackages` 和 `@MapperScan` 各自为什么这么写
- [x] **G1.2 健康检查 + 建库** — ✅ 2026-11-23（MySQL 路线：localhost:3306，health UP，19 张表建成；schema.sql 补列备注后重建库。4 道口头题转入 G1.3 开场作业）
- [x] **G1.3 启动信息 Runner** — ⚠️ 教练代笔（2026-11-23）+ teach-back 重测 4/5 通过（2026-11-24）。首答 1.5/7 教训：activeProfiles=空数组 ≠ "default" 回退名；Q6/Q7（sql.init、MODE=MySQL）第 07 站抽查
- [x] **G1.4 静态占位页** — ✅ 2026-11-24（**事件**：先拷原版控制台充数→触发硬性禁令→当场撤回，自己重写 101 字节占位页；学到：Spring Boot 内建静态资源映射、git 暂存区 AD/AM 状态含义）
- [x] **G1.5 站点收尾** — ✅ 2026-11-24（依赖树检查、`mvn package` 全绿、jar 启动 health UP + 首页正确；**tag `stage-01` 已打，第 01 站关站**）

---

### 第 02 站 · 领域模型与 LLM（终点：POST /api/agent/message 真调一次大模型）

- [x] **G2.1 types 消息模型** — ✅ 2026-11-24（Message/MessageSource/ContentBlock+4 块，7 个类零 Spring；踩坑：javadoc 撒谎、contents→content、MessageSource 是 sealed 非 enum；ImageBlock 与 Tool 契约类按减法推迟，登记 mapping）。笔记：`docs/study-notes/stage-02.md`
- [x] **G2.2 domain.llm 端口与流式值对象** — ✅ 2026-11-24（8 个契约类含 ResolvedRetryPolicy，零 Spring import；踩坑：`-pl` 单模块编译依赖走 ~/.m2，没 install 过必须带 `-am`）
- [x] **G2.3 BlockAssembler + 单测** — ✅ 2026-11-24（7 测试全绿；亮点：空流不抛异常、把"异常与否"编码进 FinishReason 语义让上层分流——事件溯源风格设计决定；ensure() 乱序防御）
- [x] **G2.4 LLM 适配器** — ⚠️ 有条件通过（2026-11-24）：DeepSeekAdapter 920 行对齐原版规模（+OpenAi/Anthropic/InMemoryPort，2226 行提交 `4b18302`），超出 roadmap 最小实现范围；**欠账：SSE 同包桩测试未交，G2.6 必还**。SubmissionPublisher=生产者消费者桥概念经补课建立
- [x] **G2.5 api/case/trigger 三层薄壳** — ✅ 2026-11-27（**端到端打通**：POST /api/agent/message 真调 DeepSeek 返回 assistant 文本；踩坑：订阅后忘 await 返回空对象→"subscribe 返回≠数据到了"；IAgentUserCase 拼写连错三次；AgentApi 最终依赖契约接口+异常分流；config 用 List<LlmAdapter> 自动收集。遗留：无 key 时 info:"provider" 不可读 → G2.6 修）
- [ ] **G2.6 站点验收** — ①修无 key 可读错误（端口/适配器前置校验，别等 401）②补 G2.4 SSE 桩测试 ③types 零 Spring 复查 ④mapping 登记 + tag `stage-02` + push

---

## 卡点笔记（用户/AI 随时追加，按时间倒序）

| 日期 | 小目标 | 卡点/发现/学到什么 |
| --- | --- | --- |
| 2026-11-27 | G2.5 | **端到端首次打通**：assistant 真实回复落地。核心坑：①订阅异步性——subscribe 返回≠数据到了，忘 await 返回永远空对象 ②接口隔离断在最后一米：AgentApi 注入实现类而非 IAgentUseCase ③拼写 User/Use 三连 ④异常消息碎渣（info:"provider"）≠ 可读错误。**事件：learning-journal.md 曾被误删，从 236b3fe 恢复——日志是存档点，commit 前别动它** |
| 2026-11-26 | G2.4 | **"写完了"≠"验证过了"**：920 行适配器零测试。SubmissionPublisher 是协议无关的生产者-消费者桥（adapter 生产→桥→订阅者），口试三道 1错2半3不知道后补课 |
| 2026-11-24 | G2.2 | **Maven reactor 教训**：`mvn -pl domain compile` 报"types 包不存在"——`-pl` 单模块模式下依赖从本地仓库 ~/.m2 解析，工程从未 install 过 → 必须带 `-am`（also-make 连带编译依赖模块）或先全量 install。**另有硬核知识**：工具调用的参数 JSON 也是流式逐片吐出的（ToolCallDelta 存在的原因） |
| 2026-11-24 | G1.4 | **控制台拷贝事件**：把原版 3300 行控制台拷进 static/ 充数 → 触发唯一硬性禁令 → 撤回重写。教训：①静态页是第 14 站的实现体，不是配置 ②git `AD/AM`：commit 提交的是暂存区不是工作区，删文件不清 add（`git restore --staged`）③Spring Boot 内建静态资源映射（classpath:/static → index.html 欢迎页） |
| 2026-11-23 | G1.1 | 首次启动失败：IDEA 直接跑 Run 按钮没激活 `standalone` profile → 回落 default → default 数据源是原版作者的局域网 MySQL（192.168.1.108:13306）→ Connection refused。**教训：读日志先看最早的 WARN（`No active profile set`），再看 Caused by 链的最底部（`Connection refused`）；启动类代码本身没问题** |
