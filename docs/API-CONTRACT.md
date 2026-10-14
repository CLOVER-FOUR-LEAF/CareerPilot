# CareerPilot 接口契约（内部对齐基线）

> 本文件是 `services/`（Java 业务层）、`agents/`（Python AI 层）、`web/`（前端）三方共同遵守的唯一事实来源。
> 任何一方改动接口形状，必须先改本文件。

---

## 0. 通用约定

### 0.1 统一响应包络

所有 REST 接口（`/api/v1/**`）返回：

```json
{ "code": 0, "message": "success", "data": <T>, "traceId": "a1b2c3d4e5f6g7h8" }
```

- `code = 0` 表示成功，非 0 为业务错误码。
- 前端 axios 响应拦截器**直接返回整个包络对象**，业务代码通过 `res.data` 取业务数据。

### 0.2 身份传递

网关 `gateway-service` 校验 JWT 后，把用户身份写入请求头：

| 请求头 | 含义 |
|--------|------|
| `X-User-Id` | 业务用户 ID，形如 `usr_a1b2c3...`（**不是** `t_user.id` 主键） |

下游所有业务服务只信任 `X-User-Id`，且该接口**不对外暴露**（只走网关）。

### 0.3 双 ID 约定

每张主表都有一个自增 `id`（数据库主键）和一个业务 ID（`xxx_id`，形如 `res_xxx` / `jd_xxx`）。

- **跨服务引用一律用业务 ID**。
- **REST 路径参数一律用数据库主键**（`:id` 是数字）。
- 响应体里 `resumeId` / `jdId` 是**数据库主键**（供前端拼路径用），`resumeBizId` / `jdBizId` 是业务 ID（供跨服务与 Agent 层用）。

> 这是本项目最容易踩坑的地方，任何 list/detail 响应都必须同时给出这两个字段。

### 0.4 通用错误码

| code | HTTP | 含义 |
|------|------|------|
| `0` | 200 | 成功 |
| `1400` | 200 | 参数校验失败 |
| `1401` | 200 | 账号不存在 / 凭证无效 |
| `1402` | 200 | 密码错误 |
| `1403` | 200 | 账号被封禁 |
| `1404` | 200 | 资源不存在或无权访问 |
| `1409` | 200 | 资源冲突（如邮箱已注册） |
| `1500` | 200 | AI 额度不足 |
| `1501` | 200 | 依赖服务不可用（Agent 未启动等） |
| `401` | 401 | 未登录 / token 失效（网关直接返回，非包络结构） |

### 0.5 分页响应

所有 `list` 类接口统一返回：

```json
{ "total": 42, "page": 1, "size": 20, "items": [ ... ] }
```

> 前端必须读 `res.data.items`，**不能**把 `res.data` 当数组用。

---

## 1. user-center（8081）

网关路由：`/api/v1/auth/**`、`/api/v1/user/**`

### 1.1 `POST /api/v1/auth/register`

请求：

```json
{ "email": "a@b.com", "password": "Abcd1234", "nickname": "阿花", "inviteCode": null }
```

- `password` 正则：`^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$`

响应 `data`：**完整的登录响应**（注册即登录，不再让用户回登录页）：

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": {
    "userId": 1,
    "userIdBiz": "usr_a1b2c3",
    "email": "a@b.com",
    "nickname": "阿花",
    "avatar": null,
    "membershipLevel": "FREE"
  }
}
```

### 1.2 `POST /api/v1/auth/login`

请求：`{ "email": "...", "password": "..." }` → 响应同上。

### 1.3 `POST /api/v1/auth/logout`

- 前端同时带上 `refreshToken`；服务端把 `refreshToken` 的 jti 写入 Redis 黑名单（TTL = 剩余有效期）。
- 响应 `data = null`。

### 1.4 `POST /api/v1/auth/refresh`

请求：`{ "refreshToken": "..." }` → 响应同 1.1 的 `data` 结构（`refreshToken` 原样回传）。

### 1.5 `GET /api/v1/user/profile`

响应 `data`：

```json
{
  "userId": 1,
  "email": "a@b.com",
  "phone": "138****8888",
  "nickname": "阿花",
  "avatar": null,
  "gender": "女",
  "birthYear": 2000,
  "city": "杭州",
  "currentTitle": "后端工程师",
  "yearsOfExperience": 3,
  "educationLevel": "本科",
  "school": "XX大学",
  "major": "软件工程",
  "graduationYear": 2022,
  "targetIndustry": "互联网",
  "targetPosition": "高级后端工程师",
  "targetSeniority": "senior",
  "targetCity": ["杭州", "上海"],
  "targetSalaryMin": 25,
  "targetSalaryMax": 40,
  "skills": ["Java", "Spring Boot", "MySQL"],
  "createdAt": "2026-10-14T10:00:00",
  "updatedAt": "2026-10-14T10:00:00"
}
```

`PUT /api/v1/user/profile` 请求体同上（可缺省），返回同结构。

### 1.6 `GET /api/v1/user/membership`

```json
{
  "level": "FREE",
  "levelName": "免费版",
  "expireAt": null,
  "autoRenew": false,
  "aiQuotaRemaining": 3,
  "aiQuotaTotal": 3
}
```

---

## 2. resume-service（8082）

网关路由：`/api/v1/resume/**`

### 2.1 `POST /api/v1/resume/upload`

`multipart/form-data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | PDF / DOCX / DOC / TXT / MD，≤ 10MB |
| `title` | text | 否 | 缺省用原始文件名 |

响应 `data`：

```json
{
  "resumeId": 12,
  "resumeBizId": "res_a1b2c3d4",
  "versionId": "ver_9f8e7d6c",
  "title": "张三-后端-2026.pdf",
  "parseStatus": "PENDING",
  "taskId": "task_1122aabb",
  "createdAt": "2026-10-14T10:00:00"
}
```

**行为**：落库 → 抽取纯文本 → 建 `t_ai_task`（`taskType = resume_analyze`）→ 异步丢给 ai-task-scheduler。接口**不等 AI**，立即返回。

### 2.2 `GET /api/v1/resume/list?page=1&size=20&keyword=`

`data.items[]`：

```json
{
  "resumeId": 12,
  "resumeBizId": "res_a1b2c3d4",
  "title": "张三-后端-2026.pdf",
  "atsScore": 78,
  "parseStatus": "SUCCESS",
  "isPrimary": false,
  "updatedAt": "2026-10-14T10:05:00"
}
```

`parseStatus` ∈ `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`。

### 2.3 `GET /api/v1/resume/{id}`

```json
{
  "resumeId": 12,
  "resumeBizId": "res_a1b2c3d4",
  "title": "...",
  "atsScore": 78,
  "parseStatus": "SUCCESS",
  "textContent": "抽取出来的简历全文纯文本",
  "createdAt": "...",
  "updatedAt": "..."
}
```

### 2.4 `GET /api/v1/resume/{id}/ats-score`

**直接返回 Agent 的 `AnalysisResult` 原结构**（前端已按此结构渲染，不要再包一层）：

```json
{
  "resume_id": "res_a1b2c3d4",
  "overall_score": 78.2,
  "dimensions": [
    { "name": "structure", "score": 80, "weight": 1.5, "feedback": "简历结构完整…" }
  ],
  "skills": [{ "name": "java", "category": "technical", "proficiency": "intermediate" }],
  "suggestions": [{ "category": "keyword", "priority": "high", "title": "增加行业关键词", "detail": "…" }],
  "section_count": 5,
  "word_count": 1832,
  "has_contact": true,
  "updatedAt": "2026-10-14T10:05:00"
}
```

未分析完成时返回 `code = 1404`，`message = "简历尚未完成分析"`。

### 2.5 `GET /api/v1/resume/{id}/versions`

```json
[{
  "versionId": "ver_9f8e7d6c",
  "versionNo": 1,
  "atsScore": 78,
  "changeNote": "初始上传",
  "createdAt": "..."
}]
```

### 2.6 `POST /api/v1/resume/{id}/reparse`

重新触发分析，返回 `data = { "taskId": "task_xxx", "parseStatus": "PENDING" }`。

### 2.7 `POST /internal/resume-analyze-callback`（内部）

ai-task-scheduler 回调。**不经网关**。

```json
{
  "taskId": "task_1122aabb",
  "bizId": "res_a1b2c3d4",
  "success": true,
  "output": { /* AnalysisResult 原样 */ },
  "errorMessage": null,
  "durationMs": 1820
}
```

---

## 3. jd-service（8083）

网关路由：`/api/v1/jd/**`

### 3.1 `POST /api/v1/jd/upload`

```json
{
  "jobTitle": "高级后端工程师",
  "companyName": "某厂",
  "rawText": "职位描述全文…",
  "sourceType": "MANUAL",
  "sourceUrl": "https://…",
  "salaryRange": "25-40k"
}
```

- `sourceType` ∈ `MANUAL` / `URL` / `FILE`，缺省 `MANUAL`。
- `rawText` 必填非空。

响应 `data`：

```json
{
  "jdId": 7,
  "jdBizId": "jd_a1b2c3d4",
  "title": "高级后端工程师",
  "company": "某厂",
  "parseStatus": "PENDING",
  "taskId": "task_3344ccdd",
  "createdAt": "2026-10-14T10:00:00"
}
```

### 3.2 `GET /api/v1/jd/list?page=1&size=20&keyword=&company=`

`data.items[]`：

```json
{
  "jdId": 7,
  "jdBizId": "jd_a1b2c3d4",
  "title": "高级后端工程师",
  "company": "某厂",
  "parseStatus": "SUCCESS",
  "sourceUrl": null,
  "rawTextPreview": "职位描述全文…的前 200 字",
  "createdAt": "..."
}
```

### 3.3 `GET /api/v1/jd/{id}/parse/result`

**直接返回 Agent 的 `JdChunk` 原结构**（前端已按此结构渲染）：

```json
{
  "job_title": "高级后端工程师",
  "company_name": "某厂",
  "department": "",
  "location": "杭州",
  "employment_type": "full_time",
  "salary_min": 25,
  "salary_max": 40,
  "salary_currency": "CNY",
  "experience_min_years": 3,
  "experience_max_years": 5,
  "education_required": "本科",
  "skills": [{ "name": "java", "category": "technical", "importance": "required", "years_required": null }],
  "responsibilities": ["…"],
  "requirements": ["…"],
  "benefits": ["…"],
  "raw_text": "…",
  "parse_score": 0.92,
  "parse_method": "regex+classify",
  "seniority": "senior",
  "industry": "互联网",
  "createdAt": "2026-10-14T10:00:12"
}
```

额外补两个后端派生字段：`seniority`、`industry`、`createdAt`。

未解析完成返回 `code = 1404`，`message = "JD 尚未解析完成"`。

### 3.4 其余

- `GET /api/v1/jd/{id}` → `{ jdId, jdBizId, title, company, salaryRange, rawText, sourceUrl, sourceType, parseStatus, createdAt, updatedAt }`
- `DELETE /api/v1/jd/{id}` → `data = null`
- `POST /api/v1/jd/{id}/reparse` → `data = { "taskId": "...", "parseStatus": "PENDING" }`

### 3.5 `POST /internal/jd-parse-callback`（内部）

```json
{
  "taskId": "task_3344ccdd",
  "bizId": "jd_a1b2c3d4",
  "success": true,
  "output": { /* JdChunk 原样 */ },
  "errorMessage": null,
  "durationMs": 940
}
```

---

## 4. match-service（8084，新增）

网关路由：`/api/v1/match/**`

### 4.1 `POST /api/v1/match/evaluate`

```json
{ "resumeId": 12, "jdId": 7 }
```

（两个都是数据库主键。）

响应 `data`：

```json
{
  "recordId": "mch_a1b2c3d4",
  "resumeId": 12,
  "jdId": 7,
  "status": "PENDING",
  "taskId": "task_5566eeff",
  "createdAt": "..."
}
```

### 4.2 `GET /api/v1/match/{recordId}`

未完成时 `status = PENDING` 且分数为 `null`；完成后：

```json
{
  "recordId": "mch_a1b2c3d4",
  "resumeId": 12,
  "jdId": 7,
  "status": "SUCCESS",
  "taskId": "task_5566eeff",
  "semanticScore": 74,
  "keywordScore": 61,
  "businessScore": 80,
  "finalScore": 70,
  "matchedSkills": ["java", "spring"],
  "gapList": [
    { "gapType": "missing_skill", "gapContent": "Kafka", "severity": 4,
      "suggestion": "在项目经历中加入 Kafka 消息队列的使用案例" }
  ],
  "createdAt": "...",
  "completedAt": "..."
}
```

### 4.3 `GET /api/v1/match/list?page=1&size=20`

`data.items[]` 为 `4.2` 的精简版（省略 `gapList`）。

### 4.4 `POST /internal/match-callback`（内部）

```json
{
  "taskId": "task_5566eeff",
  "bizId": "mch_a1b2c3d4",
  "success": true,
  "output": { /* MatchResult，见下 */ },
  "errorMessage": null,
  "durationMs": 2310
}
```

---

## 5. ai-task-scheduler（8085）

网关路由：`/api/v1/ai-tasks/**`、`/api/v1/interview/**`、`/api/v1/cover-letter/**`、`/api/v1/career-plan/**`

### 5.1 `POST /api/v1/ai-tasks`

```json
{
  "taskType": "jd_parse",
  "bizId": "jd_a1b2c3d4",
  "input": { "jd_id": "jd_a1b2c3d4", "jd_text": "…" }
}
```

`taskType` 枚举与下游映射：

| taskType | Agent 服务 | 回调目标 |
|----------|-----------|---------|
| `jd_parse` | `:8001/api/orchestrate`（内部转 jd-parser） | `http://jd-service:8083/internal/jd-parse-callback` |
| `resume_analyze` | `:8001/api/orchestrate` | `http://resume-service:8082/internal/resume-analyze-callback` |
| `match_score` | `:8001/api/orchestrate` | `http://match-service:8084/internal/match-callback` |
| `resume_optimize` | `:8001/api/orchestrate` | 无（结果直接落在任务表，前端按 taskId 取） |
| `interview` | `:8001/api/orchestrate` | 同上 |
| `cover_letter` | `:8001/api/orchestrate` | 同上 |
| `career_plan` | `:8001/api/orchestrate` | 同上 |

响应 `data` 为任务快照（见 5.3）。

### 5.2 `GET /api/v1/ai-tasks/{taskId}`

### 5.3 任务对象

```json
{
  "taskId": "task_1122aabb",
  "userId": "usr_a1b2c3",
  "taskType": "jd_parse",
  "bizId": "jd_a1b2c3d4",
  "status": "RUNNING",
  "progress": 40,
  "currentAgent": "jd-parser-agent",
  "input": { … },
  "output": { … },
  "errorMessage": null,
  "retryCount": 0,
  "durationMs": null,
  "startedAt": "2026-10-14T10:00:01",
  "completedAt": null,
  "createdAt": "2026-10-14T10:00:00",
  "updatedAt": "2026-10-14T10:00:02"
}
```

`status` ∈ `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELLED`。

### 5.4 `GET /api/v1/ai-tasks/{taskId}/stream`

`text/event-stream`，**真正持续推送**：

```
event: snapshot
data: {"taskId":"task_x","status":"RUNNING","progress":40,"currentAgent":"jd-parser-agent"}

event: done
data: {"taskId":"task_x","status":"SUCCESS","progress":100,"output":{…}}

event: error
data: {"taskId":"task_x","status":"FAILED","errorMessage":"…"}
```

服务端按 500ms 轮询任务表，直到 `SUCCESS`/`FAILED`/`CANCELLED` 才关闭流。

---

## 6. agent-orchestrator（8001，Python）

### 6.1 `POST /api/orchestrate`

```json
{
  "task_type": "jd_parse",
  "input_data": { … },
  "user_id": "usr_xxx"
}
```

响应：

```json
{
  "task_id": "task_xxx",
  "status": "completed",
  "progress": 100,
  "result": { /* 对应 Agent 的原始输出 */ },
  "error": null
}
```

> `result` **不额外包一层**，就是 `JdChunk` / `AnalysisResult` / `MatchResult` 本身。这样回调方可以直接落库。

### 6.2 `GET /health` / `POST /api/match` / `GET /api/stream/{task_type}`

保留，`POST /api/match` 等价于 `task_type = match_score` 的 `/api/orchestrate`。

---

## 7. Agent 输出结构（Python 侧 Pydantic 模型）

### 7.1 `JdChunk`（jd-parser-agent）

见 §3.3。注意 `seniority` / `industry` 是后端派生补充字段，Agent 不产出也可以。

### 7.2 `AnalysisResult`（resume-analyzer-agent）

见 §2.4。

### 7.3 `MatchResult`（match-agent）

```json
{
  "semantic_score": 74,
  "keyword_score": 61,
  "business_score": 80,
  "final_score": 70,
  "matched_skills": ["java", "spring"],
  "gap_list": [
    { "gap_type": "missing_skill", "gap_content": "Kafka", "severity": 4, "suggestion": "…" }
  ]
}
```

打分口径（**必须严格遵守**，这是项目的核心可解释性卖点）：

```
final_score = round(0.40 * semantic_score + 0.35 * keyword_score + 0.25 * business_score)
```

- `semantic_score`：JD 全文与简历全文的语义相似度，0-100。
- `keyword_score`：JD 硬技能在简历中的覆盖率，0-100（分母 = JD 硬技能数）。
- `business_score`：业务指标/年限/学历对齐度，0-100。
- `gap_list` 按 `severity` **倒序**输出；硬性条件不满足（如学历、经验年限）severity 固定为 5。

> **关于 `gap_type` 的约定**：枚举只保留 `missing_skill` / `weak_metric` / `missing_cert` 三种。
> 硬性条件不满足（学历 / 年限）**不新增枚举值**，而是记为 `weak_metric` + `severity=5`，
> 由排序规则把它顶到最前面。这样前端不需要为「硬性条件」单独写一套渲染分支，
> 同时又保证它在视觉上一定是第一条。

### 7.4 `ResumeOptimizeResult`（resume-optimizer-agent）

入参 `POST /api/optimize`：

```json
{
  "bullet": "负责用户增长相关工作",
  "jd_skills": ["A/B Testing", "用户增长"],
  "tone": "professional"
}
```

> 入参字段是 **`bullet`**，不是 `original`；`original` 只是输出里的「改写前原文」。

输出：
```json
{
  "original": "负责用户增长相关工作",
  "rewritten": "主导用户增长体系搭建，通过 A/B 测试迭代 12 套策略，3 个月将注册转化率从 3.1% 提升至 4.7%",
  "injected_skills": ["A/B Testing", "用户增长"],
  "star_breakdown": { "situation": "…", "task": "…", "action": "…", "result": "…" },
  "change_note": "补充量化结果并植入 A/B Testing 关键词"
}
```

### 7.5 `InterviewQuestion` / `InterviewEvaluation`（interview-agent）

出题 `POST /api/question`：

```json
{
  "round_no": 1,
  "total_rounds": 5,
  "jd_skills": ["Java", "Kafka"],
  "resume_highlights": ["高并发网关重构"],
  "previous_answer": null,
  "job_title": "高级后端工程师"
}
```

> **`total_rounds` 必须显式传**。不传时 Agent 侧默认 3；调用方若允许 5 轮而漏传，
> 第 4 轮会被 `round_no > total_rounds` 直接拒掉。
> 带上 `previous_answer` 时 Agent 改为生成针对该回答的**追问**而不是新题 —— 这正是「多轮追问」的关键。

出题输出：`{ "round_no": 1, "question": "…", "question_type": "behavioral|technical|followup", "focus": "…" }`

评价入参 `POST /api/evaluate`：`{ "question": "…", "answer": "…", "jd_skills": [] }`
评价输出：`{ "score": 7.5, "feedback": "…", "star_coverage": {"situation": true, "task": true, "action": false, "result": false}, "followup": "…" }`

### 7.6 `CoverLetter`（cover-letter-agent）

入参 `POST /api/generate`：

```json
{
  "jd_skills": [],
  "resume_highlights": [],
  "company_name": "某厂",
  "job_title": "高级后端工程师",
  "tone": "professional",
  "company_info": null
}
```

> 公司名字段是 **`company_name`**，不是 `company`。

### 7.7 `CareerPlan`（career-advisor-agent）

入参 `POST /api/plan`：

```json
{
  "current_title": "后端工程师",
  "target_industry": "金融",
  "target_position": "风控开发",
  "years_of_experience": 3,
  "skills": ["Java", "MySQL"],
  "question": null
}
```

> `target_industry` 与 `target_position` **必填**，缺失时 Agent 返回 400。

输出：

```json
{
  "summary": "…",
  "roadmap": [
    { "phase": "第 1-2 个月", "goal": "…", "actions": ["…"], "milestone": "…", "doc_ids": ["kb_fin_001"] }
  ],
  "skill_gap": ["…"],
  "citations": [{ "doc_id": "kb_fin_001", "source": "…", "version": "2026.01" }]
}
```

> 每条 roadmap 的 `doc_ids` 只能引用检索真实命中的文档，禁止编造引用。

---

## 8. 前端页面清单

| 路由 | 组件 | 依赖接口 |
|------|------|---------|
| `/login` | `Login.vue` | §1.2 |
| `/register` | `Register.vue` | §1.1（注册后直接进首页） |
| `/dashboard` | `Dashboard.vue` | §2.2 §3.2 §5.3 |
| `/resume` | `Resume.vue` | §2.1 §2.2 §2.4 §2.5 |
| `/jd` | `JdManage.vue` | §3.1 §3.2 §3.3 |
| `/match` | `Match.vue` | §4.1 §4.2 §4.3 |
| `/optimize` | `Optimize.vue` | §5.1（`resume_optimize`）§5.3 |
| `/interview` | `Interview.vue` | §5.1（`interview`）§5.3 |
| `/cover-letter` | `CoverLetter.vue` | §5.1（`cover_letter`）§5.3 |
| `/career-plan` | `CareerPlan.vue` | §5.1（`career_plan`）§5.3 |
| `/profile` | `Profile.vue` | §1.5 §1.6 |

---

## 9. 配置项（环境变量）

所有 Java 服务统一：

| 变量 | 默认 | 说明 |
|------|------|------|
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3306` | |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `careerpilot` / `cp123456` | |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / `redis123456` | |
| `JWT_SECRET` | 内置默认值 | 生产必须覆盖 |
| `AI_SCHEDULER_URL` | `http://localhost:8085` | 供 jd/resume/match 服务创建任务 |
| `AI_ORCHESTRATOR_URL` | `http://localhost:8001` | 供 ai-task-scheduler 调 Agent |
| `JD_SERVICE_URL` / `RESUME_SERVICE_URL` / `MATCH_SERVICE_URL` | `http://localhost:8083` 等 | 供 ai-task-scheduler 回调 |

Python Agent 统一：

| 变量 | 默认 | 说明 |
|------|------|------|
| `ORCHESTRATOR_PORT` | `8001` | |
| `JD_PARSER_URL` | `http://localhost:8002` | |
| `RESUME_ANALYZER_URL` | `http://localhost:8003` | |
| `OPENAI_API_KEY` | 空 | 为空时走纯规则引擎，不调用 LLM |
