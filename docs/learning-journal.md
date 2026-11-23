# 学习日志 — 从 0 重建进度存档

> **这是本项目的存档点（checkpoint）文件。**
> 用途：对话上下文丢失（新会话/被压缩）时，AI 先读本文件即可无缝接续；用户说"继续"即表示"读日志、接着教"。
> 维护规则：**每完成一个小目标，AI 负责更新本文件**（勾选状态 + 写下一步）；用户随时可以在"卡点笔记"里补自己的话。

---

## ⚡ 当前进度（新会话从这里接手）

- **更新时间**：2026-11-23 深夜（今日收官归档）
- **所在阶段**：第 01 站 · 骨架与启动（进度约 80%）
- **今日完成**：仓库骨架 + 架构方案 + 远程仓库（github.com/yann-lu/dsh-java，全部推送）；G1.1 启动类 ✅；G1.2 健康检查+建库 ✅（MySQL localhost:3306）；G1.3 教练代笔 ⚠️（编译通过，待 teach-back）
- **欠账（明天开场先还）**：① G1.3 teach-back 三问 ② 4 道口头题（升级为进场门票）
- **明天的路**：还账 → G1.4 静态页（5 分钟）→ G1.5 收站（依赖树检查 + `mvn package` + tag `stage-01` + 更新 mapping）→ 进第 02 站（领域模型 + LLM 端口）
- **给 AI 的接手指令**：先收 G1.3 teach-back（三问在日志 G1.3 条目上下文：ApplicationRunner vs @PostConstruct/EventListener 时机、maskCredentials 正则含义、getActiveProfiles 为空的处理）+ 4 道口头题；全过则勾 G1.3、布置 G1.4

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
| 01 | skeleton-and-boot | 🔨 进行中 |
| 02~15 | （见 roadmap） | ⬜ 未开始 |

---

## 小目标清单

### 第 01 站 · 骨架与启动（POM/包目录/配置已预置，本站只写 Java + 验收）

- [x] **G1.1 启动类** — 写 `Application.java`，standalone profile 能启动 ✅ 2026-11-23（踩坑：IDEA 直跑未激活 profile → 连到原作者库 → 学会读日志：先看最早 WARN，再看 Caused by 底部）
  - 文件：`deepseek-harness-java-app/src/main/java/cn/xiaofuge/deepseek/harness/app/Application.java`
  - 验收：日志出现 `Started Application`，Tomcat 占 8090；能说出 `scanBasePackages` 和 `@MapperScan` 各自为什么这么写
- [x] **G1.2 健康检查 + 建库** — ✅ 2026-11-23（MySQL 路线：localhost:3306，health UP，19 张表建成；schema.sql 补列备注后重建库。4 道口头题转入 G1.3 开场作业）
- [x] **G1.3 启动信息 Runner** — ⚠️ **教练代笔**（2026-11-23）：用户自己推进了 60%（删重复 import、换 ApplicationRunner、加 run 方法），卡在 IDEA 假 Environment 导入（`org.apache.ibatis.mapping.Environment`，今晚第二次自动导入踩坑）/ `String[]`→`String` 类型不符 / environment 未存字段。终版由教练写入，**待用户 teach-back 通过才算真完成**
- [ ] **G1.4 静态占位页** — `static/index.html` 写一行 "dsh-java rebuilding"，`curl /` 可见
- [ ] **G1.5 站点收尾** — types 依赖树检查无内部模块；`mvn -DskipTests package` 全绿；打 tag `stage-01`；更新 mapping 登记本站类

---

## 卡点笔记（用户/AI 随时追加，按时间倒序）

| 日期 | 小目标 | 卡点/发现/学到什么 |
| --- | --- | --- |
| 2026-11-23 | G1.1 | 首次启动失败：IDEA 直接跑 Run 按钮没激活 `standalone` profile → 回落 default → default 数据源是原版作者的局域网 MySQL（192.168.1.108:13306）→ Connection refused。**教训：读日志先看最早的 WARN（`No active profile set`），再看 Caused by 链的最底部（`Connection refused`）；启动类代码本身没问题** |
