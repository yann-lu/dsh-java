# 第 14 站 · Web 控制台与生产化

> 阶段：P3 扩展与交付 · 体量：L（2–3 天） · 前置：08、11

## 这一站完成什么

把后端能力变成“用户能看见、能操作”的产品：

- 浏览器对话、工具卡片、取消运行
- 会话历史、模型设置、插件管理、审批处理
- API Key 认证
- standalone / MySQL / Docker 三种启动方式
- 健康检查与基础可观测性

## 前置检查

- 08 站任务/审批可用
- 11 站 Goal/Todo/Workflow 可用
- 了解原生 JS、SSE、Markdown 渲染

## 任务清单

1. 静态控制台：
   - `static/index.html`、`app.js`、`app.css`
   - 本地化 `marked`、`DOMPurify`、`highlight.js`
   - 工作区侧栏、会话列表、对话区、工具卡片、右栏设置
2. SSE 客户端：
   - 解析 `meta/chunk/reasoning/step_break/tool_result/finish/done/error`
   - 60ms/帧合并渲染
   - 停止按钮 → `/api/agent/{agentId}/cancel`
   - 断流/空闲看门狗与 REST 对账
3. 管理界面：
   - 模型设置：渠道模板、同步模型、激活/删除
   - 插件：安装、激活、运行、停用、配置
   - 审批：待审批列表、allow-once/allow-session/deny
   - 历史会话回放
4. 认证：
   - `ApiKeyAuthInterceptor`：`X-API-Key` 或 `Authorization: Bearer`
   - `WebMvcConfig` 注册拦截器
   - 静态资源与 `/actuator` 放行
   - `harness.auth.api-keys` 为空时本地放行
5. 部署：
   - 默认 MySQL profile
   - `standalone` H2 profile
   - `Dockerfile` + `docker-compose`
   - `scripts/package-local.sh --zip`
6. 可观测性：
   - `/actuator/health`、`/actuator/info`
   - `GET /api/harness/config/effective`（Key/Password/Secret/Token 脱敏）
   - 启动日志打印端口、profile、模型、插件
7. 安全与性能边界：
   - XSS：Markdown 必须过 DOMPurify
   - API Key 不回显
   - SSE 心跳与超时
   - 高并发下 `AgentCollectNode.whenIdle().join()` 的阻塞风险

## 关键接口 / 类

| 类 / 文件 | 职责 |
| --- | --- |
| `static/app.js` | 控制台主逻辑 |
| `AgentStreamApi` | SSE 服务端 |
| `ApiKeyAuthInterceptor` | API Key 认证 |
| `WebMvcConfig` | 拦截器注册 |
| `Dockerfile` / Compose | 部署 |
| `package-local.sh` | 本地分发包 |

## 最小实现 vs 正式实现

- **最小实现**：一个聊天页 + 一个设置页 + API Key 拦截。
- **正式实现**：工作区/会话/工具卡片/审批/插件/模型全量管理、SSE 对账、离线渲染库、Docker/zip 分发。

## 验收

### 1. 浏览器

启动后访问 `http://localhost:8090/`：

- 新建会话 → 发消息 → 看到流式文本
- 触发工具 → 看到工具卡片
- 点停止 → Agent 取消
- 切换模型 → 下一条消息使用新模型
- 安装插件 → 工具出现
- 待审批任务 → 通过/拒绝

### 2. 认证

```bash
# 配置 harness.auth.api-keys 后
curl -s http://localhost:8090/api/agent/workspaces
# 401

curl -s -H 'X-API-Key: your-key' http://localhost:8090/api/agent/workspaces
# 200
```

### 3. Docker

```bash
export LLM_API_KEY='your-key'
docker compose up --build -d
curl -s http://localhost:8090/actuator/health
```

### 4. 本地分发包

```bash
scripts/package-local.sh --zip
cd dist/deepseek-harness-java-local
./start.sh
```

## 对照源码

- `deepseek-harness-java-app/src/main/resources/static/`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/filter/`
- `deepseek-harness-java-trigger/src/main/java/cn/xiaofuge/deepseek/harness/trigger/service/stream/`
- `Dockerfile`、`docker-compose.yml`
- `scripts/package-local.sh`
- `docs/dev-ops/`

## 常见坑

- **SSE 空闲断流**：15s 心跳必须有；前端也要有看门狗。
- **Markdown XSS**：`marked` 输出必须过 `DOMPurify`。
- **API Key 泄漏**：日志、错误响应、配置查询都要脱敏。
- **H2 与 MySQL 差异**：`schema.sql` 尽量标准 SQL；H2 用 `MODE=MySQL`。
- **单实例边界**：控制台功能完整不等于可水平扩展；多实例要先解决 Agent 内存状态与任务锁。

## 完成标志

- [ ] 浏览器能完成对话/工具/取消
- [ ] 模型、插件、审批界面可用
- [ ] API Key 认证生效
- [ ] Docker Compose 能启动
- [ ] `package-local.sh --zip` 能产出可运行包
- [ ] 打 tag：`stage-14`
