# CareerPilot · 前端 ↔ 后端接口文档

> **本文档是「读懂这个项目」的入口。**
> 覆盖前端（Vue 3）实际调用的全部接口、后端内部接口、AI Agent 接口，
> 以及它们串起来之后的完整链路。
>
> 与既有文档的分工：
> | 文档 | 定位 | 状态 |
> |---|---|---|
> | **`docs/API-FRONTEND.md`（本文）** | 前后端接口全景 + 链路 + 页面映射 | ✅ 与实现逐行核对 |
> | `docs/API-CONTRACT.md` | 内部对齐基线，三方共同遵守 | ✅ 与实现一致（较简略） |
> | `api-spec.md`（仓库根） | **早期设计稿，描述的是没建成的系统** | ⚠️ 已过时，见 [§16](#16-文档与实现偏差清单) |
>
> 核对时间：2026-10-21 ｜ 实现版本：`f92a11a`

---

## 目录

- [0. 三十秒速览](#0-三十秒速览)
- [1. 全局约定](#1-全局约定)
- [2. 请求链路总览](#2-请求链路总览)
- [3. 接口清单速查表](#3-接口清单速查表)
- [4. 认证模块（user-center :8081）](#4-认证模块user-center-8081)
- [5. 用户画像模块（user-center :8081）](#5-用户画像模块user-center-8081)
- [6. 简历模块（resume-service :8082）](#6-简历模块resume-service-8082)
- [7. JD 模块（jd-service :8083）](#7-jd-模块jd-service-8083)
- [8. 匹配模块（match-service :8084）](#8-匹配模块match-service-8084)
- [9. AI 任务模块（ai-task-scheduler :8085）](#9-ai-任务模块ai-task-scheduler-8085)
- [10. AI 能力页面（Optimize / Interview / CoverLetter / CareerPlan）](#10-ai-能力页面optimize--interview--coverletter--careerplan)
- [11. 内部接口（不经过网关）](#11-内部接口不经过网关)
- [12. Python Agent 接口（后端 → AI 层）](#12-python-agent-接口后端--ai-层)
- [13. SSE 协议详解](#13-sse-协议详解)
- [14. 页面 → 接口对照表](#14-页面--接口对照表)
- [15. 四条主链路时序](#15-四条主链路时序)
- [16. 文档与实现偏差清单](#16-文档与实现偏差清单)
- [17. 建议修复项](#17-建议修复项)

---

## 0. 三十秒速览

前端只跟 **一个地址**打交道：`/api/v1/**` → 网关（8080）→ 按路径前缀转发给 5 个后端服务。

```
浏览器
  │  Authorization: Bearer <accessToken>
  ▼
Vite dev :3000  ──/api 代理──▶  Spring Cloud Gateway :8080
                                  │  校验 JWT → 注入 X-User-Id → 剥掉客户端伪造的 X-User-Id
                                  ├── /api/v1/auth/**, /api/v1/user/**  → user-center    :8081
                                  ├── /api/v1/resume/**               → resume-service :8082
                                  ├── /api/v1/jd/**                   → jd-service     :8083
                                  ├── /api/v1/match/**                → match-service  :8084
                                  └── /api/v1/ai-tasks/**             → ai-task-scheduler :8085
                                                                        │
                                                          线程池异步 ──▶ agent-orchestrator :8001
                                                                        │  LangGraph 路由
                                                                        ▼
                                                                   8 个 Python Agent :8001-8008
                                                                        │
                                                          HTTP 回调 ──▶ /internal/*-callback
                                                                        │
                                                                        ▼
                                                                  业务表写回
```

**三种接口，别搞混：**

| 类别 | 谁调用 | 路径特征 | 鉴权 |
|---|---|---|---|
| **对外 API** | 浏览器前端 | `/api/v1/**` | 网关校验 JWT |
| **内部接口** | Java 服务之间 | `/internal/**` | **不过网关**，直连端口 |
| **Agent 接口** | Java → Python | `:8001-8008` | 无鉴权（仅内网） |

---

## 1. 全局约定

### 1.1 基础路径与代理

前端 axios 实例：`web/src/api/request.ts:26`

```ts
const instance = axios.create({ baseURL: '/api/v1', timeout: 30000 })
```

- 开发态：Vite dev server `:3000`，`/api` 代理到 `http://localhost:8080`（`web/vite.config.ts:15`）
- 生产态：Nginx `:8088`，静态产物 + `/api` 反代到网关（`deploy/nginx/conf.d/careerpilot.conf`）

### 1.2 统一响应包络

**所有 Java 服务返回同一形状**（`ApiResponse<T>`）：

```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "traceId": null
}
```

| 字段 | 说明 |
|---|---|
| `code` | `0` = 成功；非 0 = 业务错误 |
| `message` | 错误描述，成功时固定 `"success"` |
| `data` | 业务数据；`void` 接口为 `null` |
| `traceId` | 链路 ID（目前恒为 `null`，见 [§17](#17-建议修复项)） |

> **前端拦截器行为**（`request.ts:91`）：响应拦截器直接返回**整个包络**，所以业务代码里取 `res.data` 拿到的是包络的 `data` 字段。
> `code !== 0` 时拦截器 `Promise.reject` 一个带 `code` 的 Error，并默认弹 `message.error`；传 `{ silent: true }` 可关闭弹窗。

### 1.3 认证与身份传递

```
前端  ──Authorization: Bearer <accessToken>──▶  网关
                                                │  JwtAuthFilter (order = -100)
                                                │  ① 验签 → 取 subject 作为 userId
                                                │  ② 剥掉客户端可能自带的 X-User-Id
                                                │  ③ 写入真正的 X-User-Id
                                                ▼
                                          下游服务只认 X-User-Id，不解析 Authorization
```

**免鉴权路径**（`JwtAuthFilter.java:41`）：

| 路径 | 原因 |
|---|---|
| `POST /api/v1/auth/login` | 登录 |
| `POST /api/v1/auth/register` | 注册 |
| `POST /api/v1/auth/refresh` | 刷新令牌（accessToken 可能已过期） |
| `GET /actuator/health` | 网关自身健康检查 |

**两条关键安全设计：**

1. **白名单路径也会剥掉 `X-User-Id`**（`JwtAuthFilter.java:67`）——否则未登录接口会带着伪造身份进入下游。
2. **`mutate().header()` 是追加不是替换**（`JwtAuthFilter.java:95`）——必须先 `headers.remove()` 再写，否则外部请求自带 `X-User-Id` 就能越权。

**前端 Token 刷新**（`request.ts:107`）：收到 HTTP 401 → 用 refreshToken 换新 accessToken → 重放原请求（`_retried` 标记防死循环）。**并发请求共享同一个刷新 Promise**（`request.ts:64`），不会同时打多次 refresh。

> ⚠️ 刷新失败会 `clearAuth()` + 跳登录页，并把原路径放进 `?redirect=`。

### 1.4 错误码

| code | HTTP | 含义 | 触发场景 |
|---|---|---|---|
| `0` | 200 | 成功 | — |
| `1403` | 403 | 禁止访问 | 网关拦截 `/internal/**`；资源不属于当前用户 |
| `1404` | 200 | 资源不存在 | 简历/JD/任务不存在或无权访问；**资源尚未就绪**（ATS 评分、JD 解析未完成） |
| `401` | 401 | 未登录 / Token 失效 | 网关鉴权失败 |
| `500` | 500 | 服务端异常 | `GlobalExceptionHandler` 兜底 |

> **1404 的双重语义**是本项目一个特色：`GET /resume/{id}/ats-score` 和 `GET /jd/{id}/parse/result` 在**结果未就绪**时也返回 1404，前端据此**继续轮询**而不是报"不存在"。

### 1.5 分页

请求参数：`page`（默认 1）、`size`（默认 20）

```json
{
  "code": 0, "message": "success",
  "data": { "total": 128, "page": 1, "size": 20, "items": [ ] }
}
```

> `GET /api/v1/ai-tasks/list` 是例外，用 `limit` 而非分页。

### 1.6 双 ID 约定（最容易搞错的地方）

同一份简历/JD 有**两个 ID**：

| ID | 类型 | 格式 | 用途 |
|---|---|---|---|
| **数据库主键** | `Long` | 数字 | 前端 URL 路径参数、列表项标识、跨服务接口参数 |
| **业务 ID** | `String` | `res_xxx` / `jd_xxx` | 传给 AI Agent 的 `resume_id` / `jd_id` |

**规则：**

- 前端 URL 里用**主键**：`GET /api/v1/resume/{resumeId}`、`POST /api/v1/match/evaluate { resumeId, jdId }`
- 前端把主键换成业务 ID 再传给 AI：`Optimize.vue:173` → `selected.jdBizId`
- 匹配记录里存的是**业务 ID**（`MatchService.java:56`：`record.setResumeId(resume.getResumeBizId())`）

### 1.7 文件上传

`POST /api/v1/resume/upload` 使用 `multipart/form-data`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | 简历文件（PDF / Word） |
| `title` | text | 否 | 简历标题，缺省用文件名 |

Nginx 侧限 `client_max_body_size 20M`；后端 `multipart` 配置见 `resume-service/application.yml`。

---

## 2. 请求链路总览

### 2.1 同步接口（前端直接调）

```
前端 ──▶ 网关 ──▶ 业务服务 ──▶ MySQL
                    └─▶ (可选) Redis
```

### 2.2 异步 AI 接口（业务服务 → 调度器 → Agent → 回调）

```
前端 POST /api/v1/ai-tasks
   │
   ▼
ai-task-scheduler :8085
   │  ① 任务落库 t_ai_task (status=PENDING)
   │  ② 提交线程池，立即返回 taskId 给前端   ← 前端拿到 200 就返回了
   │
   │  ③ (后台线程) POST /api/orchestrate
   ▼
agent-orchestrator :8001
   │  LangGraph：route → 业务节点 → finalize
   │  业务节点按 task_type 调对应 Agent :8002-8008
   ▼
Agent 返回结果
   │
   │  ④ 结果落 t_ai_task.output_payload (status=SUCCESS)
   │  ⑤ POST 回调 → 业务服务 /internal/*-callback
   ▼
业务服务写回业务表
   │
   ▼
前端 GET /api/v1/ai-tasks/{taskId}  或  SSE /stream  取结果
```

**关键点：**

- **前端不直接调 Agent**，只调 `POST /api/v1/ai-tasks`，用 `taskType` 区分能力
- **业务服务也不直接调 Agent**，统一经调度器，调度器是唯一知道 Agent 地址的地方
- `resume_optimize` / `interview` / `cover_letter` / `career_plan` **没有回调配置**，结果留在 `t_ai_task.output_payload`，前端按 `taskId` 自己取

---

## 3. 接口清单速查表

### 3.1 对外 API（前端 → 网关 → 业务服务）

| # | 方法 | 路径 | 服务 | 鉴权 | 前端封装 | 页面 |
|---|---|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/register` | user-center | 免 | `authApi.register` | Register |
| 2 | POST | `/api/v1/auth/login` | user-center | 免 | `authApi.login` | Login |
| 3 | POST | `/api/v1/auth/logout` | user-center | 可选 | `authApi.logout` | 全局 |
| 4 | POST | `/api/v1/auth/refresh` | user-center | 免 | `authApi.refresh` | 拦截器自动 |
| 5 | GET | `/api/v1/user/profile` | user-center | ✅ | `userApi.getProfile` | Profile / Dashboard |
| 6 | PUT | `/api/v1/user/profile` | user-center | ✅ | `userApi.updateProfile` | Profile |
| 7 | GET | `/api/v1/user/membership` | user-center | ✅ | `userApi.getMembership` | Profile |
| 8 | POST | `/api/v1/resume/upload` | resume-service | ✅ | `resumeApi.upload` | Resume |
| 9 | GET | `/api/v1/resume/list` | resume-service | ✅ | `resumeApi.list` | Resume / Match / Interview / Dashboard |
| 10 | GET | `/api/v1/resume/{id}` | resume-service | ✅ | `resumeApi.getById` | Resume |
| 11 | DELETE | `/api/v1/resume/{id}` | resume-service | ✅ | `resumeApi.delete` | Resume |
| 12 | GET | `/api/v1/resume/{id}/ats-score` | resume-service | ✅ | `resumeApi.getAtsScore` | Resume |
| 13 | GET | `/api/v1/resume/{id}/versions` | resume-service | ✅ | `resumeApi.getVersions` | Resume |
| 14 | POST | `/api/v1/resume/{id}/reparse` | resume-service | ✅ | `resumeApi.reparse` | Resume |
| 15 | GET | `/api/v1/resume/{id}/sections` | resume-service | ✅ | — | ⚠️ 后端有，前端未用 |
| 16 | POST | `/api/v1/resume/{id}/primary` | resume-service | ✅ | — | ⚠️ 后端有，前端未用 |
| 17 | POST | `/api/v1/jd/upload` | jd-service | ✅ | `jdApi.upload` | JdManage |
| 18 | GET | `/api/v1/jd/list` | jd-service | ✅ | `jdApi.list` | JdManage / Optimize / CoverLetter / Interview / Dashboard |
| 19 | GET | `/api/v1/jd/{id}` | jd-service | ✅ | `jdApi.getById` | JdManage / Optimize / CoverLetter / Interview |
| 20 | DELETE | `/api/v1/jd/{id}` | jd-service | ✅ | `jdApi.delete` | JdManage |
| 21 | GET | `/api/v1/jd/{id}/parse/result` | jd-service | ✅ | `jdApi.getParseResult` | JdManage |
| 22 | POST | `/api/v1/jd/{id}/reparse` | jd-service | ✅ | `jdApi.reparse` | JdManage |
| 23 | POST | `/api/v1/match/evaluate` | match-service | ✅ | `matchApi.evaluate` | Match |
| 24 | GET | `/api/v1/match/{recordId}` | match-service | ✅ | `matchApi.get` | Match |
| 25 | GET | `/api/v1/match/list` | match-service | ✅ | `matchApi.list` | Match / Dashboard |
| 26 | POST | `/api/v1/ai-tasks` | ai-task-scheduler | ✅ | `aiTaskApi.create` | Optimize / Interview / CoverLetter / CareerPlan |
| 27 | GET | `/api/v1/ai-tasks/{taskId}` | ai-task-scheduler | ✅ | `aiTaskApi.get` | 轮询 / SSE 收尾 |
| 28 | GET | `/api/v1/ai-tasks/{taskId}/stream` | ai-task-scheduler | ✅ | `createSseStream` | Optimize（`useStream: true`） |
| 29 | GET | `/api/v1/ai-tasks/list` | ai-task-scheduler | ✅ | — | ⚠️ 后端有，前端未用 |

**共 29 个对外端点，前端实际使用 26 个。**

### 3.2 内部接口（服务间直连，不过网关）

| 方法 | 路径 | 所属服务 | 调用方 |
|---|---|---|---|
| GET | `/internal/resumes/{id}/text?userId=` | resume-service | match-service |
| POST | `/internal/resume-analyze-callback` | resume-service | ai-task-scheduler |
| GET | `/internal/jds/{id}/text?userId=` | jd-service | match-service |
| POST | `/internal/jd-parse-callback` | jd-service | ai-task-scheduler |
| POST | `/internal/match-callback` | match-service | ai-task-scheduler |

### 3.3 Agent 接口（后端 → Python）

| 方法 | 路径 | Agent | 端口 |
|---|---|---|---|
| POST | `/api/orchestrate` | agent-orchestrator | 8001 |
| POST | `/api/match` | agent-orchestrator（快捷入口） | 8001 |
| GET | `/api/stream/{task_type}` | agent-orchestrator | 8001 |
| POST | `/api/parse` | jd-parser-agent | 8002 |
| POST | `/api/analyze` | resume-analyzer-agent | 8003 |
| POST | `/api/match` | match-agent | 8004 |
| POST | `/api/optimize` | resume-optimizer-agent | 8005 |
| POST | `/api/question` | interview-agent | 8006 |
| POST | `/api/evaluate` | interview-agent | 8006 |
| POST | `/api/generate` | cover-letter-agent | 8007 |
| POST | `/api/plan` | career-advisor-agent | 8008 |

> 8 个 Agent 每个都有 `GET /health`。

---

## 4. 认证模块（user-center :8081）

### 4.1 `POST /api/v1/auth/register`

**干什么**：注册账号，**注册即登录**，直接返回完整登录态。

**请求**（`RegisterRequest`，含 Jakarta Validation）

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `email` | string | 是 | 邮箱格式 |
| `password` | string | 是 | 长度下限 |
| `nickname` | string | 否 | 昵称 |
| `inviteCode` | string \| null | 否 | 邀请码（预留，**当前无业务逻辑**） |

**响应 `data`（`LoginResponse`）**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": {
    "userId": 1,
    "userIdBiz": "usr_xxx",
    "email": "a@b.com",
    "nickname": "Clover",
    "avatar": null,
    "membershipLevel": "FREE"
  }
}
```

### 4.2 `POST /api/v1/auth/login`

**干什么**：邮箱 + 密码登录。

**请求**：`{ email, password }` → **响应同 4.1**（同样的 `LoginResponse`）

### 4.3 `POST /api/v1/auth/logout`

**干什么**：登出。**把 refreshToken 写进 Redis 黑名单**。

**请求**：`{ refreshToken }`（body 可为空）

> ⚠️ **必须带 refreshToken**，否则服务端没法拉黑它。`AuthController.java:36` 的注释专门强调了这点。
> `X-User-Id` 是可选的（`required = false`）——token 可能已部分失效时也要能调通。

**为什么只拉黑 refreshToken？**（`README.md:263`）
accessToken 是无状态 JWT，网关每请求查一次 Redis 就把网关变成瓶颈。refreshToken 才是真正的长期凭证，把它的 jti 写进 Redis（TTL = 剩余寿命）就够了。

**响应**：`data: null`

### 4.4 `POST /api/v1/auth/refresh`

**干什么**：用 refreshToken 换新的 accessToken（+ 新的 refreshToken）。

**请求**：`{ refreshToken }` → **响应同 4.1**

> 前端拦截器在收到 401 时自动调用此接口，且**并发请求共享同一个刷新 Promise**。

---

## 5. 用户画像模块（user-center :8081）

### 5.1 `GET /api/v1/user/profile`

**干什么**：读当前用户的账号信息 + 求职画像。

**响应 `data`（`UserProfileDTO`）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `userId` | number | 数据库主键 |
| `userIdBiz` | string | 业务 ID |
| `email` | string | 邮箱 |
| `phone` | string \| null | **已脱敏**：`138****8888` |
| `nickname` | string | 昵称 |
| `avatar` | string \| null | 头像 URL |
| `gender` | string \| null | 性别 |
| `birthYear` | number \| null | 出生年 |
| `city` | string \| null | 当前城市 |
| `currentTitle` | string \| null | 当前职位 |
| `yearsOfExperience` | number \| null | 工作年限 |
| `educationLevel` | string \| null | 最高学历 |
| `school` / `major` / `graduationYear` | | 学校 / 专业 / 毕业年份 |
| `targetIndustry` | string \| null | 目标行业 |
| `targetPosition` | string \| null | 目标岗位 |
| `targetSeniority` | string \| null | 目标职级 |
| `targetCity` | string[] | 目标城市数组（库里是 JSON 字符串，出参已解析） |
| `targetSalaryMin` / `targetSalaryMax` | number \| null | 目标薪资区间 |
| `skills` | string[] | 技能数组 |
| `createdAt` / `updatedAt` | string | 时间戳 |

> **容错设计**（`UserController.java:202`）：库里 JSON 字段解析失败时返回空列表并记 warn，**不让一个脏字段导致整个接口 500**。

### 5.2 `PUT /api/v1/user/profile`

**干什么**：更新画像。**逐字段判空后覆盖**，所以前端可以只提交变化的字段。

**请求**：`UserProfileDTO` 的任意子集（`nickname` 单独走 `t_user` 表，其余走 `t_user_profile` 表）

**响应**：同 5.1（内部直接 `return getProfile(userId)`）

> **细节**：薪资下限允许显式置 0，所以判断用的是 `!= null` 而不是"非空"（`UserController.java:119`）。
> `targetCity` / `skills` 存库前序列化为 JSON 字符串。

### 5.3 `GET /api/v1/user/membership`

**干什么**：会员等级与 AI 配额。

**响应 `data`**

```json
{
  "level": "FREE",              // FREE | PRO | ENTERPRISE
  "levelName": "免费版",          // 免费版 | 专业版 | 企业版
  "expireAt": null,
  "autoRenew": false,
  "aiQuotaRemaining": 0,
  "aiQuotaTotal": 0
}
```

> ⚠️ **配额目前不扣减**：`aiQuotaRemaining` 字段存在，但调度侧还没在任务开始时扣（`README.md:248`）。

---

## 6. 简历模块（resume-service :8082）

### 6.1 `POST /api/v1/resume/upload`

**干什么**：上传简历 → 落盘 → 抽取正文 → 切分 section → 建版本 → **登记 AI 分析任务** → 立即返回。

**请求**：`multipart/form-data`，`file`（必填）+ `title`（选填）

**响应 `data`（`ResumeCreatedVO`）**

```json
{
  "resumeId": 12,              // 数据库主键
  "resumeBizId": "res_xxx",    // 业务 ID
  "versionId": "ver_xxx",
  "title": "后端工程师简历",
  "parseStatus": "PENDING",
  "taskId": "task_xxxx",       // ← 拿去轮询/SSE
  "sectionCount": 5,
  "wordCount": 1830,
  "createdAt": "2026-10-21T12:00:00"
}
```

> **重要**：`parseStatus` 此刻是 `PENDING`，**ATS 评分是异步的**。前端拿 `taskId` 去跟踪，完成后再调 6.4 取评分。

### 6.2 `GET /api/v1/resume/list`

**干什么**：分页列出当前用户的简历。

**Query**：`page`（默认 1）、`size`（默认 20）、`keyword`（按标题模糊）

**响应 `data`**：`{ total, page, size, items: ResumeListItem[] }`

**`ResumeListItem`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `resumeId` | number | 主键 |
| `resumeBizId` | string | 业务 ID |
| `title` | string | 标题 |
| `atsScore` | number \| null | ATS 评分，未完成时为 `null` |
| `parseStatus` | string | `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED` |
| `isPrimary` | boolean | 是否主简历 |
| `updatedAt` | string | |

### 6.3 `GET /api/v1/resume/{id}`

**干什么**：简历详情。

**Path**：`id` = **数据库主键**（数字）

**响应 `data`（`ResumeDetailVO`）**：`resumeId`、`resumeBizId`、`title`、`atsScore`、`parseStatus`、`textContent`（正文全文）、`createdAt`、`updatedAt`

### 6.4 `GET /api/v1/resume/{id}/ats-score`

**干什么**：取 ATS 评分报告。**直接返回 Agent 的 `AnalysisResult` 原样结构**（前端按这个渲染）。

**响应**：`data` = `AnalysisResult` 原始结构（字段由 `resume-analyzer-agent` 的 Pydantic 模型决定）

**未就绪时**：`code = 1404`，`message = "简历尚未完成分析"` → 前端继续轮询。

### 6.5 `GET /api/v1/resume/{id}/versions`

**干什么**：简历历史版本列表。

**响应 `data`**：`ResumeVersionVO[]`，含 `versionId`、`resumeId`、`versionNo`、`isCurrent`、`createdAt` 等

### 6.6 `POST /api/v1/resume/{id}/reparse`

**干什么**：重新触发 AI 分析。

**响应 `data`**：`{ taskId, status, message }`

### 6.7 ⚠️ `GET /api/v1/resume/{id}/sections`

**后端已实现，前端未调用。**

**干什么**：返回简历的 section 切分结果（经历 / 教育 / 技能等段落）。

**响应 `data`**：`ResumeSectionVO[]`，含 `sectionType`、`content`、`orderNo` 等

> 目前简历正文切分结果只用于 ATS 分析，不在前端展示。

### 6.8 ⚠️ `POST /api/v1/resume/{id}/primary`

**后端已实现，前端未调用。**

**干什么**：把该简历设为主简历（`is_primary = 1`）。

**响应**：`data: null`

---

## 7. JD 模块（jd-service :8083）

### 7.1 `POST /api/v1/jd/upload`

**干什么**：新建 JD → 落库 → **登记解析任务** → 立即返回（不等 Agent）。

**请求**（`JdUploadRequest`）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `title` | string | 是 | 岗位名称 |
| `company` | string | 否 | 公司名 |
| `rawText` | string | 是 | JD 原文 |
| `sourceUrl` | string | 否 | 来源链接 |

**响应 `data`（`JdCreatedVO`）**

```json
{
  "jdId": 8,
  "jdBizId": "jd_xxx",
  "title": "高级后端工程师",
  "company": "某某科技",
  "parseStatus": "PENDING",
  "taskId": "task_xxxx",
  "createdAt": "2026-10-21T12:00:00"
}
```

### 7.2 `GET /api/v1/jd/list`

**Query**：`page`、`size`、`keyword`（标题/公司模糊）、`company`（**精确**过滤公司）

**响应 `data`**：`{ total, page, size, items: JdListItem[] }`

**`JdListItem`**

| 字段 | 类型 | 说明 |
|---|---|---|
| `jdId` | number | 主键 |
| `jdBizId` | string | 业务 ID |
| `title` / `company` | string | |
| `parseStatus` | string | 解析状态 |
| `sourceUrl` | string \| null | |
| `rawTextPreview` | string | **原文预览**（截断）—— 前端在没有详情时的兜底数据源 |
| `createdAt` | string | |

### 7.3 `GET /api/v1/jd/{id}`

**Path**：`id` = 数据库主键

**响应 `data`（`JdDetailVO`）**：`jdId`、`jdBizId`、`title`、`company`、`parseStatus`、`rawText`（**完整原文**）、`sourceUrl`、`createdAt`

> 前端在 Optimize / CoverLetter / Interview 三个页面都会调它拿 `rawText`。

### 7.4 `GET /api/v1/jd/{id}/parse/result`

**干什么**：取 JD 解析结果。**直接返回 Agent 的 `JdChunk` 原样结构**。

**响应 `data`** = `JdChunk` 原始结构：

```json
{
  "job_title": "高级后端工程师",
  "company_name": "某某科技",
  "department": "", "location": "北京", "employment_type": "full_time",
  "salary_min": 30000, "salary_max": 50000, "salary_currency": "CNY",
  "experience_min_years": 5, "experience_max_years": null,
  "education_required": "本科",
  "skills": [
    { "name": "Java", "category": "technical", "importance": "required", "years_required": 5 }
  ],
  "responsibilities": [ "..." ],
  "requirements": [ "..." ],
  "benefits": [ "..." ],
  "raw_text": "...",
  "parse_score": 0.86,
  "parse_method": "regex"
}
```

**未就绪时**：`code = 1404`，`message = "JD 尚未解析完成"`

> **设计取舍**（`README.md:275`）：JD 解析结果存原始 JSON，前端直接按 `JdChunk` 渲染。Agent 改字段时前端不用跟着改两处。

### 7.5 `POST /api/v1/jd/{id}/reparse`

**干什么**：重新触发 JD 解析。**响应**：`{ taskId, status, message }`

### 7.6 `DELETE /api/v1/jd/{id}`

**干什么**：删除 JD。**响应**：`data: null`

---

## 8. 匹配模块（match-service :8084）

### 8.1 `POST /api/v1/match/evaluate`

**干什么**：发起匹配评估。**同步做**「取素材 + 归属校验 + 建记录」，**异步跑**打分。

**请求**（`EvaluateRequest`）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `resumeId` | number | 是 | **数据库主键** |
| `jdId` | number | 是 | **数据库主键** |

**响应 `data`（`MatchCreatedVO`）**

```json
{
  "recordId": "mch_xxx",
  "status": "PROCESSING",
  "taskId": "task_xxxx",
  "createdAt": "2026-10-21T12:00:00"
}
```

**同步阶段实际做了什么**（`MatchService.java:45`）：

1. `GET resume-service /internal/resumes/{id}/text?userId=` → **归属校验在这里发生**
2. `GET jd-service /internal/jds/{id}/text?userId=`
3. 落一条 `t_match_record`（status=PENDING）
4. `POST ai-task-scheduler /api/v1/ai-tasks { taskType: "match_score", bizId: recordId }`
5. 记录状态改 PROCESSING，返回

> **失败语义**（`MatchController.java:30`）：这个接口返回成功 = 任务已经在跑了。这里失败是**真失败**（简历/JD 不存在、无权访问、依赖服务挂了）。

### 8.2 `GET /api/v1/match/{recordId}`

**干什么**：取匹配结果。

**Path**：`recordId` = 匹配记录业务 ID（`mch_xxx`），**不是数据库主键**

**响应 `data`**

```json
{
  "recordId": "mch_xxx",
  "status": "SUCCESS",
  "taskId": "task_xxxx",
  "resumeId": "res_xxx",
  "jdId": "jd_xxx",
  "semanticScore": 16.1,
  "keywordScore": 87.5,
  "businessScore": 100.0,
  "finalScore": 62.1,
  "matchedSkills": [ "Java", "Spring Boot", "MySQL" ],
  "gapList": [
    {
      "gapType": "missing_skill",
      "gapContent": "Kubernetes",
      "severity": 4,
      "suggestion": "在项目经历中加入该技术的实际使用案例（Kubernetes）..."
    }
  ],
  "errorMessage": null,
  "createdAt": "...",
  "completedAt": "..."
}
```

> **未完成时四个分数字段都是 `null`，不是 0**（`MatchService.java:127`）。前端据此显示"评分中"而不是渲染 0 分。这个细节很重要——0 分和有分数是两回事。
>
> `finalScore` 口径固定：`0.40 × 语义 + 0.35 × 关键词 + 0.25 × 业务`（`matcher.py:6`，契约锁死不可改）。

### 8.3 `GET /api/v1/match/list`

**干什么**：匹配历史（分页，**不展开 gapList**，省流量）。

**Query**：`page`、`size`

**响应 `data`**：`{ total, page, size, items: [{ recordId, resumeId, jdId, status, finalScore, createdAt }] }`

---

## 9. AI 任务模块（ai-task-scheduler :8085）

这是**所有 AI 能力的统一入口**。前端不管做简历优化、模拟面试、求职信还是职业规划，都是同一个模式：

```
POST /api/v1/ai-tasks  { taskType, bizId, input }
   → 拿 taskId
   → 轮询 GET /ai-tasks/{taskId}  或  SSE GET /ai-tasks/{taskId}/stream
```

### 9.1 `POST /api/v1/ai-tasks`

**干什么**：创建 AI 任务。**立即返回**，Agent 在后台线程池跑。

**请求**（`CreateTaskRequest`）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `taskType` | string | 是 | 见下表，`@NotBlank` |
| `bizId` | string | 否 | 业务对象 ID，取决于 taskType |
| `input` | object | 是 | **结构化对象**（不是字符串），`@NotNull` |

> `input` 设计成对象而不是 JSON 字符串，是为了不强迫调用方手工拼 JSON（`CreateTaskRequest.java:12`）。

**7 种 taskType**

| taskType | Agent | 回调 | 业务表 |
|---|---|---|---|
| `jd_parse` | jd-parser :8002 | ✅ `jd-service` | `t_jd_parse_result` / `t_jd_skill` |
| `resume_analyze` | resume-analyzer :8003 | ✅ `resume-service` | `t_resume` 的 ATS 分 |
| `match_score` | match-agent :8004 | ✅ `match-service` | `t_match_record` / `t_match_gap` |
| `resume_optimize` | resume-optimizer :8005 | ❌ | 仅 `t_ai_task` |
| `interview` | interview-agent :8006 | ❌ | 仅 `t_ai_task` |
| `cover_letter` | cover-letter-agent :8007 | ❌ | 仅 `t_ai_task` |
| `career_plan` | career-advisor :8008 | ❌ | 仅 `t_ai_task` |

**响应 `data`（`TaskVO`）**

```json
{
  "taskId": "task_xxxx",
  "userId": "usr_xxx",
  "taskType": "resume_optimize",
  "bizId": "jd_xxx",
  "status": "PENDING",
  "progress": 0,
  "currentAgent": "resume-optimizer-agent",
  "input": { },
  "output": null,
  "errorMessage": null,
  "retryCount": 0,
  "durationMs": null,
  "startedAt": null,
  "completedAt": null,
  "createdAt": "...",
  "updatedAt": "..."
}
```

> `input` / `output` 在库里是 JSON 字符串，**出参已还原成对象**（`TaskVO.java:53`）。必须用 Jackson 解析——Hutool 的 `toBean` 会把 null 留成 `JSONNull`，序列化时直接抛异常（`TaskVO.java:65`）。

### 9.2 `GET /api/v1/ai-tasks/{taskId}`

**干什么**：查任务快照。

**归属校验**：任务不属于当前用户时返回 `code = 1404`（`AiTaskController.java:41`）

**响应**：同 9.1 的 `TaskVO`（`output` 字段在任务完成后才有值）

**状态机**

```
PENDING ──▶ RUNNING ──┬──▶ SUCCESS   (progress=100)
                     └──▶ FAILED    (errorMessage 有值)
```

终态判定：`SUCCESS` / `FAILED` / `CANCELLED`（`useTaskPolling.ts:14`）

### 9.3 `GET /api/v1/ai-tasks/{taskId}/stream` （SSE）

**干什么**：实时推送任务进度。

**响应类型**：`text/event-stream`

**事件格式**

| event | data | 时机 |
|---|---|---|
| `snapshot` | `{ taskId, status, progress, currentAgent }` | 任务未终止时，每 500ms 推一次状态变化 |
| `done` | `{ taskId, status, progress:100, output:{...} }` | 任务 SUCCESS |
| `error` | `{ taskId, status, errorMessage }` | 任务 FAILED |

> **实现要点**（`AiTaskService.java:340`）：用「非终止快照流 + 最终终止事件」两段拼接，而不是直接 `takeUntil`。后者在任务已终结或轮询超时时**一条终止事件都不发就关连接**，前端只能靠超时兜底。

### 9.4 ⚠️ `GET /api/v1/ai-tasks/list`

**后端已实现，前端未调用。**

**干什么**：列出当前用户的任务（供 Dashboard / 任务中心）。

**Query**：`limit`（默认 20，上限 200）

**响应 `data`**：`TaskVO[]`（按创建时间倒序）

---

## 10. AI 能力页面（Optimize / Interview / CoverLetter / CareerPlan）

四个页面**共用同一个前端流程**（`web/src/composables/useTaskPolling.ts`）：

```
submit({ taskType, bizId, input })
   → POST /ai-tasks 拿 taskId
   → 跟踪（SSE 或轮询）
   → 拿到 output
```

**默认策略**（`useTaskPolling.ts:24`）：轮询间隔 **1500ms**，最长等待 **60000ms**。
只有 **Optimize 页面**开了 `useStream: true`（SSE 实时进度），其余三个走轮询。
**SSE 失败自动降级为轮询**（`useTaskPolling.ts:126`）。

### 10.1 简历优化（`/optimize`）

**页面**：`Optimize.vue` ｜ **taskType**：`resume_optimize`

**前端提交的 input**（`Optimize.vue:182`）

```json
{
  "bullet": "负责用户模块的开发",
  "tone": "professional",
  "jd_id": "jd_xxx",
  "jd_text": "（JD 原文）"
}
```

| 字段 | Agent 声明？ | 实际效果 |
|---|---|---|
| `bullet` | ✅ `OptimizeRequest.bullet` | 生效 |
| `tone` | ✅ `OptimizeRequest.tone` | 生效（`professional` / `passionate` / `humble`） |
| `jd_id` | ❌ | **被 Pydantic 静默丢弃** |
| `jd_text` | ❌ | **被 Pydantic 静默丢弃** |
| `jd_skills` | ✅ 但**前端没传** | 恒为 `[]` |

> 🔴 **功能级问题**：见 [§17.1](#171-高优先级前端传的-jd-信息在-agent-侧被静默丢弃)。

**输出 `data`（`ResumeOptimizeResult`）**

```json
{
  "original": "负责用户模块的开发",
  "rewritten": "主导用户模块开发，重构核心链路并推动性能优化落地",
  "injected_skills": [],
  "star_breakdown": { "situation": "...", "task": "...", "action": "...", "result": "..." },
  "change_note": "..."
}
```

### 10.2 模拟面试（`/interview`）

**页面**：`Interview.vue` ｜ **taskType**：`interview` ｜ **两阶段，同一个 taskType**

#### 阶段一：出题（`phase = "question"`）

**input**（`Interview.vue:256`）

```json
{
  "resume_id": "res_xxx",
  "jd_id": "jd_xxx",
  "job_title": "高级后端工程师",
  "resume_highlights": ["后端工程师简历"],
  "jd_text": "（JD 原文）",
  "resume_text": "（简历标题）",
  "phase": "question",
  "round_no": 1,
  "total_rounds": 3,
  "previous_answer": "（上一轮回答，可选）"
}
```

> ⚠️ `total_rounds` **必须显式传**（`Interview.vue:263` 的注释）：Agent 侧默认只有 3，不传的话用户选 5 轮时第 4 轮起会被 `round_no > total_rounds` 判失败。
> ⚠️ `resume_highlights` 前端传的是**简历标题**，不是真正的亮点（`Interview.vue:228`）。

**输出 `data`（`InterviewQuestion`）**

```json
{
  "round_no": 1,
  "question": "请介绍你在过往项目中主导过的核心技术方案，以及你如何推动它落地？",
  "question_type": "behavioral",
  "focus": "..."
}
```

#### 阶段二：评价（`phase = "evaluate"`）

**input**

```json
{
  "...同上...": "",
  "phase": "evaluate",
  "round_no": 1,
  "question": "（上一轮的问题）",
  "answer": "（用户回答）"
}
```

**输出 `data`（`InterviewEvaluation`）**

```json
{
  "score": 7.5,
  "feedback": "...",
  "star_coverage": { "situation": true, "task": true, "action": true, "result": false },
  "followup": "（针对 STAR 最弱环节的追问）"
}
```

> **输出结构不一致的处理**（`Interview.vue:300`）：同一个 taskType 下，`question` 阶段输出 `InterviewQuestion`，`evaluate` 阶段输出 `InterviewEvaluation`。前端用 `useTaskPolling<InterviewQuestion>` 声明泛型，取评价结果时做一次局部断言。

### 10.3 求职信（`/cover-letter`）

**页面**：`CoverLetter.vue` ｜ **taskType**：`cover_letter`

**input**（`CoverLetter.vue:147`）

```json
{
  "job_title": "高级后端工程师",
  "company_name": "某某科技",
  "tone": "professional",
  "jd_id": "jd_xxx",
  "jd_text": "（JD 原文）"
}
```

| 字段 | Agent 声明？ | 实际效果 |
|---|---|---|
| `job_title` | ✅ | 生效 |
| `company_name` | ✅ | 生效（默认 `"贵司"`） |
| `tone` | ✅ | 生效 |
| `jd_id` / `jd_text` | ❌ | **丢弃** |
| `jd_skills` | ✅ 但没传 | 恒为 `[]` → 技能段用 fallback `"核心工程能力"` |
| `resume_highlights` | ✅ 但没传 | 恒为 `[]` → 证据段用 fallback `"过往项目经历"` |

> 🔴 **同样属于 [§17.1](#171-高优先级前端传的-jd-信息在-agent-侧被静默丢弃)**

**输出 `data`（`CoverLetter`）**：`content`（正文）、`tone`、`word_count`、`sections` 等

### 10.4 职业规划（`/career-plan`）

**页面**：`CareerPlan.vue` ｜ **taskType**：`career_plan`

**input**（`CareerPlan.vue:164`）

```json
{
  "current_title": "后端工程师",
  "years_of_experience": 5,
  "target_industry": "人工智能",
  "target_position": "算法工程师"
}
```

**输出 `data`（`CareerPlan`）**

```json
{
  "target_position": "...",
  "readiness_score": 62,
  "roadmap": [
    { "stage": "...", "duration": "...", "actions": [ ], "doc_ids": [ "kb_xxx" ] }
  ],
  "skill_gaps": [ ],
  "doc_ids": [ ]
}
```

> **这个 Agent 刻意不调 LLM**（`README.md:196`）：让模型改写 roadmap 会导致引用与 `doc_ids` 脱节，破坏「引用可溯源」这个承诺。`career-advisor-agent/app/llm.py` 存在但 `main.py` 从不调用它。
> 前端点击引用来源时会 `scrollIntoView` 定位到对应知识库条目。

---

## 11. 内部接口（不经过网关）

> **安全边界**：`/internal/**` 在网关层被直接 403（`JwtAuthFilter.java:58`），Nginx 也 `deny all`（第 48 行）。**只能服务间直连端口调用。**

### 11.1 `GET /internal/resumes/{id}/text?userId=`

**调用方**：match-service ｜ **所属**：resume-service

**干什么**：取简历正文，**并在数据所有者这里做归属校验**。

**Query**：`userId`（必填）

**响应 `data`**

```json
{
  "resumeBizId": "res_xxx",
  "title": "后端工程师简历",
  "text": "（简历正文全文）",
  "atsScore": 78
}
```

> **为什么不直连表**（`README.md:267`）：归属校验必须发生在数据所有者手里。match-service 猜到别人的 `resumeId` 去直连表就能读走简历；走 `/internal/**` 让 resume-service 自己校验 `userId`，越权在源头就被挡住。

### 11.2 `GET /internal/jds/{id}/text?userId=`

**调用方**：match-service ｜ **所属**：jd-service

**响应 `data`**

```json
{
  "jdBizId": "jd_xxx",
  "title": "高级后端工程师",
  "company": "某某科技",
  "text": "（JD 原文）",
  "parseStatus": "SUCCESS",
  "hardSkills": [ "Java", "Spring Boot", "MySQL" ],
  "seniority": "senior",
  "industry": "互联网",
  "educationRequired": "本科",
  "experienceMinYears": 5
}
```

### 11.3 `POST /internal/resume-analyze-callback`

**调用方**：ai-task-scheduler ｜ **所属**：resume-service

**请求**（`InternalCallbackRequest`）

```json
{
  "taskId": "task_xxxx",
  "bizId": "res_xxx",
  "success": true,
  "output": { },
  "error": null,
  "durationMs": 1234
}
```

**干什么**：把 Agent 的 ATS 分析结果写回 `t_resume`（分数 + 解析状态）。

### 11.4 `POST /internal/jd-parse-callback`

**调用方**：ai-task-scheduler ｜ **所属**：jd-service

**请求**：同 11.3 的结构（`bizId` = `jd_xxx`）

**干什么**：把 `JdChunk` 写回 `t_jd_parse_result`（原始 JSON）+ `t_jd_skill`（技能明细）。

### 11.5 `POST /internal/match-callback`

**调用方**：ai-task-scheduler ｜ **所属**：match-service

**请求**：同 11.3 的结构（`bizId` = `mch_xxx`）

**干什么**：把 `MatchResult` 写回：
- `t_match_record` 的分数列（`semantic_score` / `keyword_score` / `business_score` / `final_score`）+ `result_json`
- `t_match_gap` 明细（**先删后插**，保证幂等）

---

## 12. Python Agent 接口（后端 → AI 层）

> 这层**没有鉴权**，只靠内网隔离。前端**不应**直接访问。

### 12.1 编排层 `agent-orchestrator :8001`

#### `POST /api/orchestrate`

**请求**

```json
{
  "task_type": "match_score",
  "input_data": { },
  "user_id": "usr_xxx"
}
```

**响应**

```json
{
  "task_id": "task_xxxx",
  "status": "completed",
  "progress": 100,
  "result": { },
  "error": null
}
```

**status 取值**：`completed` / `failed`（调度器据此判断成败，`AiTaskService.java:206`）

**路由映射**（`graph.py:216`）

| task_type | 节点 | 目标 Agent |
|---|---|---|
| `jd_parse` | `jd_parser` | jd-parser :8002 `/api/parse` |
| `resume_analyze` | `resume_analyzer` | resume-analyzer :8003 `/api/analyze` |
| `match_score` | `match_agent` | match-agent :8004 `/api/match` |
| `resume_optimize` | `resume_optimizer` | resume-optimizer :8005 `/api/optimize` |
| `interview` | `interview_agent` | interview-agent :8006 `/api/question` 或 `/api/evaluate` |
| `cover_letter` | `cover_letter_agent` | cover-letter-agent :8007 `/api/generate` |
| `career_plan` | `career_advisor` | career-advisor :8008 `/api/plan` |

> **进度语义**（`graph.py:28`）：进度**只增不减**（`max()`），避免 SSE 出现回退。节点推进到 10 / 30 / 90，finalize 收到 100。

#### `POST /api/match` / `GET /api/stream/{task_type}`

编排层的**快捷入口**，绕过 LangGraph 直接调 match-agent / 直接推流。业务服务**目前不用**。

### 12.2 各 Agent 的请求模型

| Agent | 端点 | 必填字段 | 可选字段 |
|---|---|---|---|
| jd-parser :8002 | `POST /api/parse` | `jd_text` | `jd_id` |
| resume-analyzer :8003 | `POST /api/analyze` | `resume_text` | `resume_id` |
| match-agent :8004 | `POST /api/match` | `jd_text`, `resume_text` | `jd_skills` |
| resume-optimizer :8005 | `POST /api/optimize` | `bullet` | `jd_skills`, `tone` |
| interview-agent :8006 | `POST /api/question` | — | `round_no`, `total_rounds`, `jd_skills`, `resume_highlights`, `previous_answer`, `job_title` |
| interview-agent :8006 | `POST /api/evaluate` | `question`, `answer` | `jd_skills` |
| cover-letter-agent :8007 | `POST /api/generate` | — | `jd_skills`, `resume_highlights`, `company_name`, `job_title`, `tone`, `company_info` |
| career-advisor :8008 | `POST /api/plan` | — | `current_title`, `target_industry`, `target_position`, `years_of_experience`, `skills`, `question` |

> 🔴 **Pydantic v2 默认 `extra='ignore'`**：上表**没有**声明的字段，Agent 会**静默丢弃**，不报错、不警告。全项目 43 个 Python 文件里**没有一处 `model_config` 覆盖**。这是 [§17.1](#171-高优先级前端传的-jd-信息在-agent-侧被静默丢弃) 的根因。

---

## 13. SSE 协议详解

### 13.1 为什么不用原生 EventSource

`EventSource` **无法携带 `Authorization` 头**。前端改用 `fetch` + `ReadableStream` **手工解析 SSE 帧**（`web/src/api/sse.ts`），后端零改动。

### 13.2 前端解析规则（`sse.ts:28`）

| 规则 | 处理 |
|---|---|
| 帧分隔符 | `\r\n\r\n` / `\n\n` / `\r\r` |
| 注释行（`:` 开头） | 忽略 |
| `event:` 字段 | 事件名，缺省 `message` |
| `data:` 多行 | 用 `\n` 拼接 |
| 无 `data:` 的块 | 丢弃 |
| JSON 解析失败 | 静默忽略该帧 |

### 13.3 端到端配置（不配就会被掐断）

| 位置 | 配置 | 原因 |
|---|---|---|
| 网关 | `httpclient.response-timeout: 300s` | SSE 长连接，响应超时必须关/放大 |
| 网关 | `connect-timeout: 5000` | 连接建立超时 |
| Nginx | `proxy_buffering off` | **不开缓冲，进度会被攒成一坨最后一次性吐出** |
| Nginx | `proxy_set_header Connection ''` | 断掉上游 Connection |
| Nginx | `gzip off` | 避免压缩缓冲延迟 |
| Nginx | `chunked_transfer_encoding on` | 分块传输 |
| Nginx | `proxy_read_timeout 600s` | 读超时（对齐服务端 `MAX_STREAM_MS` = 15 分钟） |
| 调度器 | `MAX_STREAM_MS = 15 * 60 * 1000` | 单条连接最长 15 分钟，防止任务卡死时连接永不释放 |
| 调度器 | `POLL_INTERVAL_MS = 500` | SSE 内部轮询任务表的间隔 |
| 调度器 | 独立 `sseScheduler` 线程池 | 避免慢任务把事件推送饿死 |

### 13.4 前端使用方式

```ts
// web/src/composables/useTaskPolling.ts
const polling = useTaskPolling<ResumeOptimizeResult>({ useStream: true })

// 内部会：
// 1. createSseStream(`/ai-tasks/${id}/stream`, { onEvent, onError, onClose })
// 2. 收到 done 事件 → 再 fetchFinal(id) 拿完整 output
// 3. 流中断（onError/onClose）→ 自动降级为轮询
```

---

## 14. 页面 → 接口对照表

| 页面 | 路由 | 调用的接口 |
|---|---|---|
| **Login** | `/login` | `POST /auth/login` |
| **Register** | `/register` | `POST /auth/register` |
| **Dashboard** | `/dashboard` | `GET /resume/list?page=1&size=1`<br>`GET /jd/list?page=1&size=1`<br>`GET /match/list?page=1&size=1`（三个 `Promise.all` 并发） |
| **Resume** | `/resume` | `POST /resume/upload`、`GET /resume/list`、`GET /resume/{id}`、`DELETE /resume/{id}`、`GET /resume/{id}/ats-score`、`GET /resume/{id}/versions`、`POST /resume/{id}/reparse` |
| **JdManage** | `/jd` | `POST /jd/upload`、`GET /jd/list`、`GET /jd/{id}`、`GET /jd/{id}/parse/result`、`POST /jd/{id}/reparse`、`DELETE /jd/{id}` |
| **Match** | `/match` | `GET /resume/list`、`GET /jd/list`、`POST /match/evaluate`、`GET /match/{recordId}`、`GET /match/list` |
| **Optimize** | `/optimize` | `GET /jd/list`、`GET /jd/{id}`、`POST /ai-tasks`（taskType=`resume_optimize`，SSE） |
| **Interview** | `/interview` | `GET /resume/list`、`GET /jd/list`、`GET /jd/{id}`、`POST /ai-tasks`（taskType=`interview`，两阶段） |
| **CoverLetter** | `/cover-letter` | `GET /jd/list`、`GET /jd/{id}`、`POST /ai-tasks`（taskType=`cover_letter`） |
| **CareerPlan** | `/career-plan` | `POST /ai-tasks`（taskType=`career_plan`） |
| **Profile** | `/profile` | `GET /user/profile`、`PUT /user/profile`、`GET /user/membership` |
| **MainLayout** | — | 登出 `POST /auth/logout` |

**路由守卫**（`router/index.ts:96`）：未登录访问非 `public` 页面 → 重定向 `/login?redirect=<原路径>`；已登录访问 `/login` 或 `/register` → 重定向 `/dashboard`。

---

## 15. 四条主链路时序

### 15.1 简历上传 → ATS 评分

```
前端              网关         resume-svc      scheduler     orchestrator   analyzer-agent
 │                 │              │               │              │              │
 ├─ POST /resume/upload ────────▶│               │              │              │
 │  (multipart)                  │ 落盘 + 抽正文  │              │              │
 │                 │              ├─ 建 t_resume + t_resume_version                │
 │                 │              ├─ POST /ai-tasks (resume_analyze) ─▶│        │
 │◀── 200 { resumeId, taskId, parseStatus:PENDING } ──────────────┤        │
 │                 │              │               ├─▶ LangGraph 路由             │
 │                 │              │               │              ├─ POST /api/analyze ─▶│
 │                 │              │               │              │◀── AnalysisResult ─┤
 │                 │              │               │◀── result ───┤              │
 │                 │              │               ├─ 落 t_ai_task.output_payload   │
 │                 │              │◀── POST /internal/resume-analyze-callback      │
 │                 │              ├─ 写回 ATS 分数 + parseStatus=SUCCESS            │
 │                 │              │               │              │              │
 ├─ GET /resume/{id}/ats-score ─▶│               │              │              │
 │◀── 200 { AnalysisResult 原结构 }              │              │              │
 │   (未就绪时 1404 → 继续轮询)                  │              │              │
```

### 15.2 JD 解析

同 15.1，差异在：回调是 `/internal/jd-parse-callback`，写回 `t_jd_parse_result`（原始 JSON）+ `t_jd_skill`（技能明细）。

### 15.3 简历 × JD 匹配

```
前端          网关        match-svc     resume-svc/jd-svc    scheduler    match-agent
 │             │            │                │                │             │
 ├─ POST /match/evaluate ───▶│                │                │             │
 │             │            ├─ GET /internal/resumes/{id}/text?userId= ─▶  │  ← 归属校验
 │             │            ├─ GET /internal/jds/{id}/text?userId= ────▶  │  ← 归属校验
 │             │            ├─ INSERT t_match_record (PENDING)         │
 │             │            ├─ POST /ai-tasks (match_score) ───▶│       │
 │◀── 200 { recordId, status:PROCESSING, taskId }              │       │
 │             │            │                ├─▶ LangGraph 路由 │       │
 │             │            │                │         ├─ POST /api/match ─▶│
 │             │            │                │         │◀── MatchResult ────┤
 │             │            │◀── POST /internal/match-callback ────────┤
 │             │            ├─ 写 t_match_record 分数列 + result_json
 │             │            ├─ 写 t_match_gap（先删后插）
 │◀─ GET /match/{recordId} ─▶│
 │  { finalScore: 62.1, semanticScore, keywordScore, businessScore, gapList[] }
```

### 15.4 简历优化（唯一走 SSE 的页面）

```
前端          网关        scheduler      orchestrator    optimizer-agent
 │             │            │                │                │
 ├─ POST /ai-tasks { taskType: "resume_optimize", input: {...} } ─▶│
 │◀── 200 { taskId, status: PENDING }       │                │
 │             │            ├─▶ LangGraph 路由                  │
 │             │            │         ├─ POST /api/optimize ─▶│
 │             │            │         │◀── ResumeOptimizeResult ┤
 │             │            │◀── result ┤                │
 │             │            │  (无回调配置，结果留在 t_ai_task.output_payload)
 │             │            │                │
 ├─ GET /ai-tasks/{taskId}/stream ─▶│      │                │   ← SSE
 │◀── event: snapshot { status, progress, currentAgent }  (每 500ms) │
 │◀── event: done { output: {...} }                         │
 │             │            │                │
 ├─ GET /ai-tasks/{taskId}  ← 收尾再拉一次完整快照              │
```

---

## 16. 文档与实现偏差清单

### 16.1 🔴 `api-spec.md` 描述的是没建成的系统（1467 行，**建议废弃或重写**）

| `api-spec.md` 写的 | 实际 |
|---|---|
| 端口 `user-service :8001` | 实际是 `user-center :8081` |
| 端口 `ai-orchestrator :9001` | 实际是 `agent-orchestrator :8001` |
| 端口 `ai-interview :9002` | 实际是 `interview-agent :8006` |
| `file-service :8004` / `task-service :8005` / `order-service :8006` | **这些服务不存在** |
| `POST /auth/sms-code`、`/auth/sms-code/send`（短信验证码登录） | **不存在** |
| `POST /user/avatar`（上传头像） | **不存在** |
| `PUT /auth/password`（修改密码） | **不存在** |
| `GET /jd/search`（全文 + 向量混合检索） | **不存在**（无向量库） |
| `POST /interview/session/{id}/answer`（HTTP 形式提交） | **不存在**（走 `/ai-tasks`） |
| `GET /interview/session/{id}/next-question`（SSE 流式） | **不存在** |
| `WS /interview/ws`（WebSocket 实时对话） | **不存在**（用 SSE） |
| `PUT /cover-letter/{id}`（编辑求职信） | **不存在** |
| `GET /cover-letter/{id}/export`（导出） | **不存在** |
| `GET /order/plans`、`POST /order`、`POST /payment/callback` | **不存在**（表建了但无业务） |
| `GET /admin/overview`、`GET /admin/users` | **不存在** |
| `GET /file/{id}`、通用文件服务 | **不存在**（简历文件在 resume-service 内） |

> **结论**：这份文档**不要给面试官看，也不要照着它学**。它会让人以为项目比实际大得多——一旦被追问细节就会崩。
> 建议：**移到 `docs/archive/` 并在顶部标注"设计稿，未实现"**，或直接删除。

### 16.2 网关里的 4 条死路由

`gateway-service/application.yml:38` 把这四个前缀路由到了 8085：

```
/api/v1/optimize/**, /api/v1/interview/**, /api/v1/cover-letter/**, /api/v1/career-plan/**
```

**但 ai-task-scheduler 只有一个 Controller（`/api/v1/ai-tasks`），前端也从不调这四个前缀**（只在 `router/index.ts` 里当页面路径用）。

→ 这四条路由**必然 404**，属于预留接口。**要么删掉，要么补上对应 Controller。**

### 16.3 后端有、前端未用的接口（3 个）

| 接口 | 状态 |
|---|---|
| `GET /api/v1/resume/{id}/sections` | section 切分结果，前端未展示 |
| `POST /api/v1/resume/{id}/primary` | 设主简历，前端无入口 |
| `GET /api/v1/ai-tasks/list` | 任务列表，前端未用（Dashboard 用的是 `matchApi.list`） |

### 16.4 其他实现与文档不符的点

| 项 | 说明 |
|---|---|
| `jd-parser/parser.py:2` docstring | 写着 "Three-phase parsing: regex → classify → **LLM**"，但**代码里没有 LLM 阶段**（`llm.py` 存在但 `main.py` 从不调用） |
| `jd-parser` / `resume-analyzer` 的 `requirements.txt` | 列了 `langchain` + `langchain-openai`，但**源码零 import**——全项目只有 `graph.py` import 了 `langgraph` |
| `career-advisor` 的 `llm.py` | 存在但从不调用（**这是有意的**，见 §10.4） |
| `api-spec.md` 的会员等级 | 写 `FREE/PRO/ULTRA`，实际后端是 `FREE/PRO/ENTERPRISE`（`UserController.java:170`） |
| `types/index.ts:30` | 前端类型声明 `MembershipLevel = 'FREE' \| 'PRO' \| 'ULTRA'`，**与后端实际值不符**（后端没有 ULTRA） |

---

## 17. 建议修复项

### 17.1 高优先级：前端传的 JD 信息在 Agent 侧被静默丢弃

**现象**：前端传 `jd_id` / `jd_text` / `resume_id` / `resume_text`，但 Agent 的 Pydantic 模型**没有声明这些字段**，`extra='ignore'` 导致**静默丢弃**。

**受影响的三个功能：**

| 功能 | 后果 |
|---|---|
| **简历优化** | Agent 拿不到 JD。`OptimizeRequest` 只有 `jd_skills`，前端没传 → 恒为 `[]`。而优化器的 docstring 明说输入是"简历要点 + **目标 JD 技能**"，`injected_skills`（JD 技能注入）**永远为空**。**"针对这个 JD 优化"的核心价值没实现。** |
| **求职信** | `jd_skills` / `resume_highlights` 都是 `[]` → 技能段落用 fallback `"核心工程能力"`，证据段落用 fallback `"过往项目经历"`。**生成的求职信是通用模板，不含任何真实 JD 技能或简历亮点。** |
| **模拟面试** | `jd_skills` = `[]` → `_pick_subject` 走 fallback `"核心技术方案"`，出题与具体 JD 技能无关；`evaluate_answer` 里 `skill_hits` 恒空，**JD 技能命中检查完全失效**。 |

**为什么 `verify_contract.py` 没拦住**：它的 `ALLOWED_EXTRA_KEYS` 把 `jd_id` / `jd_text` / `jd_skills` 都列为"允许的额外键"——脚本只校验"前端传的键是否是 Agent 接受的字段"，**但语义丢失（传了但 Agent 不消费）它查不出来**。

> 实测：当前 `python scripts/verify_contract.py` **退出码 0，4 项检查全绿通过**。绿灯并不代表契约正确——这正是这条规则需要补强的地方。

**修法（推荐 A）**：

**A. 前端补传 `jd_skills`**（最小改动，最对症）
前端已经有 JD 的解析结果（`GET /jd/{id}/parse/result` 返回 `JdChunk.skills`），把它映射成 `jd_skills` 数组传给 Agent：

```ts
// Optimize.vue
const parsed = await jdApi.getParseResult(form.jdId)
input: {
  bullet: original,
  tone: form.tone,
  jd_skills: parsed.data?.skills?.map(s => s.name) ?? [],   // ← 补上
}
```

**B. Agent 侧加 `model_config = ConfigDict(extra='allow')` 或显式声明 `jd_text`**
让 Agent 真的消费 JD 原文。但要改 8 个 Agent 的模型，工作量大。

**C. 增强 `verify_contract.py`**
增加一条检查：**前端传的每个 key，必须在对应 Agent 的 Pydantic 模型里有声明**（而不只是在 `ALLOWED_EXTRA_KEYS` 里）。这能从根上防回归。

> **建议 A + C 一起做**：A 立刻修复功能，C 防止将来再犯。

### 17.2 🟡 中 — 清理 `api-spec.md`

移到 `docs/archive/api-spec-DESIGN.md`，顶部加显著标注：

```markdown
> ⚠️ **本文档是早期设计稿，描述的系统未实现。**
> 实际实现请看 [API-FRONTEND.md](./API-FRONTEND.md)。
> 已知不存在的模块：短信登录 / 头像 / 向量检索 / WebSocket / 求职信编辑导出 / 订单支付 / 管理后台。
```

### 17.3 🟡 中 — 网关死路由

删除 `application.yml:38` 中未被使用的四个前缀，或补上对应 Controller。

### 17.4 🟢 低 — 修文档与实现的小出入

- `jd-parser/parser.py:2` docstring 删掉 "→ LLM"
- `jd-parser` / `resume-analyzer` 的 `requirements.txt` 删掉 `langchain` / `langchain-openai`（源码零引用）
- `types/index.ts:30` 的 `MembershipLevel` 改成 `'FREE' | 'PRO' | 'ENTERPRISE'`
- `ApiResponse.traceId` 目前恒为 `null`——要么真的生成（`t_ai_task` 已有 `trace_id` 字段可参考），要么在文档里注明"预留"

---

## 附：本文档的核对方式

本文档所有接口、字段、状态码均来自**逐文件读源码**核对，不是从旧文档转述：

- Java 端点：`services/*/src/main/java/**/controller/*.java` 全部 11 个端点 Controller
- 网关路由与鉴权：`gateway-service/application.yml` + `JwtAuthFilter.java`
- Agent 端点与请求模型：`agents/*/app/main.py` 全部 8 个
- 前端调用：`web/src/api/*.ts` + `web/src/composables/useTaskPolling.ts` + 全部 11 个 View
- 契约一致性：`scripts/verify_contract.py` 的 `TASK_TYPE_TO_AGENT` / `ENDPOINT_TO_REQUEST` / `ALLOWED_EXTRA_KEYS`

**发现文档与实现不符时，以实现为准，并更新本文档与 `docs/API-CONTRACT.md`。**
