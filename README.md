# CareerPilot · 智能求职领航员

> 基于多 Agent 协同（LangGraph 编排）的求职全链路平台：
> **解析 JD → 诊断简历 → 匹配打分 → 优化润色 → 模拟面试 → 求职信 → 职业规划**

---

## 架构总览

```
                       ┌──────────────┐
   浏览器  ───────────▶ │ Vite / Nginx│  前端静态资源 + 反向代理
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
├── scripts/
│   ├── verify_contract.py    契约一致性检查（前后端 / Agent 字段名）
│   └── local/                ★ 本地运行脚本（见下）
├── services/                 Java 微服务
│   ├── gateway-service/      8080
│   ├── user-center/          8081  认证 / 画像 / 会员
│   ├── resume-service/       8082  简历上传 / 版本 / ATS 评分
│   ├── jd-service/           8083  JD 解析
│   ├── match-service/        8084  匹配打分 / Gap List
│   ├── ai-task-scheduler/    8085  任务编排 / SSE 进度 / 回调分发
│   └── admin-service/        8086  ★ 平台管理端（仅 ADMIN）
├── agents/                   Python AI 层
│   ├── agent-orchestrator/   8001  LangGraph 编排
│   ├── jd-parser-agent/      8002
│   ├── resume-analyzer-agent/8003
│   ├── match-agent/          8004  ★ 三段式加权打分
│   ├── resume-optimizer-agent/8005
│   ├── interview-agent/      8006
│   ├── cover-letter-agent/   8007
│   └── career-advisor-agent/ 8008  RAG 行业知识库
├── web/                      ★ 用户端 Vue 前端（/ 官网首页 + /seeker 求职者 + /hr 招聘方）
├── admin-web/                ★ 管理端独立前端（运营/用户行为/Agent 配置/健康/审计）
├── android/                  ★ Android 原生客户端（Kotlin + Jetpack Compose）
└── deploy/nginx/             Nginx 配置（可选，用静态产物模式时用）
```

> 三端拆分（角色模型、路由矩阵、鉴权链路）见 [`docs/PLATFORM-SPLIT.md`](./docs/PLATFORM-SPLIT.md)。
> 视觉系统（纸张 / 墨色 / 朱砂、字体与组件令牌）见 [`docs/UI-DESIGN.md`](./docs/UI-DESIGN.md)。
> Nacos 接入评估见 [`docs/NACOS-PLAN.md`](./docs/NACOS-PLAN.md)。

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

> **Linux / Docker**：用 `scripts/local/linux/start-all.sh`（基础设施跑 Docker），
> 详细步骤见 [`docs/PLATFORM-SPLIT.md`](./docs/PLATFORM-SPLIT.md#5-本地启动与账号)。
> 下面这节是 Windows 本机直跑的方式。

**Windows 下不依赖 Docker。** 全部进程直接跑在本机：MySQL / Redis 是本机服务，
6 个 Java 微服务 + 1 个管理服务是 JVM 进程，8 个 Python Agent 是 uvicorn 进程，两个前端是 vite dev server。

### 0. 前置

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 编译目标 Java 21；Lombok 必须 ≥ 1.40 |
| Maven | 3.9+ | |
| Node.js | 20+ | |
| Python | 3.11+ | 建议 3.12 |
| MySQL | 8.x | 只用 `mysqld` / `mysql` 两个可执行文件即可 |
| Redis | 7.x | Windows 版可用 <https://github.com/redis-windows/redis-windows/releases> |

### 1. 配置

```powershell
copy scripts\local\.env.example scripts\local\.env
```

按需修改。默认配置会：

- 在 **3307** 端口起一个**项目专用的 MySQL 实例**（数据目录 `D:\CareerPilotData\mysql`），
  不占用你机器上已有的 3306；
- 在 6379 起 Redis；
- 业务账号 `careerpilot` / `cp123456`。

> 想改用你现有的 MySQL？把 `.env` 里的 `MYSQL_PORT` 改成 `3306`，
> 再把 `MYSQL_USER` / `MYSQL_PASSWORD` 改成该实例上真实存在的账号即可。

### 2. 初始化数据库（一次性）

```powershell
powershell -File scripts\local\init-db.ps1
```

会做四件事：初始化数据目录 → 启动 mysqld → 建库建账号 → 导入
`db/migrations/V1__init_schema.sql`（20 张表）。**幂等**，重复执行不会重复建表。

### 3. 一键起全栈

```powershell
powershell -File scripts\local\start-all.ps1
```

顺序：基础设施 → Python 环境 → Agent 层 → Java 微服务 → 前端。
首次运行会自动装 Python 依赖和构建 jar（合计十几分钟），之后都是秒级启动。

启动后：

| 地址 | 说明 |
|------|------|
| http://localhost:3000 | 官网首页（项目介绍 + 求职者 / 招聘者双入口） |
| http://localhost:3000/seeker/dashboard | 求职者工作台（需 USER） |
| http://localhost:3000/hr/dashboard | 招聘方工作台（需 RECRUITER / ADMIN） |
| http://localhost:3100 | 管理端（仅 ADMIN）：概览 / 用户行为 / Agent 配置 / 配置 / 健康 / 审计 |
| http://localhost:8080 | 网关直连 |
| http://localhost:8080/actuator/health | 健康检查 |
| http://localhost:8001/docs | 编排服务 OpenAPI |
| http://localhost:8004/docs | 匹配 Agent OpenAPI |

演示账号（Linux/Docker 环境用 `scripts/local/linux/bootstrap-admin.sh` 引导）：

| 账号 | 密码 | 角色 |
|---|---|---|
| `user@demo.local` | `User1234` | USER |
| `hr@demo.local` | `Abcd1234` | RECRUITER |
| `admin@demo.local` | `Admin1234` | ADMIN |

### 4. 分步启动 / 状态 / 停止

```powershell
powershell -File scripts\local\setup-python-envs.ps1   # 8 个 Agent 的 venv + 依赖
powershell -File scripts\local\build-services.ps1      # mvn clean package
powershell -File scripts\local\start-infra.ps1        # MySQL + Redis
powershell -File scripts\local\start-agents.ps1        # 8 个 Python Agent
powershell -File scripts\local\start-services.ps1      # 6 个 Java 微服务
powershell -File scripts\local\start-frontend.ps1     # 前端
powershell -File scripts\local\status.ps1             # 状态总览
powershell -File scripts\local\stop-all.ps1           # 一键停止
```

进程日志统一在 `.runtime/logs/`，PID 在 `.runtime/*.pid`（都已在 `.gitignore` 里）。

### 5. 不配 LLM 也能跑

所有 Agent 的**规则引擎是主实现**，完整的；`OPENAI_API_KEY` 为空时自动跳过 LLM 增强。
`career-advisor-agent` 刻意完全不调 LLM —— 让模型改写 roadmap 会导致引用与 `doc_ids` 脱节，
破坏「引用可溯源」这个承诺。

---

## 配置项

所有配置都支持环境变量覆盖，默认值见各服务的 `application.yml`。
本地运行统一由 `scripts/local/.env` 提供，启动脚本会把它导出到各子进程。

| 变量 | 默认 | 说明 |
|------|------|------|
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3306` | 本地脚本默认用 3307（项目专用实例） |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `careerpilot` / `cp123456` | |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / `redis123456` | |
| `JWT_SECRET` | 内置默认值 | **生产必须覆盖** |
| `AI_SCHEDULER_URL` | `http://localhost:8085` | 业务服务建任务用 |
| `AI_ORCHESTRATOR_URL` | `http://localhost:8001` | 调度服务调 Agent 用 |
| `RESUME_SERVICE_URL` / `JD_SERVICE_URL` | `http://localhost:8082` / `:8083` | match-service 取素材用 |
| `STORAGE_ROOT` | `./data/uploads` | 简历文件存储根目录，本地脚本改为数据目录下的 `uploads` |
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

### 另外两个自动化检查

```bash
python scripts/check_semantic_loss.py    # 语义丢失（前端 → Agent）
python scripts/check_forbidden_terms.py  # 禁用词（合规自检）
```

`check_semantic_loss.py` 查的是 `verify_contract.py` 查不出来的那一类问题：
**前端传了，但链路中间没人消费**。历史上就因为 Pydantic 默认
`extra='ignore'`，`jd_skills` 被静默丢弃，三个 AI 功能悄悄退化成
通用模板文案，而契约检查是全绿的。

---

## HR 侧能力（招聘方视角）

> **本报告为辅助建议，最终判断由招聘方作出。**

同一套匹配引擎，翻转一个视角服务招聘方。

### 它解决什么问题

简历是**自述材料**，不是能力证明。简历上写「主导过亿级流量系统」——
可能真实，也可能包装；简历上**没写** Kubernetes——不代表不会，只代表**没有证据**。

所以 HR 侧要的不是「一个分数」，而是「**该问什么**」：

```
匹配层发现缺口（L1）  →  归因到能力维度（L2）  →  转成验证问题（L3）
  「缺 Kubernetes」        「交付运维能力，层级 3」   「你们的部署流程是怎样的？
                                                    自己搭集群还是用托管服务？」
```

### 六个分区

| 区 | 内容 |
|---|---|
| ① 匹配总览 | 复用 match-agent 的三段式分数 |
| ② 能力维度覆盖 | 每个维度的「要求层级 vs 已有证据」 |
| ③ 证据缺口与归因 | 逐条 gap：维度、层级差、归因说明、原文佐证 |
| ④ 面试地图 | 验证问题 + 意图 + 追问 + 判定信号 |
| ⑤ 追问记录 | 面试官录入回答 → 覆盖判定 + 理由 + 原文引用 |
| ⑥ 免责与审计 | 由**代码拼接**的声明 |

### 三层结构

| 层 | 名称 | 产出 | 在哪 |
|---|---|---|---|
| L1 | 证据对齐 | 事实差异（缺什么技能） | `match-agent`（既有） |
| L2 | 语义归因 | 背后真正缺的能力维度 | `interview-agent/app/hr/attribution.py` |
| L3 | 行为验证 | 用什么问题能验出来 | `interview-agent/app/hr/verification.py` |

### 三条硬约束（写在代码里，不是写在文档里）

1. **判定枚举只有三个值**：`demonstrated` / `adjacent` / `not_evidenced`。
   注意 `not_evidenced` 的意思是「**本次提问未获得证据**」，
   **不是**「候选人不具备该能力」—— 简历没写 ≠ 不会。
   枚举白名单在 `compliance.py`，运行期有断言兜底，另有禁用词扫描脚本。
2. **免责声明由代码拼接**：不让 LLM 生成，防止模型漏写。
3. **每条结论都可溯源**：模型引用的原文必须能在输入里定位，
   定位不到就**丢弃该结论**（`verify_evidence_ref`）。

### 确定性内核不交给 LLM

分数、维度、层级、判定枚举、问题结构、禁用问法判定 —— 全部由规则决定；
LLM 只做**文案润色**，且润色结果要过代码 linter，通不过就保留规则版本。

这样「禁用问法率 = 0」是结构性保证，不是概率。

### 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/hr/insight` | 发起报告（异步），返回 `insightId` + `taskId` |
| GET | `/api/v1/hr/insight/{insightId}` | 报告详情（含逐 gap 的验证问题） |
| GET | `/api/v1/hr/insight/list` | 我的报告列表 |
| POST | `/api/v1/hr/verify/answer` | 提交面试官录入的回答 → 覆盖判定 |
| GET | `/api/v1/hr/verify/records/{insightId}` | 追问记录（人工决策留痕） |

新增的 taskType：`hr_insight` / `jd_calibrate` / `hr_verify`。
HR 侧复用 `interview-agent` 的 recruiter 模式，**不新增 Agent 进程**。

### 评估

```bash
python scripts/eval_hr_insight.py
```

输出四个指标：维度归因准确率、问题覆盖率、禁用问法率、引用可验证率。

> ⚠ 当前数据集是**合成脱敏**样本，标注也是我们自己写的。
> 指标全绿只说明**规则与我们的标注一致**，不代表真实场景准确率。
> 这个脚本当前的价值是**回归基线**：规则一改，指标掉下来就能发现。
> 要得到有业务意义的数字，需要接入 40-60 组真实人工标注样本。

### 架构决策记录

`docs/adr/` 下 6 条，每条含「背景 → 备选 → 决策 → 代价 → 什么条件下推翻它」：

| ADR | 主题 |
|---|---|
| [ADR-001](./docs/adr/ADR-001-deterministic-core-vs-llm.md) | 确定性内核与 LLM 表现层分离 |
| [ADR-002](./docs/adr/ADR-002-evidence-verification-as-gate.md) | 引用校验作为硬门槛 |
| [ADR-003](./docs/adr/ADR-003-decision-support-not-decision-maker.md) | 产品形态：decision support 而非 decision maker |
| [ADR-004](./docs/adr/ADR-004-verdict-enum-three-values.md) | 判定枚举三值，禁用「不符合/淘汰」类措辞 |
| [ADR-005](./docs/adr/ADR-005-capability-taxonomy-excludes-demographics.md) | 能力维度 taxonomy 不含人口学特征 |
| [ADR-006](./docs/adr/ADR-006-defer-langgraph-refactor.md) | 本期不改造 LangGraph 图结构 |

### 合规边界（请如实理解）

- 上述偏见缓解措施是**内部自检**，
  **不等于**通过 NYC Local Law 144 要求的**独立第三方偏见审计**。
- 法规状态核实于 2026-10-22，实施前请复核（EU AI Act 的合规期限
  因 2026 Digital Omnibus 推迟至 2027-12-02）。

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
