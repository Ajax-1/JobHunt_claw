# JobClaw

JobClaw 是一款面向校招和实习的求职信息管理应用。它把分散在招聘网页、公告、表格和图片中的职位线索整理到同一个地方，帮助你筛选机会、记录投递进展，并通过对话查询适合自己的岗位。

## 可以做什么

- **查找岗位**：按公司、岗位、地点、招聘类型和招聘对象筛选，查看招聘详情与原始投递链接。
- **管理求职进展**：在「我的求职」中收藏岗位，记录 `SAVED`、`APPLIED`、`INTERVIEW`、`OFFER`、`REJECTED`、`WITHDRAWN` 状态，补充备注和跟进日期。每个用户的记录独立保存。
- **整理招聘线索**：管理员导入链接、文本、Excel／CSV 或图片，提取结构化职位草稿，核对和修改后发布到职位库。
- **追踪信息来源**：保留采集来源、执行记录和草稿结果，方便检查与重新采集。
- **使用求职助手**：通过 Web 对话维护求职偏好、查询和推荐职位；也可配置微信、钉钉或飞书入口。
- **连接 AI 客户端**：通过 MCP 接口查询职位，在后台管理模型供应商及查看模型调用记录。

常见使用流程：导入招聘线索 → 审核职位 → 筛选并收藏 → 记录投递与面试进展。

## 本地运行

需要 Java 21；前端源码运行还需要 Node.js 和 pnpm。模型接口、外部聊天渠道和 MySQL 均需使用自己的配置。

### 启动后端

在仓库根目录执行：

```bash
cp .env.example .env
./mvnw -DskipTests install
./mvnw spring-boot:run -pl app
```

默认使用 H2 文件数据库，无需安装 MySQL。运行数据保存在 `workspace/`，该目录和 `.env` 不会提交到 Git。

- 后端地址：<http://localhost:8087>
- 健康检查：<http://localhost:8087/actuator/health>
- 默认 H2 账号为 `sa`，密码为空。

本地环境提供开发登录入口。使用 AI 提取和对话功能前，在管理后台的「LLM供应商」页面填写 API Key、接口地址和模型；查看已有职位及管理求职记录不需要调用模型。

### 启动最新前端

新页面请通过前端开发服务访问：

```bash
cd ui-react
pnpm install
pnpm dev
```

打开 <http://localhost:3000>，页面默认连接 `localhost:8087` 的后端。后端附带一份静态页面，但可能落后于前端源码；本轮新增的「我的求职」页面请在 `3000` 端口使用。

### 使用 MySQL（可选）

在 `.env` 中设置数据库连接：

```dotenv
JOBCLAW_DATABASE_NAME=jobclaw
DATABASE_HOST=localhost
DATABASE_PORT=3306
DATABASE_USERNAME=root
DATABASE_PASSWORD=your-password
```

随后从仓库根目录执行：

```bash
./mvnw -Ptest -DskipTests install
./mvnw -Ptest -pl app spring-boot:run
```

数据库方案由 Maven profile 选择：默认 `dev` 使用 H2 和 Hibernate 建表，`test` 使用 MySQL 和 Liquibase。只修改 `.env` 不会切换数据库类型。

## 代码结构

| 目录 | 职责 |
| --- | --- |
| `app/` | Web API、职位与求职记录、管理后台、数据持久化、MCP 服务 |
| `core/` | 消息路由、会话、Agent 注册、模型解析和对话记忆 |
| `agents/` | 用户信息采集、职位提取和职位推荐 |
| `channels/` | WebSocket、微信、钉钉、飞书消息入口 |
| `providers/` | 模型服务商适配 |
| `plugins/` | 浏览器、职位库检索和计划工具 |
| `ui-react/` | React / Next.js Web 界面 |

后端使用 Java 21、Spring Boot 4、Spring AI、Spring Modulith、LangGraph4J 和 JPA；前端使用 React 19、Next.js 15、TailwindCSS 和 shadcn/ui。模型与聊天入口采用独立模块，业务功能通过共享的会话和工具接口连接。

## 当前边界

- 首次运行不附带完整招聘数据库，需要导入或采集职位信息。
- 职位信息的完整性与时效性取决于原始来源；AI 提取结果应核对后再发布，投递前请确认招聘方原始页面。
- 外部网页可能有登录、验证码或访问限制，无法保证所有招聘网站都能采集。
- 「我的求职」记录需要手动更新，当前不会自动向招聘网站提交简历或同步投递结果。
- 微信、钉钉、飞书及模型服务需单独配置。本地开发登录仅用于本地体验，不应用于公开部署。

## 开发验证

```bash
# 仓库根目录
./mvnw test

# ui-react 目录：仅构建，不更新后端静态页面
pnpm build
```
