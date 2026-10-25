# campus-radar 校招雷达

国内校招岗位的数据层：每天从各公司**校招官网**采集岗位，统一成一个岗位库，再通过 **MCP** 开放给任意 Agent（Claude、Grok Bot、Muse 等）使用。

为什么做成「被 Agent 调用的一层」而不是又一个 Agent：通用 Agent 每次现上网搜，校招信息会漏、会重、会过期；它们缺的是一份准确、持续更新的国内校招数据。这个项目只做这一件事，并把它做准。

## 当前覆盖

14 个数据源，约 1.3 万个在招岗位（2026-10-25）。

**飞书招聘通用适配器**（`FeishuAtsCollector`，在 `application.yml` 的 `radar.feishu-sites` 里加一行即可接入新公司）：

| 公司 | 编码 | 站点 |
| --- | --- | --- |
| 字节跳动 | `bytedance` | jobs.bytedance.com（同一套系统，约 7000 个岗位） |
| 小鹏汽车 | `xiaopeng` | xiaopeng.jobs.feishu.cn |
| 蔚来 | `nio` | nio.jobs.feishu.cn |
| 中科创达 | `thundersoft` | thundersoft.jobs.feishu.cn |
| 影石Insta360 | `insta360` | arashivision.jobs.feishu.cn |
| 面壁智能 | `modelbest` | modelbest.jobs.feishu.cn |
| 德赛西威 | `desaysv` | yesv-desaysv.jobs.feishu.cn（不返回招聘类型，按标题判断实习） |

**官网专用采集器**：

| 数据源 | 编码 | 采集方式 | 说明 |
| --- | --- | --- | --- |
| 阿里巴巴 | `alibaba` | XSRF cookie + `listBatch` 动态发现当届批次 | 应届生 + 实习批次 |
| 腾讯 | `tencent` | 列表 + 逐个拉详情（只拉新岗位） | 列表接口不带 JD |
| 美团 | `meituan` | `jobType` 1 校招 / 2 实习 | |
| 快手 | `kuaishou` | `recruitSubProjectCodes` 传空数组返回全部项目 | |
| 百度 | `baidu` | 表单 POST，每页最多 20 | 岗位名去掉城市前缀和编号 |
| 京东 | `jd` | `pageIndex` 从 0 开始 | 城市在 `requirementVoList` 里 |
| 拼多多 | `pdd` | 服务端固定每页 10 条 | 只有应届生页，实习页待接 |

待接入：网易（校招站需要先选项目）、华为（WAF 拦截裸请求，需要浏览器）、Moka（接口响应加密，只读渲染后的页面）、拼多多和京东的实习页。

## 快速开始

需要 Java 21+ 和 Maven。

```bash
mvn -DskipTests package
java -jar target/campus-radar-0.1.0-SNAPSHOT.jar
```

服务默认监听 `8090`，数据存在 `./data/`（H2 文件库）。每天北京时间 7 点自动采集；也可以手动触发（只允许本机调用）：

```bash
# 采集单个数据源（同步返回本次结果）
curl -X POST http://127.0.0.1:8090/api/admin/collect/bytedance
# 后台采集全部数据源
curl -X POST http://127.0.0.1:8090/api/admin/collect
```

## 接入 Agent

MCP 端点：`http://<host>:8090/mcp`，传输方式 Streamable HTTP。

| 工具 | 作用 |
| --- | --- |
| `search_jobs` | 按关键词、公司、城市、校招/实习、届别、最近 N 天检索在招岗位；有关键词按相关度排序，不限公司时各公司穿插，返回 JD 命中片段 |
| `get_job` | 查看单个岗位的完整 JD 和官网投递链接 |
| `list_sources` | 每家公司在招岗位数和最近一次采集时间 |

公司名支持简称和英文名（字节、ByteDance、阿里、PDD 等）；没收录的公司会返回 `isError` 和已收录公司列表。

**Claude Code**

```bash
claude mcp add --transport http campus-radar http://localhost:8090/mcp
```

**云端 Agent（Grok Bot、Muse 等）**：它们跑在自己的云端机器上，访问不到你电脑的 `localhost`。需要先开启令牌，再用隧道暴露一个公网地址：

```bash
RADAR_MCP_TOKEN=换成一串足够长的随机字符 java -jar target/campus-radar-0.1.0-SNAPSHOT.jar
cloudflared tunnel --url http://localhost:8090
```

然后在 Agent 的 connector / MCP 设置里填 `https://<隧道地址>/mcp`，请求头 `Authorization: Bearer <令牌>`。

- 配置令牌后，`/mcp` 和 `/api/**` 都需要带令牌。
- `/api/admin/**`（手动触发采集）只接受直连本机的请求，经隧道或反向代理转发进来的请求一律 403，带令牌也不行。
- Homebrew 装不上 cloudflared 时，可以直接从 [cloudflared Releases](https://github.com/cloudflare/cloudflared/releases) 下载对应系统的二进制。

## REST 接口

- `GET /api/jobs?keyword=&company=&city=&recruitType=校招&gradYear=&sinceDays=&limit=20`
- `GET /api/jobs/{id}`
- `GET /api/sources`

## 设计要点

- **增量采集**：岗位按 `(source, externalId)` 唯一；内容摘要没变只刷新 `lastSeenAt`；本轮没再出现的岗位标记为 `CLOSED`，不删除。
- **健康检查**：本轮条数低于上次成功采集的一半时，标记 `WARN` 并**跳过下线**，避免接口改版或风控导致整批岗位被误下线。
- **只对新岗位拉详情**：腾讯这类列表不带 JD 的来源，已有 JD 的岗位沿用库里内容，每天只多请求新增的那部分。
- **解析与请求分离**：每个采集器的解析方法用录制的真实响应测试（`src/test/resources/fixtures`），接口改版时先更新 fixture 再看哪条断言失败。

## 采集边界

- 只采公司校招官网公开的岗位，低频请求（同一来源请求间隔 ≥ 400ms，每天一轮），保留原文链接，引导用户回官网投递。
- 不破解加密接口，不绕过验证码和风控。
- 不搬运牛客等聚合平台整理的内容；它们只作为「哪家开招了」的线索来源。

## 路线图

- **P0（当前）**：官网采集、统一岗位库、只读 MCP
- **P1**：飞书招聘 / 华为 / Moka 浏览器采集；个人档案与 `match_jobs` 打分；微信每日推送
- **P2**：按 JD 定制简历（起草 + 事实校验）、投递台账、邮件解析自动更新进度
- **P3**：浏览器插件填网申表、海外数据源
