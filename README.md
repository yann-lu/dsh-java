# deepseek-harness-java-zero — 从 0 重建练习仓库

> 只有架构，没有代码。所有 Java 实现都由你自己写。

## 这个仓库是什么

学习同一个项目（`deepseek-harness-java`）的三个仓库，各司其职：

| 仓库 | 角色 | 用法 |
| --- | --- | --- |
| `deepseek-harness-java` | **规格书 + 标准答案** | 只读。看包路径、类名、签名、注释；实现体默认遮住，卡住才翻 |
| `deepseek-harness-java-personal` | 对照重建（过程版） | 之前按“骨架照抄+实现精简”推进的版本，可用来 diff 对照 |
| `deepseek-harness-java-zero`（本仓库） | **从 0 实现练习场** | 每一行实现代码都自己写，禁止复制任何实现体 |

## 已预置什么 / 没预置什么

**已预置（架构层，照抄是合理的）：**

- 根 POM + 7 个模块 POM：模块名、依赖方向与原工程一致（坐标多了 `-zero` 后缀防混淆）
- 全部包目录树（425 个目录，按原工程 1:1 铺好）——空目录就是地图，写到哪一站用到哪一站
- app 模块配置文件（`application.yml` / `application-standalone.yml` / `harness.yml` / `banner.txt` / `schema.sql`）：环境管道，api-key 走环境变量占位符
- 课程：`docs/rebuild-roadmap/`（15 站任务清单，从原工程拷入）

**没预置（实现层，全部自己写）：**

- 所有 `.java` 文件，一个都没有
- `static/` 控制台页面（第 14 站再写）
- `scripts/` 启动脚本（想要就自己写）

## 从 0 写的四步循环

0. **先读架构**：动手前先读 [`docs/technical-architecture.md`](docs/technical-architecture.md)——从原项目提取的架构底稿（是什么/核心决策/底层vs插件边界/插件机制/表设计），每站写完回来修订
1. **看规格**：读 `docs/rebuild-roadmap/` 当前站，去原工程找到每个类的**包路径、类名、公共签名、注释**——不看实现体
2. **自己写**：在对应包下建同名类，先抄签名/record 字段/枚举值，实现自己写
3. **卡住才翻**：卡住超过 ~15 分钟才允许看原实现；看懂后**关掉，重写**
4. **diff 登记**：`diff -u 原版 本仓库版`，差异和原因登记到 `docs/rebuild-mapping.md`

规则细则见 [`AGENTS.md`](AGENTS.md)。

## 常用命令

```bash
# 全量编译
mvn -q compile

# 跑 app（写了 Application 之后）
mvn -pl deepseek-harness-java-app -am spring-boot:run

# 与原工程对照单个类
diff -u \
  ../deepseek-harness-java/deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/llm/model/valobj/StreamChunk.java \
  deepseek-harness-java-domain/src/main/java/cn/xiaofuge/deepseek/harness/domain/llm/model/valobj/StreamChunk.java
```

## 路线图

课程与阶段划分见 [`docs/rebuild-roadmap/README.md`](docs/rebuild-roadmap/README.md)，逐站清单：

| 站 | 主题 | 状态 |
| --- | --- | --- |
| 01 | skeleton-and-boot | ⬜ |
| 02 | domain-model-and-llm | ⬜ |
| 03 | sse-and-session-events | ⬜ |
| 04 | react-loop-and-tools | ⬜ |
| 05 | case-pipeline-and-prompts | ⬜ |
| 06 | approval-hooks-sandbox | ⬜ |
| 07 | persistence-and-replay | ⬜ |
| 08 | task-lifecycle | ⬜ |
| 09 | model-settings-and-routing | ⬜ |
| 10 | workspace-terminal-jobs | ⬜ |
| 11 | goal-plan-todo-workflow | ⬜ |
| 12 | java-plugin-spi | ⬜ |
| 13 | node-mcp-skills-subagents | ⬜ |
| 14 | web-console-and-production | ⬜ |
| 15 | digital-human-collaboration-a2a | ⬜ |
