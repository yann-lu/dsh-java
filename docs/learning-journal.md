# 学习日志 — 从 0 重建进度存档

> **这是本项目的存档点（checkpoint）文件。**
> 用途：对话上下文丢失（新会话/被压缩）时，AI 先读本文件即可无缝接续；用户说"继续"即表示"读日志、接着教"。
> 维护规则：**每完成一个小目标，AI 负责更新本文件**（勾选状态 + 写下一步）；用户随时可以在"卡点笔记"里补自己的话。

---

## ⚡ 当前进度（新会话从这里接手）

- **更新时间**：2026-11-23
- **所在阶段**：第 01 站 · 骨架与启动
- **已完成**：仓库骨架、技术架构方案、**G1.1 启动类**（应用可启动，第一次因 profile 未激活连错库，已解决）
- **下一个动作**：等待用户完成 **G1.2 健康检查 + 配置对照阅读**（含 4 道口头题）
- **给 AI 的接手指令**：读"学习协议"和小目标清单 → 收 G1.2 作业（curl 输出 + 4 道题答案）→ 通过则勾掉、布置 G1.3

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
- [ ] **G1.2 健康检查 + 建库** — 用户选定 **MySQL 路线**（localhost:3306，root 空密码，`sql.init.mode=always` 已在 yml）
  - 步骤：① 建库 `deepseek_harness_java`（schema.sql 只建表不建库）→ ② 默认 profile 启动 → ③ `/actuator/health` 返回 UP → ④ `show tables` 应见 19 张表
  - 口头题（4 道）：scanBasePackages 为什么扫整个 harness 包；Hikari 连不上库为何 `Start completed`（答案藏在 yml 的 hikari 配置里）；`sql.init.mode=always` 干什么、MySQL 路线为什么必须靠它；standalone 的 H2 为什么带 `MODE=MySQL`
- [ ] **G1.3 启动信息 Runner** — 写 `StartupInfoRunner`（ApplicationRunner），启动时打印端口/profile/数据源
- [ ] **G1.4 静态占位页** — `static/index.html` 写一行 "dsh-java rebuilding"，`curl /` 可见
- [ ] **G1.5 站点收尾** — types 依赖树检查无内部模块；`mvn -DskipTests package` 全绿；打 tag `stage-01`；更新 mapping 登记本站类

---

## 卡点笔记（用户/AI 随时追加，按时间倒序）

| 日期 | 小目标 | 卡点/发现/学到什么 |
| --- | --- | --- |
| 2026-11-23 | G1.1 | 首次启动失败：IDEA 直接跑 Run 按钮没激活 `standalone` profile → 回落 default → default 数据源是原版作者的局域网 MySQL（192.168.1.108:13306）→ Connection refused。**教训：读日志先看最早的 WARN（`No active profile set`），再看 Caused by 链的最底部（`Connection refused`）；启动类代码本身没问题** |
