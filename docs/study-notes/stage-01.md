# 第 01 站学习笔记 · 骨架与启动（tag: stage-01）

> **用法**：这是一份复习卡片，不是操作手册。先做 §6 自测，答不出的再回来查卡片。
> 惯例：每站一份 `stage-0X.md`，站关站当天写，越早写回忆越完整。

---

## 1. 这一站到底建了什么（空间感）

一条会呼吸的最小链路：

```text
mvn package → java -jar app.jar → Spring Boot 启动
  → Application（全包扫描 + MapperScan）
  → 数据源连 MySQL（localhost:3306）→ sql.init 跑 schema.sql 建 19 张表
  → Tomcat 占 8090 → /actuator/health 返回 UP
  → StartupInfoRunner 打印启动体检信息
  → 浏览器访问 / 拿到静态占位页
```

依赖方向（全项目的宪法，ArchUnit 之后会守护它）：

```text
app → trigger → api/case → domain ← infrastructure
domain → types
```

---

## 2. 知识点卡片

### 卡片 A · Spring Boot 扫描机制 ⭐（G1.1 踩坑来源）

- `@SpringBootApplication` **默认只扫启动类所在包及子包**
- 我们的 Controller 在 `harness.trigger.http`、用例在 `harness.cases`——都是 `harness.app` 的**兄弟包不是子包**，默认扫不到 → 一个 bean 都不会注册
- 所以必须 `@SpringBootApplication(scanBasePackages = "cn.xiaofuge.deepseek.harness")` 把扫描根提到公共祖先
- `@MapperScan("...infrastructure.dao")`：空包不报错，但包名错了到第 07 站才炸——**当时看不出来，埋雷型错误**

### 卡片 B · profile 与占位符 ⭐（teach-back 1.5/7 的重灾区）

- `activeProfiles` = **你显式激活的** profile（IDEA Active profiles / `-Dspring.profiles.active=...`）
- 一个都没激活 → `getActiveProfiles()` 返回**空数组**；`"default"` 只是日志里的**回退显示名**，不是激活值
- `@Value("${spring.profiles.active}")` **不给默认值 → 启动直接抛 `Could not resolve placeholder`**
- 解法：给默认值 `${...:default}`，或用 `Environment.getActiveProfiles()`（可空、可多个，语义上 profile 本来就是"一组状态"）
- 记忆钩子：**空数组 ≠ "default" 这个字符串**

### 卡片 C · sql 初始化三兄弟 ⭐（第 07 站要抽查）

| 配置 | 值 | 效果 |
| --- | --- | --- |
| `spring.sql.init.mode` | `always` | 每次启动都跑 schema.sql |
| | `embedded`（**默认**） | 只对 H2/Derby 这类内嵌库跑 |
| | `never` | 永不 |

- **MySQL 路线必须显式 `always`**，否则回落 embedded → 一张表都不建
- 敢 always 是因为 schema.sql 全是 `CREATE TABLE IF NOT EXISTS` / `INSERT ... WHERE NOT EXISTS`——**幂等**
- H2 的 `MODE=MySQL`：H2 兼容模式，让 `INSERT IGNORE`、`ON DUPLICATE KEY`、`TIMESTAMP(3)` 这些 MySQL 方言能被解析 → **同一份 DDL 两种库通用**
- 建表顺序：schema.sql 只建**表**，**库**要自己 `CREATE DATABASE`（不然 Unknown database）

### 卡片 D · 连接池真相（Hikari 之谜）

- 你 yml 里的 `initialization-fail-timeout: 0` = 池子**启动时不做任何初始连接尝试**
- 所以连不上库时照样打印 `HikariPool-1 - Start completed`——**池子开门 ≠ 缸里有水**
- 真正建连接发生在第一次 `getConnection()` → 失败 → `request timed out after 3007ms`
- 若设成 `30000`：启动时阻塞最多 30 秒试连，失败则**应用启动直接失败**（fail-fast 提前到启动期）

### 卡片 E · 启动钩子时序

```text
@PostConstruct        （refresh 中间，bean 刚造好就跑，依赖可能没就绪，顺序不保证）
   ↓
ApplicationRunner     （refresh 完成后；抛异常会阻断启动 = fail-fast 体检站）
   ↓
ApplicationReadyEvent （比 Runner 更晚一点）
```

- 读配置：简单单值用 `@Value`（记得带默认值）；可缺省/多值/动态用 `Environment`
- 日志打印外部凭据（JDBC URL）要脱敏：`url.replaceAll("//[^@/]+@", "//***@")`

### 卡片 F · 静态资源（零 Controller 的页面）

- `classpath:/static/`、`/public/`、`/resources/`、`/META-INF/resources/` 由 **`ResourceHttpRequestHandler`** 内建映射
- 访问 `/` 自动找 `index.html` 当欢迎页——约定优于配置
- 第 14 站的控制台也只是"更复杂的静态文件"，服务机制不变

### 卡片 G · 日志排错方法论 ⭐（最通用的一课）

**读日志两步法**：
1. 先看**最早的 WARN/INFO**（病根）——比如 `No active profile set`
2. 再看 **Caused by 链的最底部**（死因）——比如 `java.net.ConnectException: Connection refused`

中间那几十行堆栈是"尸体解剖过程"，不用逐行读。

---

## 3. 踩坑实录（现象 → 根因 → 一句话教训）

| 日期 | 现象 | 根因 | 教训 |
| --- | --- | --- | --- |
| 11-23 | 启动失败 Connection refused | 没激活 profile → default 指向**原作者**的局域网 MySQL | 用卡片 G 两步法；配置值要问"这是谁的库" |
| 11-23 | 编译失败：LoggerFactory 引用不明确 | IDEA 自动导入了 `org.mybatis.logging.LoggerFactory` | **自动导入不可信，import 区要过目**，日志认准 `org.slf4j` |
| 11-23 | G1.3 写不出（教练代笔） | IDEA 导了假 `Environment`（`org.apache.ibatis.mapping`）+ `String[]` 塞 `String` | 同上陷阱第二次；`getActiveProfiles()` 返回**数组** |
| 11-24 | teach-back 首答 1.5/7 | activeProfiles 误区、Hikari/sql.init/MODE 全答不上 | 天天在用 ≠ 掌握，口头复述才是验收 |
| 11-24 | 首页是完整控制台 | 把原版 3300 行控制台拷进 static/ 充数 | **复制实现体 = 幻觉进度**；按钮调的 API 后端根本不存在 |
| 11-24 | git status 出现 `AD`/`AM` | 删了文件但没清暂存区 | **commit 提交的是暂存区不是工作区**；`git restore --staged` 清 add |

---

## 4. 产出清单

- 代码：`Application.java`、`StartupInfoRunner.java`、`static/index.html`（101 字节，自己的）
- 数据：`deepseek_harness_java` 库 + 19 张表（schema.sql 已补列级备注，与原版偏差登记在 mapping）
- 文档：`technical-architecture.md`（全项目地图）、`rebuild-mapping.md`、`learning-journal.md`
- 远程：github.com/yann-lu/dsh-java，tag `stage-01`

---

## 5. 通往第 02 站的桥

- 第 02 站目标：`POST /api/agent/message` 真调一次大模型
- 起点：types 模块 7 个值对象（Message / MessageSource / ContentBlock + 4 Block）——**零 Spring 的纯 record**
- 遗留债：sql.init / MODE=MySQL 深度复查在第 07 站；控制台在第 14 站从 0 写

---

## 6. 快速自测（先自己答，再往下翻）

1. 为什么 `scanBasePackages` 必须写全包根？
2. 没激活任何 profile 时，`getActiveProfiles()` 和日志里的 "default" 分别是什么？
3. `sql.init.mode` 三档各是什么？删掉后 MySQL 路线会怎样？
4. Hikari 连不上库为什么还 `Start completed`？改成 `initialization-fail-timeout: 30000` 行为怎么变？
5. `@PostConstruct` / `ApplicationRunner` / `ApplicationReadyEvent` 按时间排序，谁最可能拿到"运行时真相"？谁抛异常会阻止启动？
6. 没写任何 Controller，`http://localhost:8090/` 的页面是谁提供的？
7. 日志里出现一屏堆栈，先看哪里？

<br>

---

<details>
<summary><strong>答案（点开对照）</strong></summary>

1. 默认只扫启动类所在包及子包；trigger/case/domain 都是 app 的兄弟包，不提根就一个 bean 都扫不到
2. `getActiveProfiles()` 是**空数组**；"default" 是日志里的回退显示名
3. always / embedded（默认）/ never；删掉后 MySQL 不是内嵌库 → 一张表不建
4. `initialization-fail-timeout: 0` 让池子启动时不做初始连接；池子装配完成 ≠ 有活连接。改 30000 后启动期阻塞试连 30 秒，失败则启动失败
5. 排序：PostConstruct → Runner → ReadyEvent；**Runner** 最适合拿运行时真相且 fail-fast（PostConstruct 顺序不保证）
6. Spring Boot 内建静态资源映射（ResourceHttpRequestHandler + classpath:/static + index.html 欢迎页约定）
7. 先看最早的 WARN（病根），再看 Caused by 链最底部（死因）

</details>

---

*第 01 站完 · 2026-11-24 · 下一份：stage-02.md*
