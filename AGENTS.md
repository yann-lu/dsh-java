# Project Rules — 从 0 重建

本仓库是 `deepseek-harness-java` 的**从 0 实现练习仓库**。与 `deepseek-harness-java-personal`（对照重建）不同，这里的所有实现代码必须自己写。

核心原则：

> **设计照抄原工程，实现从 0 自己写，diff 找差距，偏差必登记。**

---

## 1. 参考项目

原项目（规格书 + 标准答案）固定为：

```text
/Users/luyan/workspace/luyan_workspace/deepseek-harness-java
```

`deepseek-harness-java-personal` 同样是“答案”，规则相同。

查类示例：

```bash
cd /Users/luyan/workspace/luyan_workspace
rg "class AgentUseCase|interface ILlmRuntimePort|record SessionEvent" deepseek-harness-java
```

---

## 2. 设计层：必须与原工程一致

以下内容照抄，不允许改名（同个人版规则）：

- Maven 模块名、包路径
- 类名 / 接口名 / 枚举名 / record 名
- 公共方法名和参数顺序
- DTO 字段名
- 配置 key（如 `harness.llm.deepseek.base-url`）
- REST 路径（如 `/api/agent/stream`）
- 事件名（如 `USER_MESSAGE`、`ASSISTANT_CHUNK`）
- 数据库表名（如 `harness_session_event`）
- 端口/仓储接口命名（如 `ILlmRuntimePort`、`ISessionEventStore`）
- 关键注释语义：定位、为什么这么设计、容易踩的坑

---

## 3. 实现层：从 0 写，这是本仓库存在的意义

1. 只看原类的**签名和注释**，不看实现体
2. 实现完全自己写，允许和原版不一样
3. **卡住超过 ~15 分钟，才允许翻原实现**；看懂后关掉，凭理解重写
4. ❌ **唯一硬性禁令：禁止复制任何实现体（原版或个人版）粘贴进本仓库**
5. 测试可以比原版少，但测试意图要对齐：验证同名类/方法的核心行为
6. Javadoc 用中文写，保留原项目的类/方法语义

---

## 4. 允许按阶段做减法

减法是**删内容，不是换名字**（同个人版规则）：

```text
先写 8 个事件，事件名和原项目一致          ✅
先写 8 个事件，但改名叫 UserSaid / AiSaid  ❌
```

补的时候按原工程的名称、顺序和语义补。

---

## 5. 每一站的工作流

1. 读 `docs/rebuild-roadmap/` 对应阶段
2. 列出这一站要新增/修改的类
3. 到原工程记录每个类的包路径、公共 API（不看实现）
4. 在 `docs/rebuild-mapping.md` 增加映射行
5. 建同名类，抄骨架，写实现
6. 编译 + 测试
7. `diff -u` 对照原工程，把有价值的差异写进 mapping（用了不同数据结构？漏了什么分支？）
8. 更新 mapping 状态

---

## 6. 偏差必须登记

确需改名/换路径/换签名的，登记到 `docs/rebuild-mapping.md` 的 **Deviations** 表（个人版是什么、原版是什么、为什么、计划哪站对齐）。骨架层面的既定偏差已预填在该表里。

不允许“悄悄改名”。
