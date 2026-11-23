# 从 0 重建版 ↔ 原项目 对照表

> 用途：让本仓库和原项目能一一对应，方便两边 diff、跳转和对照学习。
>
> 规则见根目录 [`AGENTS.md`](../AGENTS.md)。

参考项目：

```text
/Users/luyan/workspace/luyan_workspace/deepseek-harness-java
```

---

## 1. 状态说明

| 状态 | 含义 |
| --- | --- |
| `same` | 类名、包路径、公共方法/字段基本一致，实现为独立从 0 编写 |
| `subset` | 结构一致，但当前阶段只实现了一部分字段/方法/枚举 |
| `simplified` | 名字/路径一致，内部实现更小 |
| `temporary` | 阶段性实现，后续会替换 |
| `missing` | 原项目有，本仓库还没写；括号里写计划阶段 |
| `copied` | 架构/管道文件，按原样拷贝（POM、配置等），不属于实现练习范围 |

---

## 2. 模块与启动

| 本仓库 | 原项目 | 状态 | 备注 |
| --- | --- | --- | --- |
| `pom.xml`（父） | 同名 | copied | 坐标 `-zero` 后缀、版本 0.1.0；其余（Spring Boot 3.3.2 / Java 17 / 插件管理）一致 |
| 7 个模块 POM | 同名 | copied | 依赖方向一致；去掉了 types 的 SCM/发布元数据和 app 的 sample-tools-plugin（第 12 站补） |
| 全部包目录树 | 同名 | copied | 425 个目录 1:1 铺好，空目录为架构地图 |
| `app/.../resources/application.yml` 等配置 | 同名 | copied | 环境管道；api-key 走环境变量占位符 |
| `app/.../resources/schema.sql` | 同名 | copied+ | DDL 整体拷入，**增补了全部列级 COMMENT 备注**（原版只有表级 `--` 注释）；表结构/索引/种子与原版一致 |
| `app/.../resources/application.yml` | 同名 | copied+ | 环境管道；数据源改为本机 MySQL（localhost:3306），删除注释残留的示例密码 |
| `app/.../resources/static/` | 同名 | missing（14） | 控制台页面属实现，第 14 站自己写 |
| `docs/rebuild-roadmap/` | 原项目同名目录 | copied | 15 站课程，从原工程拷入 |
| `scripts/` | 原项目有 scripts/ | missing | 想要启动脚本时自己写 |
| 全部 Java 源码 | — | missing | 本仓库要填的内容，逐站登记到下面各表 |

---

## 3. types 模块

| 本仓库 | 原项目 | 状态 | 备注 |
| --- | --- | --- | --- |
| （待第 01 站起逐个登记） | | | |

## 4. domain 模块

| 本仓库 | 原项目 | 状态 | 备注 |
| --- | --- | --- | --- |
| （待第 02 站起逐个登记） | | | |

## 5. api 模块

| 本仓库 | 原项目 | 状态 | 备注 |
| --- | --- | --- | --- |
| （待第 03 站起逐个登记） | | | |

## 6. case 模块

| 本仓库 | 原项目 | 状态 | 备注 |
| --- | --- | --- | --- |
| （待第 05 站起逐个登记） | | | |

## 7. infrastructure 模块

| 本仓库 | 原项目 | 状态 | 备注 |
| --- | --- | --- | --- |
| （待第 07 站起逐个登记） | | | |

## 8. trigger 模块

| 本仓库 | 原项目 | 状态 | 备注 |
| --- | --- | --- | --- |
| （待第 03 站起逐个登记） | | | |

## 9. app 模块

| 本仓库 | 原项目 | 状态 | 备注 |
| --- | --- | --- | --- |
| （待第 01 站起逐个登记） | | | |

---

## 10. Deviations（既定偏差）

| 本仓库 | 原项目 | 为什么 | 计划 |
| --- | --- | --- | --- |
| 父 POM 坐标 `deepseek-harness-java-zero` | `deepseek-harness-java` | 避免与原工程/个人版在 IDE、本地仓库混淆 | 永久保留 |
| 版本 `0.1.0` | `0.1.7` | 独立版本线，从零开始计 | 永久保留 |
| 无 `plugins/sample-tools-plugin`、`plugins/deepseek-harness-plugin-archetype` 模块 | 有 | 第 12 站（Java 插件 SPI）内容，提前建空模块无意义 | 第 12 站补 |
| types POM 无 SCM/licenses/developers/发布插件注释 | 有 | 原作者的中央仓库发布管道，与架构无关 | 永久省略 |
| app POM 无 sample-tools-plugin 依赖及 JAR 拷贝执行 | 有 | 同 plugins 模块，第 12 站内容 | 第 12 站补 |
| `static/` 目录为空 | 有控制台页面 | 控制台是第 14 站实现内容 | 第 14 站自己写 |

---

## 11. 从 0 写的差异笔记

> 每站 diff 之后，把“我的写法 vs 原版写法”值得记住的差异记在这里（数据结构选择、并发处理、边界条件等）。

| 站 | 类 | 我的写法 | 原版写法 | 学到什么 |
| --- | --- | --- | --- | --- |
| | | | | |
