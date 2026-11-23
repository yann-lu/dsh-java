# 第 01 站 · 骨架与启动

> 阶段：P0 可运行内核 · 体量：S（0.5–1 天） · 前置：无

## 这一站完成什么

搭出 7 个核心 Maven 模块的骨架，让 Spring Boot 能启动、H2 能建表、健康检查能返回 UP。  
这一站不写业务，只把“以后每个能力该放哪”固定下来。

## 前置检查

- JDK 17+
- Maven 3.9+
- 端口 `8090` 空闲

## 任务清单

1. 建父 `pom.xml`：`packaging=pom`，声明 7 个核心模块：
   ```text
   deepseek-harness-java-app
   deepseek-harness-java-trigger
   deepseek-harness-java-api
   deepseek-harness-java-case
   deepseek-harness-java-domain
   deepseek-harness-java-infrastructure
   deepseek-harness-java-types
   ```
2. 固定依赖方向（先只写 pom，不写代码）：
   ```text
   app → trigger + infrastructure
   trigger → api + case + infrastructure
   case → api + domain
   domain → types
   infrastructure → domain
   types → 无内部依赖
   ```
3. 建 `types` 模块：先只放一个 `package-info.java`，确认它不依赖 Spring。
4. 建 `app` 模块：`Application` 使用
   `@SpringBootApplication(scanBasePackages = "cn.xiaofuge.deepseek.harness")`，
   并加 `@MapperScan("cn.xiaofuge.deepseek.harness.infrastructure.dao")`。
5. 写 `application.yml`：端口 8090、数据源占位、MyBatis 下划线转驼峰、actuator 只暴露 health/info。
6. 写 `application-standalone.yml`：H2 文件库，`MODE=MySQL`，`spring.sql.init.mode=always`。
7. 写最小 `schema.sql`：只建一张 `harness_session` 表，验证 SQL 初始化能跑。
8. 加一个 `StartupInfoRunner implements ApplicationRunner`：启动时打印端口、profile、数据源。
9. 加一个静态占位页 `static/index.html`，内容先写 “dsh-java rebuilding”。

## 关键接口 / 类

| 类 / 文件 | 职责 |
| --- | --- |
| `Application` | 唯一启动类，扫全工程包 |
| `StartupInfoRunner` | 启动期打印环境信息，不写业务 |
| `application.yml` | 通用配置 |
| `application-standalone.yml` | H2 独立运行配置 |
| `schema.sql` | 最小建表脚本 |

## 最小实现 vs 正式实现

- **最小实现**：一个 `/actuator/health` + 一个静态页 + 一张表。
- **正式实现**：`app` 模块还有 `PresetPluginLoader`、`ModelSyncRunner`、`SchemaMigrationRunner` 三个 Runner；完整 `schema.sql` 有 19 张表。这些留到后续阶段。

## 验收

```bash
mvn -q -DskipTests package

java -jar deepseek-harness-java-app/target/deepseek-harness-java-app-*.jar \
  --spring.profiles.active=standalone

curl -s http://localhost:8090/actuator/health
# {"status":"UP"}

curl -s http://localhost:8090/ | head
# <!doctype html> ... dsh-java rebuilding
```

再检查一次依赖方向：

```bash
mvn -q -pl deepseek-harness-java-types dependency:tree | grep deepseek-harness-java
# 应该没有其他内部模块
```

## 对照源码

- `pom.xml`（根）
- `deepseek-harness-java-app/src/main/java/cn/xiaofuge/deepseek/harness/app/Application.java`
- `deepseek-harness-java-app/src/main/resources/application.yml`
- `deepseek-harness-java-app/src/main/resources/application-standalone.yml`
- `deepseek-harness-java-app/src/main/resources/schema.sql`

## 常见坑

- **不要在 app 里写业务**：app 只负责启动、配置、静态资源、启动 Runner。
- **H2 要加 `MODE=MySQL`**：否则后续 MySQL 方言 SQL 跑不通。
- **`@MapperScan` 暂时没有 Mapper 也可以加**：MyBatis 启动不会因为空包失败，但要注意包名正确。
- **`mvn package` 默认跳过测试**：根 pom 里 `skipTests=true`，想跑测试要加 `-DskipTests=false`。

## 完成标志

- [ ] `mvn -DskipTests package` 成功
- [ ] `standalone` profile 能启动
- [ ] `/actuator/health` 返回 `UP`
- [ ] `/` 返回静态占位页
- [ ] `types` 的依赖树里没有其他内部模块
- [ ] 打 tag：`stage-01`
