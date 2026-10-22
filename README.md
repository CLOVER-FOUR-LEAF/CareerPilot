# CareerPilot · 智能求职领航员

> 基于多 Agent 协同（LangGraph 编排）的求职全链路平台：
> **解析 JD → 诊断简历 → 匹配打分 → 优化润色 → 模拟面试 → 求职信 → 职业规划**

---

## 架构总览

```
                       ┌──────────────┐
   浏览器  ───────────▶ │    Nginx     │  静态资源 + 反向代理
                       └──────┬───────┘
                              │ /api/v1/**
                       ┌──────▼───────┐
                       │ gateway (8080)│  JWT 校验 → 注入 X-User-Id → 路由
                       └──┬───┬───┬───┬───┬────────┘
              ┌───────────┘   │   │   │   └───────────┐
              ▼               ▼   │   ▼               ▼
      ┌──────────────┐ ┌──────────┐ │ ┌──────────┐ ┌────────────┐
      │ user-center  │ │ resume-  │ │ │jd-service│ │ match-     │
      │   (8081)     │ │ service  │ │ │  (8083)  │ │ service    │
      │ 认证/画像/会员 │ │ (8082)   │ │ │          │ │  (8084)    │
      └──────────────┘ └────┬─────┘ │ └────┬─────┘ └─────┬──────┘
                            │       │      │             │
                            └───────┴──────┴─────────────┘
                                          │ 建任务 / 写回
                                    ┌─────▼──────────────┐
                                    │ ai-task-scheduler  │
                                    │      (8085)        │  线程池异步 + SSE 进度
                                    └─────┬──────────────┘
                                          │ /api/orchestrate
                                    ┌─────▼──────────────┐
                                    │ agent-orchestrator │  LangGraph 状态图
                                    │      (8001)        │
                                    └──┬───┬───┬───┬───┬──┘
                     ┌─────────────────┘   │   │   │   └─────────────────┐
                     ▼                     ▼   │   ▼                     ▼
             ┌──────────────┐  ┌──────────────┐ │ ┌──────────┐ ┌──────────────────┐
             │jd-parser 8002│  │resume-analyze│ │ │match 8004│ │ optimizer 8005   │
             │               │  │      8003    │ │ │          │ │ interview  8006  │
             │               │  │              │ │ │          │ │ cover-letter 8007│
             │               │  │              │ │ │          │ │ career-adv  8008 │
             └──────────────┘  └──────────────┘ │ └──────────┘ └──────────────────┘
                                                  ▲
                                          业务服务经 /internal/** 回调写回
```

### 一条完整链路（以「简历 × JD 匹配」为例）

```
1. 前端 POST /api/v1/match/evaluate { resumeId, jdId }
2. gateway 校验 JWT，注入 X-User-Id
3. match-service：
   ├─ GET resume-service /internal/resumes/{id}/text?userId=…   ← 归属校验在数据所有者手里
   ├─ GET jd-service     /internal/jds/{id}/text?userId=…
   ├─ 落一条 t_match_record (status=PENDING)
   └─ POST ai-task-scheduler /api/v1/ai-tasks { taskType: match_score, bizId: recordId }
4. ai-task-scheduler 线程池异步执行 → POST agent-orchestrator /api/orchestrate
5. LangGraph 路由 → match-agent /api/match
      final = 0.40×语义 + 0.35×关键词 + 0.25×业务
      输出 Gap List（按 severity 倒序，硬性条件 severity=5）
6. 结果落回 t_ai_task.output_payload，并回调
   POST match-service /internal/match-callback
7. match-service 写回分数列 + t_match_gap 明细
8. 前端轮询 GET /api/v1/match/{recordId}（或走 SSE）
```

---

## 技术栈

| 层 | 选型 |
|----|------|
| 前端 | Vue 3.4 + TypeScript + Vite 5 + Ant Design Vue 4 + Pinia + ECharts |
| 网关 | Spring Cloud Gateway 4.3（WebFlux） |
| 业务服务 | Spring Boot 3.5 + MyBatis-Plus 3.5 + MySQL 8 + Redis 7 |
| AI 编排 | LangGraph（StateGraph）+ FastAPI + httpx |
| AI Agent | 8 个 Python 服务，规则引擎为主、LLM 为可选增强 |
| 可观测 | Actuator + Spring Boot 日志（Prometheus/Grafana 为二期） |

**编译目标 Java 21**（字节码向下兼容）。构建机可以是 JDK 24/25 ——
但 Lombok 必须 ≥ 1.40，否则注解处理器在 JDK 24+ 上会静默失效（所有 getter 都不生成）。

---

## 目录结构

```
CareerPilot/
├── docs/API-CONTRACT.md      ★ 三方共同遵守的接口契约，改接口先改它
├── db/migrations/            MySQL 初始化 Schema（唯一事实来源）
├── scripts/verify_contract.py 契约一致性检查（前后端 / Agent 字段名）
├── services/                 Java 微服务
│   ├── gateway-service/      8080
│   ├── user-center/          8081  认证 / 画像 / 会员
│   ├── resume-service/       8082  简历上传 / 版本 / ATS 评分
│   ├── jd-service/           8083  JD 解析
│   ├── match-service/        8084  匹配打分 / Gap List
│   └── ai-task-scheduler/    8085  任务编排 / SSE 进度 / 回调分发
├── agents/                   Python AI 层
│   ├── agent-orchestrator/   8001  LangGraph 编排
│   ├── jd-parser-agent/      8002
│   ├── resume-analyzer-agent/8003
│   ├── match-agent/          8004  ★ 三段式加权打分
│   ├── resume-optimizer-agent/8005
│   ├── interview-agent/      8006
│   ├── cover-letter-agent/   8007
│   └── career-advisor-agent/ 8008  RAG 行业知识库
├── web/                      Vue 前端
├── android/                  ★ Android 原生客户端（Kotlin + Jetpack Compose）
└── deploy/                   docker-compose + nginx
```

---

## Android 客户端

`android/` 下是一套**原生 Android 实现**（Kotlin + Jetpack Compose + Material 3），
覆盖 Web 端的全部页面：登录注册、首页、简历管理、JD 解析、匹配打分、
简历优化、模拟面试、求职信、职业规划、个人中心。

**只做前端**：数据全部来自现有后端的 `/api/v1/**`，后端上服务器后
在 App 内改一次服务器地址即可，不用重新打包。

```powershell
cd android
.\gradlew.bat assembleRelease      # 产物：app\build\outputs\apk\release\app-release.apk
```

详见 [`android/README.md`](./android/README.md)。

---

## 本地启动

### 0. 前置

- JDK 21+（本项目在 JDK 25 上验证通过）
- Maven 3.9+
- Node 20+
- Python 3.11+
- Docker + Docker Compose

### 1. 一键起全栈

```bash
cd deploy
cp .env.example .env       # 可选：覆盖端口、密码、JWT_SECRET
docker compose up -d --build
```

启动后：

| 地址 | 说明 |
|------|------|
| http://localhost | 前端（经 Nginx） |
| http://localhost:8080 | 网关直连 |
| http://localhost:8001/docs | 编排服务 OpenAPI |
| http://localhost:8004/docs | 匹配 Agent OpenAPI |

> 数据库初始化脚本只在 **数据卷为空**（首次 `up`）时执行。
> 改了 `db/migrations/V1__init_schema.sql` 之后要重来：
> `docker compose down -v && docker compose up -d --build`。

### 2. 分开启动（开发模式）

```bash
# ── 基础设施 ─────────────────────────────────
cd deploy && docker compose up -d mysql redis

# ── Java 微服务（每个新开一个终端）────────────
cd services && mvn clean package -DskipTests
java -jar services/user-center/target/user-center-1.0.0-SNAPSHOT.jar
java -jar services/resume-service/target/resume-service-1.0.0-SNAPSHOT.jar
java -jar services/jd-service/target/jd-service-1.0.0-SNAPSHOT.jar
java -jar services/match-service/target/match-service-1.0.0-SNAPSHOT.jar
java -jar services/ai-task-scheduler/target/ai-task-scheduler-1.0.0-SNAPSHOT.jar
java -jar services/gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar

# ── Python Agent 层（每个新开一个终端）────────
cd agents/match-agent && pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8004
# 其余 7 个 Agent 同理，端口见上表

# ── 前端 ────────────────────────────────────
cd web && npm install && npm run dev      # http://localhost:3000
```

`vite.config.ts` 已配好 `/api` → `http://localhost:8080` 的代理，前端无需额外配置。

### 3. 不配 LLM 也能跑

所有 Agent 的**规则引擎是主实现**，完整的；`OPENAI_API_KEY` 为空时自动跳过 LLM 增强。
`career-advisor-agent` 刻意完全不调 LLM —— 让模型改写 roadmap 会导致引用与 `doc_ids` 脱节，
破坏「引用可溯源」这个承诺。

---

## 配置项

所有配置都支持环境变量覆盖，默认值见各服务的 `application.yml`。

| 变量 | 默认 | 说明 |
|------|------|------|
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3306` | |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `careerpilot` / `cp123456` | |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / `redis123456` | |
| `JWT_SECRET` | 内置默认值 | **生产必须覆盖** |
| `AI_SCHEDULER_URL` | `http://localhost:8085` | 业务服务建任务用 |
| `AI_ORCHESTRATOR_URL` | `http://localhost:8001` | 调度服务调 Agent 用 |
| `RESUME_SERVICE_URL` / `JD_SERVICE_URL` | `http://localhost:8082` / `:8083` | match-service 取素材用 |
| `STORAGE_ROOT` | `./data/uploads` | 简历文件存储根目录 |
| `OPENAI_API_KEY` | 空 | 为空则纯规则引擎 |
| `MATCH_AGENT_URL` 等 | `http://localhost:8004` … | orchestrator 分发用 |

---

## 接口契约

完整的请求 / 响应结构见 **[docs/API-CONTRACT.md](./docs/API-CONTRACT.md)**。
改动任何一方的接口形状，先改那份文档。

### 改完接口后跑一致性检查

```bash
python scripts/verify_contract.py
```

它会检查：

1. `agents/` 下所有 Python 文件语法正确、无乱码字符
2. 每个 Agent 的 Pydantic 请求模型存在
3. **前端每个 AI 页面提交的 `input` 键，确实是目标 Agent 接受的字段**
   （历史上就因为 `original`/`bullet`、`company`/`company_name`、漏传 `total_rounds` 踩过坑）
4. 契约文档记录了关键字段

退出码 0 = 通过。

---

## 已知边界

- **未接入对象存储**：简历文件落本地磁盘（`STORAGE_ROOT`），切 MinIO 只需替换
  `FileStorageService#store` 一个方法。
- **未做配额扣减**：`t_membership.ai_quota_remaining` 已有字段，但调度侧还没在任务开始时扣减。
- **支付/订阅表已建但无业务**：订单、支付、订阅、通知四张表已就位，二期填充。
- **语义相似度是轻量实现**：2 文档 TF-IDF 余弦，未引入向量库。
  项目文档里描述的 ChromaDB + bge-reranker 属于二期。
- **无自动化测试**：本项目当前不设测试环境，构建门禁是
  `mvn package` + `npm run build` + `scripts/verify_contract.py`。

---

## 设计上的几个取舍

**为什么登记 AI 任务失败不影响上传成功？**
文件已经落盘、正文已经入库，用户至少还能看到自己的简历。调度服务抖一下不该废掉整个上传功能。
所以 `AiTaskClient` 失败返回 `null` 而不是抛异常，记录留在 `PENDING` 等用户手动重试。

**为什么登出只拉黑 refreshToken，不拉黑 accessToken？**
accessToken 是无状态 JWT，网关每请求查一次 Redis 就把网关变成了瓶颈。
refreshToken 才是真正的长期凭证，把它的 jti 写进 Redis（TTL = 剩余寿命）就够了。

**为什么 match-service 不直连简历和 JD 的表？**
归属校验必须发生在数据所有者手里。match-service 猜到别人的 `resumeId` 去直连表就能读走简历；
走 `/internal/**` 让 resume-service 自己校验 `userId`，越权在源头就被挡住。

**为什么网关要先剥掉 `X-User-Id` 再写入？**
`mutate().header()` 是**追加**不是替换。只追加的话，外部请求自带一个 `X-User-Id` 就能让下游读到伪造身份，
直接变成越权。

**为什么 JD 解析结果要存原始 JSON？**
前端的解析结果弹窗直接按 Agent 返回的 `JdChunk` 结构渲染。不存原结构就意味着后端每次都要重新拼一遍，
Agent 改字段时前端就得跟着改两处。
