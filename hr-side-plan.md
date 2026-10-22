# CareerPilot · HR 侧能力扩展 · 项目计划书

> **文档用途**：本文件是**独立可执行的实施依据**。任何新会话（AI 助手或人）读完本文件即可开始工作，不需要额外上下文。
>
> ⚠️ **并行开发提示**：`android/` 目录正被另一会话开发（分支 `feature/android-app`）。本期的**文件所有权划分、冻结契约清单、改动铁律**见 [§0.6](#06-与-android-会话并行的协作规则开工前必读)——**开工前务必先读这一节**。
>
> **状态**：待实施 ｜ **最后更新**：2026-10-22 ｜ **基线 commit**：`f92a11a`（工作区含未提交改动）
>
> **配套文档**（同仓库，实施前建议通读）
> | 文档 | 作用 |
> |---|---|
> | `docs/API-FRONTEND.md` | 前后端接口全景，本文所有接口约定的依据 |
> | `docs/API-CONTRACT.md` | 内部对齐基线，三方共同遵守 |
> | `README.md` | 架构总览、本地启动方式 |

---

## 目录

- [0. 给执行者：五分钟上手](#0-给执行者五分钟上手)
- [1. 背景与产品判断](#1-背景与产品判断)
- [2. 现状基线（必读）](#2-现状基线必读)
- [3. 范围定义](#3-范围定义)
- [4. 功能设计](#4-功能设计)
- [5. 技术设计](#5-技术设计)
- [6. 数据模型与 API 契约](#6-数据模型与-api-契约)
- [7. 里程碑与验收标准](#7-里程碑与验收标准)
- [8. 质量保障](#8-质量保障)
- [9. 风险与对策](#9-风险与对策)
- [10. 面试叙事](#10-面试叙事)
- [11. 附录](#11-附录)

---

## 0. 给执行者：五分钟上手

### 0.1 这是什么

CareerPilot 是一个**求职侧**的多智能体平台：解析 JD → 诊断简历 → 匹配打分 → 优化润色 → 模拟面试 → 求职信 → 职业规划。

**本计划要做的**：把同一套 AI 引擎**翻转一个视角**，服务**招聘方（HR）**——不是做招聘平台，而是做**招聘方的决策支持工具**，帮 HR 补信息差。

### 0.2 你要做的事（一句话）

> **把"匹配层发现的证据缺失（gap）"，自动转化为"面试层的验证问题（问题 → 追问 → 判定）"**，让 HR 拿到一张针对具体简历的追问地图，而不是一个分数。

### 0.3 开工前必做

```powershell
# 1. 确认全栈能跑起来（17 个进程）
powershell -File scripts\local\start-all.ps1
powershell -File scripts\local\status.ps1

# 2. 确认契约检查通过（应为退出码 0）
agents\agent-orchestrator\.venv\Scripts\python.exe scripts\verify_contract.py

# 3. 通读接口约定
#    docs/API-FRONTEND.md
```

### 0.4 新会话启动提示词（可直接复制粘贴）

```
我在 C:\Project\CareerPilot 项目上工作。请先读这两个文件建立上下文：
1. hr-side-plan.md  ← 本项目计划书（HR 侧能力扩展的实施依据）
2. docs/API-FRONTEND.md  ← 前后端接口全景

重要前提（务必遵守）：
- 仓库里有另一个会话正在并行开发 android/ 目录（分支 feature/android-app）。
  → 绝对不要碰 android/** 目录
  → 不要执行任何 git 写操作（不 add / commit / checkout / stash / branch）
  → 现有 /api/v1 契约被 Android 端消费，属于冻结契约，
    只允许新增端点、新增可选字段，禁止修改既有端点的请求/响应形状
  → 详细的文件所有权划分见计划书 §0.6
- 本期只做后端 + 网页，**不做 Android 端**

然后按计划书的「7. 里程碑与验收标准」从 M0 开始执行。
M0 是 P0 前置修复（前端丢 JD 数据），它必须先做完。
每完成一个里程碑：跑验收命令 → 贴出结果 → 确认后再进入下一个。
不要跳到 M2 及以后，LLM 层工程化（M1）是后面所有功能的地基。
所有代码改动遵循现有约定：Java 侧包结构/命名照抄同服务既有类，
Python 侧沿用现有 llm.py 的降级模式，前端沿用 web/src/api/index.ts 的封装风格。
```

### 0.5 全局约束（贯穿所有里程碑）

| # | 约束 | 原因 |
|---|---|---|
| C1 | **不新增 Java 微服务、不新增 Python Agent 进程** | 本期所有能力挂在现有 8 个 Agent + 编排层内 |
| C2 | **不删除或重命名任何现有对外接口** | 现有前端 26 个端点全部在用 |
| C3 | **确定性内核不交给 LLM** | LLM 只做语义归因与文案生成；分数、severity、判定枚举由规则决定 |
| C4 | **输出禁用词零容忍** | 合规红线，见 [§3.3](#33-合规红线硬约束) |
| C5 | **LLM 失败必须能降级** | 沿用 `match-agent/app/main.py` 的 `_polish_suggestions` 模式：LLM 挂了返回规则结果 |
| C6 | **每个结论必须可溯源** | 引用的原文片段必须能在输入文本中验证存在 |

### 0.6 与 Android 会话并行的协作规则（开工前必读）

**当前仓库状态（2026-10-22 核实）**

| 项 | 状态 |
|---|---|
| 当前分支 | `feature/android-app` |
| `android/` 目录 | **未跟踪，正在被另一个会话开发**（Kotlin + Jetpack Compose + OkHttp + kotlinx.serialization） |
| 工作区 | 大量未提交改动（Dockerfile 批量删除、`scripts/local/` 未跟踪等） |

> **结论：直接基于当前工作区改代码，不建新分支、不做 commit、不做任何 git 写操作。**
> 用户已明确：并行开发下 commit 会乱，**以现有代码为基础推进**。

#### 0.6.1 关键事实：Android 端是同一套 `/api/v1` 契约的下游消费者

Android 应用通过 `ApiClient.kt` 直连同一套后端（`http://10.0.2.2:8080/api/v1/`），并**完整复刻了前端的包络解析、401 自动刷新、SSE 流、multipart 上传**。

**因此：Android 正在消费现有契约。任何对现有端点的改动都会打断它的开发。**

#### 0.6.2 冻结契约清单 —— **禁止修改**

以下端点被 Android 直接调用，**请求形状、响应形状、字段名、错误码一律不动**：

| 类别 | 端点 |
|---|---|
| 认证 | `POST /api/v1/auth/login`、`POST /api/v1/auth/register`、`POST /api/v1/auth/logout`、`POST /api/v1/auth/refresh` |
| 简历 | `POST /api/v1/resume/upload`、`GET /api/v1/resume/list`、`GET /api/v1/resume/{id}`、`GET /api/v1/resume/{id}/ats-score`、`GET /api/v1/resume/{id}/versions`、`POST /api/v1/resume/{id}/reparse` |
| JD | `POST /api/v1/jd/upload`、`GET /api/v1/jd/list`、`GET /api/v1/jd/{id}`、`GET /api/v1/jd/{id}/parse/result`、`POST /api/v1/jd/{id}/reparse` |
| 匹配 | `POST /api/v1/match/evaluate`、`GET /api/v1/match/{recordId}`、`GET /api/v1/match/list` |
| AI 任务 | `POST /api/v1/ai-tasks`、`GET /api/v1/ai-tasks/{taskId}` |
| SSE | `GET /api/v1/ai-tasks/{taskId}/stream`（**事件名与 data 结构不能改**） |
| 包络 | `ApiResponse{code, message, data, traceId}` —— `code != 0` 视为失败；**`data` 为 null 会被 Android 判为失败**（`ApiClient.parseEnvelope`） |

#### 0.6.3 文件所有权划分

| 区域 | 归属 | 规则 |
|---|---|---|
| `android/**` | 🔴 **Android 会话** | **完全不碰** |
| `web/src/views/Optimize.vue`、`CoverLetter.vue`、`Interview.vue` | 🟡 **低冲突** | Android 不读 Vue。M0 要改这三个文件，**只加字段不改逻辑** |
| `web/src/api/index.ts`、`types/index.ts`、`router/index.ts`、`MainLayout.vue` | 🟡 **低冲突** | M5 只做**增量**（新增 `hrApi`、新增类型、新增路由），不改现有导出 |
| `agents/*/app/main.py`（Pydantic 模型） | 🟢 **安全** | Agent 是 Android 看不到的内部层，加**可选**字段即可 |
| `agents/agent-orchestrator/app/{graph,state}.py` | 🟢 **安全** | 新增 taskType 是纯增量 |
| `services/*/controller/**`（新 Controller） | 🟢 **安全** | 新建文件，不碰现有 Controller |
| `services/gateway-service/.../application.yml` | 🟡 **中冲突** | 只**追加**一条路由（`/api/v1/hr/**`），不动既有 5 条 |
| `db/migrations/` | 🟢 **安全** | 新建 `V2__hr_insight.sql`，不改 `V1` |
| **`scripts/verify_contract.py`** | 🔴 **高冲突** | Android 会话可能给它加 Android 检查 → **本期不改这个文件**，见 0.6.4 |
| **`docs/API-CONTRACT.md`** | 🔴 **高冲突** | 两边都可能写 → **本期不改**，改写到 `docs/adr/` |
| **`docs/API-FRONTEND.md`** | 🔴 **高冲突** | Android 会话可能补 Android 章节 → **本期不追加**，HR 侧接口写进 `docs/adr/` |

#### 0.6.4 冲突规避策略：M0 的检查放**独立脚本**

原计划 M0 要改 `scripts/verify_contract.py` 加第 5 项检查。**在并行开发下这有撞车风险。**

**改为**：新建独立脚本 `scripts/check_semantic_loss.py`，不碰 `verify_contract.py`。

```powershell
# 独立运行，退出码语义与 verify_contract.py 一致
python scripts\check_semantic_loss.py
```

- 复用 `verify_contract.py` 里的解析思路（AST 读 Pydantic 模型 + 正则抓前端 input），**但独立实现**
- 独立文件 → 两个会话可以各自演进，互不影响
- 后续若 Android 会话也想合并检查，由**单一方**在合适时机合并，本期不做

#### 0.6.5 本期**不做** Android 端

用户已明确：**企业端（HR 侧）只需要后端 + 网页**。

因此：
- ❌ 不做 Android HR 页面
- ❌ 不改 `android/**` 任何文件
- ❌ 不在 `ApiEnvelope` / `Models.kt` 里加 HR 相关模型

但**接口设计上要为将来留门**：`/api/v1/hr/**` 的响应结构保持与现有端点一致的包络格式（`code/message/data/traceId`），`data` **必须非 null**，这样将来加 Android 端时无需改后端。

#### 0.6.6 改动原则（本期铁律）

| # | 原则 | 落地 |
|---|---|---|
| P1 | **纯增量** | 只新增端点、新增可选字段、新增 taskType；不改既有端点的请求/响应形状 |
| P2 | **不删不改既有字段语义** | 现有字段的类型和含义不动；新字段一律可选 |
| P3 | **不改热文件** | 0.6.3 里标 🔴 的文件本期不碰，需要的说明写进 `docs/adr/` |
| P4 | **不执行 git 写操作** | 不 add / commit / checkout / stash / branch |
| P5 | **改完必跑既有验证** | `verify_contract.py` 退出码必须仍为 0（不回归 Android 依赖的现有契约） |
| P6 | **新增自己的验证脚本** | 语义丢失检查、禁用词检查各自独立成脚本，不侵入现有脚本 |

---

## 1. 背景与产品判断

### 1.1 为什么值得做 HR 侧

**核心洞察：简历是自述材料，不是能力证明。**

- 简历上写"主导过亿级流量系统"——可能真实，也可能是包装
- 简历上**没写**"有 Kubernetes 经验"——不代表不会，只代表**没有证据**

这正是「初筛」与「真正的分析」的分界线：

| | 初筛（现状） | 真正的分析（目标） |
|---|---|---|
| 回答的问题 | 简历里**有没有** JD 要的词 | 这个人**到底会不会**，以及**怎么验证** |
| 本质 | 特征匹配（等同 ATS） | 能力建模 + 证据分级 + 验证设计 |
| 产出 | 一个分数 / 通过-不通过 | 能力画像 + **证据缺口 + 验证方案** |
| 盲区 | 简历没写 = 不具备？（**不一定**） | — |

**推论：HR 侧"模拟面试"不是硬加的功能，是逻辑必然。** 一份自述材料无法判断适任性，能判断的只有行为数据；行为数据的自动化获取方式，在当前技术条件下只有对话式追问。

### 1.2 双边对称性——本项目的差异化所在

| 求职者侧（**已有**） | 招聘方侧（**本期新增**） | 复用引擎 |
|---|---|---|
| 我缺什么（Gap List） | 他缺什么、风险在哪 | **同一个 match 引擎** |
| 我该往哪准备 | 该招什么样的人 | 同一引擎反向 |
| 模拟面试**自测** | 模拟面试**验人** | 同一个 interview 引擎 |
| 职业规划 | JD 校准 | 同一个 career 引擎 |

**本项目不是两套产品，是一个产品的两面。**

- BOSS 直聘类平台：**只有候选人侧数据**
- Moka / 北森 / Workday：**只有企业侧数据**
- CareerPilot：**候选人本来就在用，HR 侧看到的是同一套结论**

**数据飞轮**：候选人用得越多 → 越多真实 (简历, JD) 配对数据 → gap→验证问题映射越准 → HR 侧价值越高 → 反过来吸引更多候选人。通用 ATS 复制不了这一点。

### 1.3 三层分析模型（本期工作的坐标系）

| 层 | 名称 | 产出 | 现状 |
|---|---|---|---|
| **L1** | 证据对齐 | 事实差异（缺什么技能、缺多少年限） | ✅ 已有：`match-agent` 的 `gap_list` |
| **L2** | 语义归因 | 这个 gap 背后**真正缺的能力维度**是什么 | ❌ 缺失，本期 F1 |
| **L3** | 行为验证 | 用什么问题能把这个维度验出来 | ❌ 缺失，本期 F2/F3 |

**本期工作的本质：把 L1 的输出接上 L3 的设计，中间用 L2 补一层语义。**

---

## 2. 现状基线（必读）

> ⚠️ 以下为**已核实**的事实（逐文件读源码，基线 commit `f92a11a`）。实施前请抽样复核。

### 2.1 运行拓扑

```
浏览器 ──▶ Vite :3000 (/api 代理) 或 Nginx :8088
              │
              ▼
    Spring Cloud Gateway :8080  ── 校验 JWT → 注入 X-User-Id
              │
   ┌──────────┼───────────┬───────────┬──────────────────┐
   ▼          ▼           ▼           ▼                  ▼
user-center resume-    jd-service  match-service   ai-task-scheduler
  :8081     service     :8083        :8084            :8085
                         :8082                            │
                                    线程池异步 ──▶ agent-orchestrator :8001
                                                        │ LangGraph 路由
                                                        ▼
                                              8 个 Python Agent :8001-8008
                                                        │
                                    HTTP 回调 ──▶ /internal/*-callback ──▶ 写回业务表
```

**基础设施**：MySQL 8（项目专用实例 **3307**，库 `careerpilot`，20 张表）、Redis 7（6379，**仅用于 refreshToken 黑名单**）、本地磁盘（简历原件，`STORAGE_ROOT`）。

### 2.2 关键文件地图

| 关注点 | 文件 | 说明 |
|---|---|---|
| **匹配打分** | `agents/match-agent/app/matcher.py` | TF-IDF + 技能词表 + 业务对齐，三段加权 |
| **匹配词表** | `agents/match-agent/app/knowledge_base.py` | `SKILL_LEXICON` / `ALIAS_INDEX` / `CERT_ALIASES` / `SUBSUMED_BY` / `EDUCATION_*` / `METRIC_KEYWORDS` |
| **打分模型** | `agents/match-agent/app/main.py` | `MatchRequest` / `MatchResult` / `MatchGap` |
| **LLM 降级范式** | `agents/*/app/llm.py`（8 份） | `complete()` 永不抛异常，失败返回 `None` |
| **LLM 降级调用示例** | `agents/match-agent/app/main.py:60` | `_polish_suggestions()`：只润色文案、条数校验、失败回退规则 |
| **编排图** | `agents/agent-orchestrator/app/graph.py` | `build_graph()` / `should_continue()` / `NODE_HANDLERS` |
| **编排状态** | `agents/agent-orchestrator/app/state.py` | `AgentState`（TypedDict, total=False）、`RESULT_KEYS`、`AGENT_NAMES` |
| **HTTP 客户端** | `agents/agent-orchestrator/app/http_client.py` | `post_json()` / `AgentError` |
| **面试引擎** | `agents/interview-agent/app/interviewer.py` | STAR 检测、追问、评分 |
| **任务调度** | `services/ai-task-scheduler/.../service/AiTaskService.java` | 建任务、线程池执行、SSE、回调、审计 |
| **Agent 地址配置** | `services/ai-task-scheduler/.../config/AgentProperties.java` | `agent-names` 映射、`callbacks` 映射 |
| **网关路由** | `services/gateway-service/src/main/resources/application.yml` | 5 条路由 + CORS + SSE 超时 |
| **网关鉴权** | `services/gateway-service/.../filter/JwtAuthFilter.java` | 白名单、剥 `X-User-Id`、拦 `/internal/**` |
| **前端 API 层** | `web/src/api/index.ts` | `authApi` / `userApi` / `resumeApi` / `jdApi` / `matchApi` / `aiTaskApi` |
| **前端请求层** | `web/src/api/request.ts` | baseURL `/api/v1`、30s 超时、401 自动刷新重试 |
| **SSE 客户端** | `web/src/api/sse.ts` | fetch + ReadableStream 手工解析 SSE 帧 |
| **AI 任务轮询** | `web/src/composables/useTaskPolling.ts` | 间隔 1500ms / 超时 60000ms / SSE 失败降级轮询 |
| **契约检查** | `scripts/verify_contract.py` | 语法 / Pydantic 模型 / 前端→Agent 字段 / 契约文档 |
| **本地启动** | `scripts/local/*.ps1` | `start-all.ps1` / `status.ps1` / `stop-all.ps1` 等 11 个 |

### 2.3 已有能力清单（直接可复用）

| 能力 | 位置 | 复用方式 |
|---|---|---|
| 技能规范化（别名 + 包含关系） | `matcher.py` 的 `resolve_jd_skills()` | 归因层识别技能维度时直接调用 |
| gap 生成 + severity 分级 | `matcher.py` 的 `build_gap_list()` | **L1 输出，无需重写** |
| gap 类型枚举 | `missing_skill` / `weak_metric` / `missing_cert` | 归因层按类型分派 |
| STAR 四要素检测 | `interviewer.py:80` `detect_star_coverage()` | 判定层的"证据结构完整度"参考 |
| 追问策略 | `interviewer.py:107` `weakest_missing_element()` | 追问树生成的起点 |
| 题目类型决策 | `interviewer.py:198` `_choose_type()` | 问题类型分派 |
| LLM 降级范式 | `llm.py` + `match-agent/main.py:60` | **所有新 LLM 调用照抄** |
| 任务调度 + SSE + 审计 | `AiTaskService.java` | 新 taskType 无需改动 |
| 溯源纪律 | `career-advisor-agent` 的 `doc_ids` | **归因层与验证层照抄这个模式** |
| 契约一致性检查 | `scripts/verify_contract.py` | 扩展一条"语义丢失"检查 |

### 2.4 已核实的缺陷（实施前必须知道）

#### D1（P0，功能级）前端传的 JD 信息在 Agent 侧被静默丢弃

**链路**：前端 `input` → 调度器存库 → 编排层原样透传 → **Agent 的 Pydantic 模型未声明 → `extra='ignore'` 静默丢弃**。

全项目 43 个 `.py` 文件中**零处 `model_config` 覆盖**，Pydantic v2 默认即 `extra='ignore'`。

| 功能 | 前端传 | Agent 声明 | 实际后果 |
|---|---|---|---|
| 简历优化 | `jd_id` / `jd_text` | 仅 `bullet`/`jd_skills`/`tone` | `jd_skills` 恒 `[]` → **`injected_skills` 永远为空**，"针对该 JD 优化"未实现 |
| 求职信 | `job_title`/`company_name`/`tone`/`jd_id`/`jd_text` | + `jd_skills`/`resume_highlights` | 两者恒 `[]` → 技能段落走 fallback `"核心工程能力"`，**生成的是通用模板信** |
| 模拟面试 | 同上 + `resume_highlights`(实为简历标题) | `jd_skills` 未传 | 出题走 fallback `"核心技术方案"`；`evaluate_answer` 的 `skill_hits` 恒空，**JD 技能命中检查失效** |

**为什么 `verify_contract.py` 没拦住**：其 `ALLOWED_EXTRA_KEYS` 恰好放行了 `jd_id`/`jd_text`/`jd_skills`——脚本只校验"前端传的键是否是 Agent 接受的字段"，**查不出"传了但没人消费"的语义丢失**。

**实测**：当前 `verify_contract.py` 退出码 0、4 项全绿。**绿灯 ≠ 契约正确。**

> **D1 是 M3 的前置**：验证计划生成必须能拿到真实 JD 技能，否则 gap→问题的映射没有素材。

#### D2（P1）`api-spec.md` 描述的是未实现的系统

仓库根目录 `api-spec.md`（1467 行）端口写 `:8001/:9001/:9002`，并包含短信验证码登录、头像上传、向量混合检索、WebSocket 通道、求职信编辑导出、订单支付、管理后台等**均不存在**的功能。**不要给面试官看。** 建议移到 `docs/archive/` 并加标注。

#### D3（P1）网关 4 条死路由

`gateway-service/application.yml:38` 把 `/api/v1/optimize|interview|cover-letter|career-plan/**` 路由到 8085，但 8085 只有 `/api/v1/ai-tasks` 一个 Controller，前端也从不调这四个前缀（它们只是前端路由路径）→ **必然 404**。

---

## 3. 范围定义

### 3.1 本期做（In Scope）

| # | 功能 | 产出 |
|---|---|---|
| **F0** | 修复 D1（JD 数据丢失） | 前端 4 处补传 `jd_skills` + 契约检查加"语义丢失"规则 |
| **F1** | **归因层**：gap → 能力维度 | 每个 gap 附带 `dimension` / `required_level` / `evidence_ref` |
| **F2** | **验证计划生成**：gap → 结构化面试问题 | 问题 + 意图 + 追问 + 判定信号 |
| **F3** | **追问树与判定** | 回答 → 覆盖度判定（**枚举三值**） |
| **F4** | **JD 校准**（反向） | JD 隐含能力模型 / 硬门槛 / 过度筛选信号 |
| **F5** | **HR 视图页面** | 输入 JD + 简历 → 完整 HR 报告 |

### 3.2 本期不做（Out of Scope）—— 越界即失败

| 不做 | 原因 |
|---|---|
| ❌ **做 Android 端（HR 页面）** | 用户已明确：企业端**只需后端 + 网页**。且 `android/` 正被另一会话开发，不碰（§0.6.5） |
| ❌ 输出"建议录用 / 不录用 / 不适合 / 淘汰" | **合规红线**，见 §3.3 |
| ❌ 视频/语音面试的**情绪识别** | EU AI Act Art. 5 **明确禁止**工作场所情绪识别（2025-02-02 生效） |
| ❌ 真实企业账号体系、多租户、计费、SSO | 范围爆炸，与技术验证无关 |
| ❌ 完整 ATS（人才库、流水线、offer 管理） | 与本项目差异化无关 |
| ❌ 招聘方自主上传简历的存储/隐私基建 | 复用现有 `resume-service` 即可，不新建 |
| ❌ 偏见审计的正式第三方审计 | 工程量与资质门槛高；本期只做**内部自检**（§8.3） |
| ❌ 把 LangGraph 改造成真多 Agent 协作图 | 见 §5.3 的说明，**本期不需要** |
| ❌ 引入向量库 / embedding | 本期 L1/L2 都不需要语义检索 |

### 3.3 合规红线（硬约束）

> 法规状态核实于 2026-10-22。实施前请复核是否变动。

| 法域 | 要求 | 本期影响 |
|---|---|---|
| **EU AI Act** | 招聘/简历筛选属 Annex III 第 4 类**高风险**。**2026 Digital Omnibus 将合规期限从 2026-08-02 推迟至 2027-12-02**。提供者需技术文档(Art 11)、质量体系(Art 17)、EU AI 数据库登记(Art 71)、CE 标志(Art 48)；部署者需人工监督(Art 14/26)、记录输入输出、告知员工。**罚款最高 €3500 万或全球营收 7%** | 设计成 **decision support, not decision maker** |
| **EU AI Act Art. 5** | **禁止工作场所情绪识别**（2025-02-02 已生效） | 本期不做语音/视频，天然规避 |
| **NYC Local Law 144** | 年度**独立偏见审计** + 公开审计摘要 + 使用前**至少 10 天**通知候选人 | 输出不构成 AEDT 决策，不触发 |
| **Illinois AIVIA** | 告知 + 解释 + **取得同意** + 候选人可要求删除 + 年度偏见审计 | 本期无视频分析，暂不适用 |
| **EEOC** | disparate impact 责任 | 见 §5.6 偏见缓解 |

**三条设计约束（必须落到代码里，不能只写在文档里）：**

1. **R1 输出措辞**：所有输出字段的枚举值**不得**包含"录用/淘汰/不适合/不通过"。判定只用三值：`demonstrated` / `adjacent` / `not_evidenced`。
2. **R2 人工决策**：报告结尾必须显式声明"本报告为辅助建议，最终判断由招聘方作出"，且该声明**由代码拼接**（不可由 LLM 生成，防止 LLM 漏写）。
3. **R3 溯源**：每个结论携带 `evidence_ref`（原文片段），**由代码验证该片段确实出现在输入文本中**，验证不通过则丢弃该结论。

---

## 4. 功能设计

### 4.1 F0 · 修复 JD 数据丢失（M0）

**改动点**

| 文件 | 改动 |
|---|---|
| `web/src/views/Optimize.vue` | `input` 补 `jd_skills`（从 `GET /jd/{id}/parse/result` 的 `skills[].name` 映射） |
| `web/src/views/CoverLetter.vue` | 同上 |
| `web/src/views/Interview.vue` | 同上（`buildContext()` 内统一补） |
| `web/src/views/CareerPlan.vue` | 无 JD 场景，不改 |
| `scripts/verify_contract.py` | 新增第 5 项检查：**前端传的每个 key 必须在目标 Agent 的 Pydantic 模型里有声明**（不在 `ALLOWED_EXTRA_KEYS` 白名单里的额外键一律判失败） |

**契约检查的新规则（要点）**

```
对每个 AI 页面 → 找出其 taskType → 定位 Agent → 解析该 Agent 的 Pydantic 请求模型字段
  → 前端 input 的 key 集合 减去 模型字段集合 减去 显式声明的辅助键
  → 非空 = 失败（说明存在"传了但 Agent 不消费"的语义丢失）
```

`ALLOWED_EXTRA_KEYS` 保留但**必须收窄**：只保留编排层会消费、但 Agent 不需要的路由性字段（`jd_id`、`round_no`、`phase` 等），并在代码注释里写明"这些键由编排层消费，故允许"。

### 4.2 F1 · 归因层（L2）

**目标**：把"缺 Kubernetes（gap_type=missing_skill, severity=4）"翻译成"**该岗位要求生产环境集群运维能力（L2: dimension=delivery, level=3），候选人简历证据仅覆盖应用层部署（L1: level=1）**"。

**能力维度taxonomy**（先小后大，够用即可）

```python
CAPABILITY_DIMENSIONS = {
    "delivery":      {"name": "交付与工程实践", "signals": ["ci/cd", "部署", "发布", "灰度", "流水线", "回滚"]},
    "scale":         {"name": "规模与稳定性",   "signals": ["qps", "并发", "容量", "性能", "延迟", "故障", "高可用"]},
    "architecture":  {"name": "架构与治理",     "signals": ["微服务", "服务治理", "拆分", "边界", "分布式", "限流", "熔断"]},
    "data":          {"name": "数据与存储",     "signals": ["mysql", "redis", "es", "索引", "分库", "缓存", "一致性"]},
    "domain":        {"name": "业务领域理解",   "signals": ["交易", "支付", "风控", "电商", "教育", "医疗", "招聘"]},
    "collaboration": {"name": "协作与影响力",   "signals": ["主导", "跨部门", "带人", "推动", "协同", "汇报"]},
}
```

**归因输入**：`gap`（来自 L1）+ `jd_text` + `resume_text` + `jd_skills`
**归因输出**：

```json
{
  "gap_ref": "gap#3",
  "dimension": "delivery",
  "dimension_name": "交付与工程实践",
  "required_level": 3,
  "evidence_level": 1,
  "gap_kind": "adjacent_experience",
  "explanation": "JD 要求能独立管理生产集群的交付能力；简历仅体现应用层部署，未见集群运维证据",
  "evidence_ref": "简历第2段：「负责服务的 CI/CD 流水线建设与日常发布」"
}
```

**实现分工（关键）**

| 部分 | 谁做 | 理由 |
|---|---|---|
| `dimension` 归类 | **规则**（技能类别 + gap_type + 关键词） | 确定性、可测试、可审计 |
| `required_level` 推断 | **规则**（JD 措辞强度词："主导/独立"=3，"参与/使用"=1） | 同上 |
| `explanation` 文案 | **LLM** | 需要自然语言归纳 |
| `evidence_ref` 抽取 | **LLM 产出 + 代码校验** | LLM 抽，原文校验 |

> **遵守 C3**：分类和分级永远由规则决定，LLM 只写文案。

### 4.3 F2 · 验证计划生成（L3）

**目标**：把归因后的 gap 转成**非诱导性**的面试问题。

**问题生成的硬规则（代码强制）**

| 规则 | 说明 | 违反示例 |
|---|---|---|
| **禁止二元问法** | 不得出现「会不会/熟悉/了解/掌握 + 技能名」 | ❌「你会 Kubernetes 吗？」 |
| **必须锚定场景** | 问题必须包含"怎么做/多少/什么场景"之一 | ✅「你们的部署流程是怎样的？自己搭集群还是用托管服务？」 |
| **不得暗示答案** | 不得在问题中预设候选人会/不会 | ❌「你没用过 K8s 对吧？那你怎么做部署的？」 |
| **不得泄露缺口** | 问题里不能出现"JD 要求但你简历没有"这类表述 | ❌「JD 要求 Kubernetes，你简历里没有，怎么回事？」 |

**按能力维度分派的提问框架**

```python
QUESTION_FRAMES = {
    "delivery":      "你们的 {skill} 是怎么从代码走到生产的？{probe}",
    "scale":         "{skill} 在你们那边承载了多少量级？出过什么问题？{probe}",
    "architecture":  "你们当初是怎么决定这么做的？{skill} 在架构里承担什么角色？{probe}",
    "data":          "{skill} 当时的数据量/访问模式是什么样的？{probe}",
    "domain":        "这个业务里最难的点在哪？和 {skill} 怎么结合的？{probe}",
    "collaboration": "这件事是你一个人做的还是跨团队？你负责了哪部分？{probe}",
}
```

**输出结构**

```json
{
  "id": "vq#1",
  "gap_ref": "gap#3",
  "dimension": "delivery",
  "question": "你们的部署流程是怎样的？自己搭集群还是用托管服务？",
  "intent": "区分「集群运维经验」与「仅应用层部署经验」",
  "probes": [
    { "question": "如果集群要升级或回滚，你们的流程是什么？", "trigger": "回答提到托管服务" },
    { "question": "你直接操作过集群配置吗？比如扩容、调度策略、节点排障？", "trigger": "回答含糊未提具体操作" }
  ],
  "signals": [
    { "verdict": "demonstrated", "evidence": "描述了自建集群的扩缩容、排障、版本升级的具体操作" },
    { "verdict": "adjacent",     "evidence": "只描述了使用托管 K8s 服务、写 YAML/Helm values，未涉及集群运维" },
    { "verdict": "not_evidenced", "evidence": "回答停留在'我们用云厂商的服务'，未展开任何本人职责" }
  ]
}
```

**顺序建议**：按 severity 倒序，severity=5（硬性条件）优先；同一 dimension 的问题聚在一起，避免面试官问得散。

### 4.4 F3 · 追问树与判定

**输入**：面试官录入的候选人回答（一段文本）
**输出**：

```json
{
  "vq_id": "vq#1",
  "coverage": "adjacent",
  "confidence": 0.72,
  "matched_signal_index": 1,
  "reason": "回答提到使用托管 K8s 并编写 YAML，属应用层经验；未见自建集群运维证据",
  "evidence_ref": "候选人回答：「我们用阿里云 ACK，我只负责写 YAML 和改 Helm values」"
}
```

**判定枚举（硬约束 R1）**：

| 值 | 含义 | 禁止的近义说法 |
|---|---|---|
| `demonstrated` | 有直接证据支持该能力达到要求层级 | ~~符合/通过/胜任~~ |
| `adjacent` | 有相邻经验，部分满足 | ~~基本符合/勉强~~ |
| `not_evidenced` | 本次提问未获得证据 | ~~不符合/不合格/淘汰~~ |

> **`not_evidenced` ≠ 不适合。** 这个措辞差别就是合规红线的具体体现，必须在代码常量、prompt、UI 文案、文档里四处一致。

**追问触发**：若首轮回答未命中任何 `signals`（或命中但 `confidence` < 阈值），按 `probes` 顺序追问最多 2 次。

**实现建议**：判定**用规则 + LLM 辅助**：
- 规则先做：关键词命中 `signals[].evidence` 中的特征词 → 命中则给高 confidence
- LLM 做：规则无法判定时的语义归纳，且**必须返回引用的回答片段**（代码校验片段在回答原文中）

### 4.5 F4 · JD 校准（反向）

**输入**：一份 JD 原文
**输出**：

```json
{
  "capability_model": [
    { "dimension": "delivery", "required_level": 3, "evidence_in_jd": "负责 CI/CD 体系建设" },
    { "dimension": "scale",    "required_level": 2, "evidence_in_jd": "支撑百万级日活" }
  ],
  "hard_gates": [
    { "item": "本科及以上", "type": "education", "one_vote_veto": true }
  ],
  "over_filtering_signals": [
    { "signal": "同时要求 5 年经验 + 3 年管理经验，但职位名未体现管理职责", "risk": "screening_out" }
  ],
  "vague_phrases": [
    { "phrase": "熟悉分布式", "why_vague": "未说明是哪一层：数据一致性 / 服务编排 / 事务还是一致性哈希", "suggestion": "拆成具体项" }
  ]
}
```

**这是纯规则 + LLM 润色的活，难度最低、演示效果最好**，建议作为 M2 的并行任务。

### 4.6 F5 · HR 视图页面

**新页面**：`web/src/views/HrView.vue`，路由 `/hr-insight`

**输入**：
- JD：从已有 JD 列表选（`jdApi.list` / `jdApi.getById`）
- 简历：上传（复用 `resumeApi.upload`）或从已有选

**输出分区**

| 区 | 内容 |
|---|---|
| ① 匹配总览 | 三段分 + final score（复用 `matchApi`） |
| ② 能力维度覆盖 | 每个 dimension 的 required_level vs evidence_level 缺口条 |
| ③ Gap 清单 | 复用现有 gap 展示 + **新增「验证问题」按钮** |
| ④ 面试地图 | 验证问题列表 + 意图 + 追问 + 判定信号（可折叠） |
| ⑤ 追问记录 | 面试官录入回答 → 得到 `coverage` 判定 |
| ⑥ 免责与审计 | **由代码拼接**的声明 + 引用来源清单 |

---

## 5. 技术设计

### 5.1 总体架构

```
前端 HR 视图
   │  POST /api/v1/ai-tasks { taskType: "hr_insight", input: { jd_id, resume_id } }
   ▼
ai-task-scheduler :8085  ──▶ agent-orchestrator :8001
                                  │ LangGraph 路由（新增 taskType 节点）
                                  ▼
                    ┌─────────────────────────────┐
                    │  hr-insight-agent (新增逻辑)  │
                    │  或复用 3 个既有 Agent 组合     │
                    └─────────────────────────────┘
                           │  ① match-agent  :8004  → gap_list（L1，已有）
                           │  ② 归因（F1）           → dimension（L2，新增）
                           │  ③ 验证计划（F2）        → verification_plan（L3，新增）
                           ▼
                        落 t_ai_task.output_payload
                                  │
                    回调 ──▶ match-service（分数回写）
```

**编排层是否要改？—— 本期不需要。**

现有 `should_continue()` 是"taskType → 单节点"映射，新增一个 `hr_insight` taskType + 一个节点即可，**不动图结构**。真多 Agent 协作图（fan-out / 反思循环）是 §11 的延伸项，不是本期前提。

> 理由：先让功能跑通，把"协作图"作为**重构**而不是**前置依赖**，避免编排层成为进度瓶颈。

### 5.2 新增 taskType

| taskType | Agent 归属 | 回调 | 说明 |
|---|---|---|---|
| `hr_insight` | 新增节点，调用 :8004 + 新归因/验证逻辑 | ❌（结果留 `t_ai_task`） | 生成完整 HR 报告 |
| `jd_calibrate` | 新增节点 | ❌ | JD 校准报告 |

**改动点**

```python
# agents/agent-orchestrator/app/state.py
RESULT_KEYS["hr_insight"] = "hr_insight_result"
RESULT_KEYS["jd_calibrate"] = "jd_calibrate_result"
AGENT_NAMES["hr_insight"] = "hr-insight-agent"
AGENT_NAMES["jd_calibrate"] = "jd-calibrate-agent"

# agents/agent-orchestrator/app/graph.py
NODE_HANDLERS += (
    ("hr_insight", "hr_insight", call_hr_insight),
    ("jd_calibrate", "jd_calibrate", call_jd_calibrate),
)

# services/ai-task-scheduler/.../config/AgentProperties.java  (application.yml)
agent-names.hr_insight: hr-insight-agent
agent-names.jd_calibrate: jd-calibrate-agent
```

**Agent 进程归属决策**

| 方案 | 说明 | 建议 |
|---|---|---|
| **A. 新建独立 Agent 进程 :8009** | 职责最清晰，符合现有模式 | ❌ 违反 C1（不新增进程） |
| **B. 逻辑放进 `match-agent` :8004** | 复用 L1 产出，少一次跨进程调用 | ⚠️ match-agent 职责变重 |
| **C. 逻辑放进 `interview-agent` :8006** | 复用题目生成、追问、判定结构 | ✅ **推荐**——验证计划本质就是"面试题生成" |
| **D. 逻辑放在编排层 :8001** | 编排层不该有业务逻辑 | ❌ 违背现有分层 |

**推荐 C**：`interview-agent` 新增 `mode=recruiter` 能力。理由：
- 已有 `QuestionRequest` / `EvaluateRequest` / `generate_question()` / `evaluate_answer()` / `weakest_missing_element()` 结构全部可复用
- 已有 `jd_skills` / `resume_highlights` 入参（本期才真正被喂上）
- 语义上「生成验证问题」就是「生成面试题」的一个 mode

**F1 归因放哪**：归因是 match 的下游、verify 的上游。建议放在 `interview-agent` 的 recruiter mode 里作为**第一步**（它需要 gap 作为输入），或放在 `match-agent` 作为可选的 `with_attribution` 参数。**建议放 interview-agent**，保持 match-agent 的输出契约（`MatchResult`）不变，避免影响现有调用方。

### 5.3 LLM 调用规范（M1 必做地基）

**现状**：每个 Agent 各有一份 `llm.py`，`complete()` 返回裸字符串，无结构化输出、无重试、无 token 计量。

**目标**：统一为一个共享模块（建议放 `agents/shared/llm_client.py`，或每个 Agent 内保持同名但从 shared 导入实现）。

```python
# 建议的接口
async def complete_json(
    system_prompt: str,
    user_prompt: str,
    schema: dict,              # JSON Schema
    max_retries: int = 2,
    timeout_s: int = 30,
) -> Optional[dict]:
    """
    结构化输出 + 校验 + 重试。
    任何失败返回 None，由调用方降级到规则实现。
    绝不抛异常（沿用现有约定）。
    """
```

**必须满足的 6 条**

| # | 要求 | 实现要点 |
|---|---|---|
| 1 | **结构化输出** | 请求体带 `response_format: {type: "json_schema", json_schema: {...}}`；若 OpenAI 兼容端点不支持，退化为"提示 JSON + 解析 + 校验" |
| 2 | **校验 + 重试** | 解析失败或字段缺失 → 指数退避重试（1s/2s/4s + 抖动），最多 N 次 |
| 3 | **超时** | 分阶段超时；httpx `timeout=Timeout(connect=5, read=timeout_s)` |
| 4 | **失败降级** | 返回 `None` → 调用方用规则实现。**沿用 `_polish_suggestions` 模式** |
| 5 | **token 计量** | 解析 `usage.prompt_tokens` / `completion_tokens`，写日志 + 落库到 `t_agent_invocation` |
| 6 | **模型与参数可配** | 沿用现有 `OPENAI_API_BASE` / `OPENAI_MODEL` / `OPENAI_TEMPERATURE`；**新增 `LLM_PROMPT_VERSION`** 便于 prompt 版本管理 |

**Prompt 设计原则**（写进代码注释）

1. **不喂原始简历全文给归因/验证**——只喂 gap 涉及的**相关片段**，降低幻觉与成本
2. **强制引用**：`evidence_ref` 必须是输入中**原样出现的连续片段**（代码会校验）
3. **禁止输出禁用词**：system prompt 里列出禁用词清单（§8.3）
4. **温度**：归因/判定用 0.1-0.3，生成问题用 0.5-0.7

### 5.4 可解释性与溯源（R3）

**引用校验算法**

```python
def verify_evidence_ref(ref: str, source_text: str, min_len: int = 8) -> bool:
    """
    校验 LLM 返回的 evidence_ref 确实出现在原始文本中。
    做法：去掉空白与标点后做子串匹配（容忍 LLM 微调标点）。
    不通过 → 该条结论整体丢弃（或整个输出降级）。
    """
```

**为什么这条特别重要**：它把"防幻觉"从"相信模型"变成"**可执行的代码断言**"。这是很好的面试素材——"我们不靠 prompt 祈祷，而是用代码验证模型引用的每个证据都真实存在于输入里"。

**可观测性**：所有 `evidence_ref` 校验失败的记录落日志 + 落 `t_agent_invocation`（可统计"LLM 幻觉率"）。

### 5.5 判定阈值与可调参数

```python
# 建议集中到 config，便于调参
HR_INSIGHT_CONFIG = {
    "evidence_min_len": 8,        # 引用最短长度
    "confidence_threshold": 0.6,  # 低于此值触发追问
    "max_probes": 2,              # 单题最多追问次数
    "max_questions": 12,          # 单次报告最多问题数
    "forbidden_terms": [          # 禁用词（代码强制 + linter）
        "建议录用", "建议不录用", "淘汰", "不适合", "不合格",
        "通过筛选", "未通过", "录用", "拒绝", "pass", "fail",
    ],
    "allowed_verdicts": ["demonstrated", "adjacent", "not_evidenced"],
}
```

### 5.6 偏见风险与缓解

**风险来源**：简历中性别、年龄、毕业年份、院校、籍贯与 JD 关键词存在统计相关；规则引擎按词表匹配会**放大**这些相关性（NYC LL144 / EEOC 的核心关切）。

**缓解措施（本期做）**

| # | 措施 | 落地位置 |
|---|---|---|
| B1 | **能力维度不直接对应人口学特征** | taxonomy 里没有性别/年龄/学校/籍贯维度 |
| B2 | **不把"无证据"等同于"不具备"** | 判定枚举强制三值，`not_evidenced` 措辞在代码常量里固化 |
| B3 | **毕业年份不作为能力信号** | 归因层输入过滤掉 `birthYear` / `graduationYear` / `school` / `major` |
| B4 | **保留人工决策 + 留痕** | 报告页展示"由人决策"，并记录 HR 的实际决策（可选） |
| B5 | **禁用词 linter** | CI + 运行时双检（§8.3） |
| B6 | **拒绝"毕业院校"作为 gap 维度** | 学历门槛仍按 JD 硬性条件处理（这是岗位要求不是偏见），但不作为"能力弱"的证据 |

> **说明**：B1-B6 是**内部自检**，不等于通过 NYC LL144 的正式独立偏见审计。文档里必须如实这么写。

### 5.7 前端改动

> **全部为增量**：只在现有文件里**新增**导出、新增类型、新增路由、**新增页面文件**。
> 不修改任何现有导出函数的签名或行为（§0.6.6 P1/P2）。

| 文件 | 改动 | 冲突风险 |
|---|---|---|
| `web/src/api/index.ts` | **新增** `hrApi`（`insight()` / `calibrate()` / `submitVerify()`），沿用现有 `aiTaskApi.create` 模式 | 低（只追加导出） |
| `web/src/types/index.ts` | **新增** `HrInsightReport` / `VerificationQuestion` / `VerificationVerdict` / `CapabilityDimension` 等类型 | 低（只追加类型） |
| `web/src/views/HrView.vue` | **新建**页面 | 无 |
| `web/src/router/index.ts` | **新增**路由 `/hr-insight` | 低（只追加路由项） |
| `web/src/layouts/MainLayout.vue` | **新增**菜单入口 | 低（只追加） |
| `web/src/views/Optimize.vue` 等 3 个 | **只加 `jd_skills` 字段，不动其余逻辑**（M0） | 低（Android 不读 Vue） |

**复用**：`useTaskPolling`（`useStream: true`）、`jdApi.list` / `resumeApi.list` / `resumeApi.upload`、`ECharts`（能力维度图）

---

## 6. 数据模型与 API 契约

### 6.1 新增表

```sql
-- ── HR 洞察报告 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_hr_insight` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `insight_id`      VARCHAR(32)  NOT NULL COMMENT '业务ID ins_xxx',
    `task_id`         VARCHAR(32)  DEFAULT NULL COMMENT '关联 t_ai_task.task_id',
    `user_id`         VARCHAR(32)  NOT NULL,
    `jd_id`           VARCHAR(32)  DEFAULT NULL COMMENT 't_jd.jd_id（业务ID）',
    `resume_id`       VARCHAR(32)  DEFAULT NULL COMMENT 't_resume.resume_id（业务ID）',
    `mode`            VARCHAR(16)  NOT NULL DEFAULT 'RECRUITER' COMMENT 'RECRUITER/CANDIDATE',
    `capability_json` JSON         DEFAULT NULL COMMENT '能力维度模型',
    `gap_plan_json`   JSON         DEFAULT NULL COMMENT 'gap → 验证问题映射',
    `report_json`     JSON         DEFAULT NULL COMMENT '完整报告',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_insight_id` (`insight_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_task` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'HR 侧洞察报告';

-- ── 面试追问记录 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `t_verification_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `record_id`     VARCHAR(32)  NOT NULL COMMENT '业务ID ver_xxx',
    `insight_id`    VARCHAR(32)  NOT NULL,
    `vq_id`         VARCHAR(32)  NOT NULL COMMENT 'VerificationQuestion.id',
    `round_no`      INT          NOT NULL DEFAULT 1,
    `question`      TEXT         DEFAULT NULL,
    `answer`        MEDIUMTEXT   DEFAULT NULL,
    `coverage`      VARCHAR(24)  DEFAULT NULL COMMENT 'demonstrated/adjacent/not_evidenced',
    `confidence`    DECIMAL(4,3) DEFAULT NULL,
    `reason`        TEXT         DEFAULT NULL,
    `evidence_ref`  TEXT         DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_id` (`record_id`),
    KEY `idx_insight` (`insight_id`),
    KEY `idx_vq` (`vq_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '验证追问记录';

-- ── t_agent_invocation 增列（LLM 可观测性）──────────────────
-- 注意：model_name 列已存在（VARCHAR 64），直接复用，不要重复添加
ALTER TABLE `t_agent_invocation`
    ADD COLUMN `prompt_version`  VARCHAR(32)  DEFAULT NULL COMMENT 'prompt 版本',
    ADD COLUMN `input_tokens`    INT          DEFAULT NULL,
    ADD COLUMN `output_tokens`   INT          DEFAULT NULL,
    ADD COLUMN `evidence_failed` INT          DEFAULT NULL COMMENT '引用校验失败计数';
```

> **注意**：
> ① 本期只"读取" `t_match_gap`，**不修改** `t_match_record` / `t_match_gap` 结构，保持向后兼容。
> ② `t_agent_invocation` 已有 `model_name`（模型名）、`duration_ms`、`retry_attempt`、`error_msg` 列，**直接复用，不要重复添加**。
> ③ 新增 DDL 建议放独立迁移文件 `db/migrations/V2__hr_insight.sql`，不要改 `V1__init_schema.sql`（保持"唯一事实来源"的单向演进）。

### 6.2 新增对外 API

| 方法 | 路径 | 服务 | 说明 |
|---|---|---|---|
| POST | `/api/v1/hr/insight` | match-service | 同步做取素材+建记录，异步跑报告生成，返回 `{ insightId, taskId, status }` |
| GET | `/api/v1/hr/insight/{insightId}` | match-service | 取报告（含逐 gap 的验证问题） |
| GET | `/api/v1/hr/insight/list` | match-service | 分页列出我的报告 |
| POST | `/api/v1/hr/calibrate` | match-service | JD 校准（异步任务） |
| POST | `/api/v1/hr/verify/answer` | match-service | 提交面试官录入的回答 → 返回 `coverage` 判定 |
| GET | `/api/v1/hr/verify/records/{insightId}` | match-service | 取追问记录 |

**网关路由**（追加到 `gateway-service/application.yml`）

```yaml
- id: match-service
  uri: http://${MATCH_SERVICE_HOST:localhost:8084}
  predicates:
    - Path=/api/v1/match/**,/api/v1/hr/**
```

**内部回调**（新增）

```
POST /internal/hr-insight-callback   → match-service
```

### 6.3 AI 任务契约

```jsonc
// POST /api/v1/ai-tasks
{
  "taskType": "hr_insight",
  "bizId": "ins_xxx",
  "input": {
    "jd_id": "jd_xxx",         // 业务ID
    "resume_id": "res_xxx",    // 业务ID
    "jd_text": "...",
    "resume_text": "...",
    "jd_skills": ["Java", "Kubernetes"],   // ← M0 修好后前端会带，这里也需带
    "max_questions": 12
  }
}
```

---

## 7. 里程碑与验收标准

> **每个里程碑必须独立可验收、独立可演示。** 顺序不可跳。

### M0 · 修复 D1（P0 前置）— 预计 0.5 天

| 项 | 内容 |
|---|---|
| 改动 | 3 个 Vue 页面**只加字段不改逻辑**补传 `jd_skills`；**新建独立脚本** `scripts/check_semantic_loss.py`（**不改** `verify_contract.py`，见 §0.6.4） |
| 验收 1 | `python scripts\verify_contract.py` 退出码 **仍为 0**（不回归 Android 依赖的现有契约，§0.6.2） |
| 验收 2 | `python scripts\check_semantic_loss.py` 退出码 **0** |
| 验收 3 | **人为删掉 `Optimize.vue` 里的 `jd_skills` 后重跑 `check_semantic_loss.py`，必须失败并明确指出"Optimize.vue 传了 jd_skills 但 resume-optimizer-agent 不接受"** |
| 验收 4 | 手动跑一次简历优化任务，`output.injected_skills` **非空**（含 JD 里的技能名） |
| 验收 5 | 手动跑一次求职信任务，正文包含真实 JD 技能，不再是 fallback `"核心工程能力"` |
| 验收 6 | 手动跑一次模拟面试，出题内容包含 JD 里的具体技能，不再是 fallback `"核心技术方案"` |
| 验收 7 | **`git status` 中 `scripts/verify_contract.py`、`docs/API-CONTRACT.md`、`docs/API-FRONTEND.md` 三个文件保持 Android 会话留下的原状**（未被本期改动） |

### M1 · LLM 层工程化底座 — 预计 1.5 天

| 项 | 内容 |
|---|---|
| 改动 | 共享 `llm_client`：`complete_json()`（结构化输出 + 校验 + 重试 + 超时 + token 计量 + 失败降级）；8 个 Agent 迁移；`t_agent_invocation` 加 5 列 |
| 验收 1 | `complete_json()` 传一个**故意不合法的 schema**，连续重试 N 次后返回 `None` 而**不抛异常** |
| 验收 2 | 指向一个**不存在的端口**，调用后 5s 内返回 `None`，不挂起 |
| 验收 3 | 成功调用一次后，`t_agent_invocation` 里有 `input_tokens` / `output_tokens` / `model_name` / `prompt_version` 的非空值 |
| 验收 4 | **不配 `OPENAI_API_KEY` 时，全系统仍然能跑通**（规则降级路径不回归） |
| 验收 5 | 现有 `verify_contract.py` 仍退出 0 |

### M2 · 归因层（F1）+ JD 校准（F4）— 预计 1.5 天

| 项 | 内容 |
|---|---|
| 改动 | `CAPABILITY_DIMENSIONS` + 规则归类 + LLM 归因文案 + 引用校验；`jd_calibrate` taskType + 节点 + 前端最小页面 |
| 验收 1 | 拿项目里**正在跑的那份 JD**（含 Kubernetes gap）跑归因，输出 `dimension=delivery`、`required_level` 与 `evidence_level` 有明确差值、`evidence_ref` **能在简历原文中定位到** |
| 验收 2 | 引用校验函数对**伪造的 ref** 返回 `False`（写单测） |
| 验收 3 | `jd_calibrate` 能识别出至少 2 条 `vague_phrases`（如"熟悉分布式"）和 1 条 `over_filtering_signals`（若该 JD 存在） |
| 验收 4 | 归因的 `dimension` / `required_level` **由规则决定**——用同一份输入跑 3 次结果完全一致（LLM 只影响 `explanation` 文案） |

### M3 · 验证计划生成（F2）— 预计 1.5 天

| 项 | 内容 |
|---|---|
| 改动 | `QUESTION_FRAMES` + 提问规则 + LLM 生成 + 禁用问法 linter；`hr_insight` taskType + 节点 + `t_hr_insight` 表 + 服务与回调 |
| 验收 1 | **每个 severity ≥ 4 的 gap 都至少产出一个验证问题**（覆盖率 100%） |
| 验收 2 | **禁用问法 linter 通过**：全量报告中不含「会不会 X」「熟悉 X」「了解 X」句式（脚本自动扫） |
| 验收 3 | 每个问题都有 `intent` + ≥1 个 `probes` + 覆盖全部三个 `verdict` 的 `signals` |
| 验收 4 | 报告中每个 `evidence_ref` 都能在原文中定位（引用校验 100% 通过） |
| 验收 5 | `POST /api/v1/hr/insight` 端到端可用，前端能看到报告 |

### M4 · 追问树与判定（F3）— 预计 1 天

| 项 | 内容 |
|---|---|
| 改动 | `POST /api/v1/hr/verify/answer`；规则命中 + LLM 辅助判定；`t_verification_record` 表 |
| 验收 1 | 判定输出**只可能出现** `demonstrated` / `adjacent` / `not_evidenced` 三种值（写单测穷举） |
| 验收 2 | **全量输出禁用词扫描 0 命中**（"录用""淘汰""不适合""不合格""通过筛选"等） |
| 验收 3 | 回答含糊（"还行吧""就是用了一下"）→ 判定为 `not_evidenced` 并**自动触发追问** |
| 验收 4 | 回答命中 `signals[].evidence` 特征词 → 判定 `demonstrated` 且 `confidence ≥ 0.7` |
| 验收 5 | 每次追问记录落 `t_verification_record`，含 `evidence_ref` 且可在回答原文中定位 |

### M5 · HR 视图页面（F5）— 预计 1 天

| 项 | 内容 |
|---|---|
| 改动 | `HrView.vue` + 路由 + 菜单 + `hrApi` + 类型定义；ECharts 能力维度图 |
| 验收 1 | 选 JD + 选简历 → 出完整报告（6 个分区齐全） |
| 验收 2 | 报告底部**免责与审计声明可见**，且经代码确认是**代码拼接**而非 LLM 生成 |
| 验收 3 | 面试官能录入回答并看到 `coverage` 判定 + 理由 + 引用片段 |
| 验收 4 | 移动端/窄屏不破版（Ant Design 栅格） |

### M6 · 端到端演示 + 文档 — 预计 1 天

| 项 | 内容 |
|---|---|
| 改动 | 一键演示脚本 + README 更新 + 简历文案 + `docs/adr/` 决策记录 |
| 验收 1 | `start-all.ps1` 一键起全栈，HR 报告链路端到端可演示（≤3 分钟） |
| 验收 2 | README 新增"HR 侧能力"章节 + **合规声明**（明确"辅助建议，非决策工具"） |
| 验收 3 | `docs/adr/` 至少 3 条决策记录（见 §10.3） |
| 验收 4 | 简历文案定稿（§10.1） |

---

## 8. 质量保障

### 8.1 测试策略

当前项目**零测试**（`agents/*/` 下只有第三方库自带的 test 文件）。本期新增功能应**从一开始就带测试**，不要重复"先写后补"的坑。

| 层级 | 范围 | 建议数量 | 工具 |
|---|---|---|---|
| 单元 | 能力维度映射、提问框架选择、禁用词检测、引用校验、判定枚举 | 30-50 | `pytest` |
| 契约 | 前端→Agent 字段一致性 | 扩展现有 `verify_contract.py` | 现有脚本 |
| 集成 | `hr_insight` 端到端（无 LLM 也能跑通） | 5-8 | `pytest` + `httpx` |
| Lint | 禁用词扫描（全仓 + 生成产物） | 1 个脚本 | 独立脚本 |

**新增测试目录约定**：

```
agents/interview-agent/tests/
  test_attribution.py
  test_verification.py
  test_evidence.py       # 引用校验
  test_forbidden.py      # 禁用词 linter
scripts/check_forbidden_terms.py   # 跨仓扫描，CI 用
```

### 8.2 Eval 集设计

**这是 AI 应用开发岗面试必问的"效果怎么评"，必须做。**

| 项 | 设计 |
|---|---|
| 样本量 | 40-60 组 (JD, 简历, 人工标注) |
| JD 来源 | 公开招聘信息（BOSS/拉勾/公司官网公开 JD），**脱敏后**使用 |
| 简历来源 | 公开示例简历（`resume` 仓库、各类模板），**合成 + 脱敏** |
| 标注维度 | ① 人工标注的 gap 集合 ② 每个 gap 的能力维度 ③ 每个 gap 期望的验证问题要点（3-5 个关键词，不要求逐字相同） |
| 指标 | **gap 召回率**（人工标 gap 被系统发现的比）<br>**维度归因准确率**<br>**验证问题覆盖率**（high-severity gap 获得问题的比）<br>**禁用问法率**（必须为 0）<br>**引用可验证率**（evidence_ref 通过校验的比，期望 100%） |
| 判定阈值 | gap 召回 ≥ 0.85；维度归因准确 ≥ 0.70；问题覆盖率 = 1.0；禁用问法 = 0；引用可验证 = 1.0 |

**Eval 脚本**：`scripts/eval_hr_insight.py`，输出 JSON + Markdown 报告，**可重复运行**（结果进版本库，作为回归基线）。

### 8.3 合规自检清单

**必须自动化，不能靠人肉检查。**

| # | 检查 | 实现 |
|---|---|---|
| C-1 | 输出中无禁用词 | `scripts/check_forbidden_terms.py`，扫 `t_hr_insight.report_json` + 生成的 Markdown 报告 + 前端文案常量 |
| C-2 | 判定值只在白名单内 | 单元测试穷举 + 运行时 assert |
| C-3 | 所有 `evidence_ref` 可验证 | 生成时即校验，未通过则丢弃结论 |
| C-4 | 不使用人口学特征 | 归因输入过滤 `birthYear`/`graduationYear`/`school`/`major`；写单测断言这些字段不进入 prompt |
| C-5 | 免责声明由代码拼接 | 单元测试断言报告尾部字符串来自代码常量而非模型输出 |
| C-6 | 人工决策留痕 | `t_verification_record` 记录每次判定，可追溯 |

> **在文档中如实声明**：以上为**内部自检**，**不等于**通过 NYC Local Law 144 要求的**独立第三方偏见审计**。

---

## 9. 风险与对策

| # | 风险 | 概率 | 影响 | 对策 |
|---|---|---|---|---|
| R1 | **范围失控**，做成了完整 B 端产品 | 高 | 高 | 严守 §3.2 Out of Scope；每里程碑独立验收；不新增服务/表之外的东西 |
| R2 | **LLM 幻觉**：编造简历里没有的经历 | 高 | 高 | 引用校验（§5.4）作为**硬门槛**，校验不过即丢弃；不信任"模型说它引用了" |
| R3 | **合规越界**：输出滑向"录用建议" | 中 | **极高** | 枚举值硬编码 + 禁用词 linter + 代码拼接免责声明（三重） |
| R4 | **偏见放大**：规则引擎按词表匹配放大统计歧视 | 中 | 高 | §5.6 六条缓解；不把无证据当不具备；能力维度不含人口学特征 |
| R5 | **无真实企业数据**，演示说服力不足 | 高 | 中 | 用公开 JD + 合成脱敏简历；eval 集兜底；**在文档中如实说明是合成数据** |
| R6 | **LLM 不可用**（无 key / 断网 / 限流） | 中 | 中 | M1 的降级机制；规则版归因 + 规则版问题模板必须**单独可用** |
| R7 | **与现有契约冲突** | 中 | 中 | 新增字段一律可选；不改 `MatchResult` 现有字段语义；契约检查加入 CI |
| R8 | **本地无 GPU**，本地模型跑不动 | 中 | 低 | 本期不要求本地模型；用 OpenAI 兼容端点；`llm.py` 协议已兼容 vLLM/Ollama |
| R9 | **与 §11 的协作图重构冲突** | 低 | 中 | 本期**不改图结构**；协作图是后续重构，届时新 taskType 可平滑迁移 |

---

## 10. 面试叙事

### 10.1 简历 bullet（候选）

> - 设计**双边 AI 决策支持**架构：同一匹配引擎同时服务求职者（Gap List 指导简历改进）与招聘方（基于 gap 的结构化面试验证地图）；**LLM 仅承担语义归因与追问生成，确定性内核（加权评分、severity 分级、判定枚举）保持可解释可审计**，规避自动化就业决策的合规风险
> - 基于「**简历是自述材料而非能力证明**」的产品判断，将匹配层的证据缺失（gap）转化为面试层的验证问题（问题 → 追问 → 判定三段式），实现从证据对齐（L1）到行为验证（L3）的跃迁
> - 实现**引用可验证**机制：模型返回的每条证据片段均由代码回原文校验，校验不通过即丢弃结论——把"防幻觉"从提示词约束变成可执行断言（幻觉率 0）
> - 建立 50 组标注 eval 集跟踪 gap 召回率、维度归因准确率与验证问题覆盖率，并接入**禁用词 linter**（输出滑向"录用建议"时自动拦截）作为合规自检

### 10.2 讲述脚本

**30 秒版**

> 我的求职平台和招聘方用的是**同一套引擎**，不是两个产品。求职侧输出 Gap List——我缺什么；招聘侧把同一份 Gap List **翻转成面试问题**。因为简历是自述材料，"没写"不等于"不会"，所以我把每个证据缺口设计成一个可验证的追问——问题、追问、判定信号三段式。LLM 只做语义归因和文案，分数和判定枚举都是确定性规则，所以每条结论都能溯源到原文，且不会滑向"建议录用"这种受监管的输出。

**2 分钟版（结构）**

1. **问题**（20s）：信息差在双边都存在；简历是自述不是证明
2. **架构**（40s）：同一引擎两个视角；L1 gap → L2 归因 → L3 验证；图示
3. **技术难点**（40s）：防幻觉用**代码校验引用**而不是 prompt；防越界用**枚举 + linter + 代码拼接声明**三重
4. **效果**（20s）：eval 集指标 + 合规自检

### 10.3 建议补的 ADR（架构决策记录）

放 `docs/adr/`，每条：背景 → 备选 → 决策 → 代价 → 什么条件下推翻它。

| ADR | 主题 |
|---|---|
| ADR-001 | 确定性内核与 LLM 表现层分离（分数/severity/判定枚举不交给模型） |
| ADR-002 | 引用校验作为硬门槛：模型引用的证据必须能在原文定位 |
| ADR-003 | 产品形态选择：decision support 而非 decision maker（附法规依据与时间点） |
| ADR-004 | 输出枚举三值 `demonstrated/adjacent/not_evidenced`，禁用"不符合/淘汰"类措辞 |
| ADR-005 | 能力维度 taxonomy 不含人口学特征（偏见缓解的模型层设计） |
| ADR-006 | 本期不改造 LangGraph 图结构（先功能后重构） |

> **ADR-003 / 004 / 005 是本项目最有价值的工程产出**——它们证明你在设计时就把"这个结论会被怎么质疑"考虑进去了。

---

## 11. 附录

### 11.1 术语表

| 术语 | 含义 |
|---|---|
| **JD** | Job Description，职位描述 |
| **JD 解析** | 把非结构化 JD 文本抽成结构化字段（技能、薪资、年限、学历、段落分类） |
| **Gap** | JD 要求与候选人现状之间的**事实差异**（L1 输出） |
| **severity** | gap 严重度 1-5；**硬性条件不满足固定为 5** |
| **归因（Attribution）** | 把 gap 映射到具体能力维度与要求层级（L2） |
| **能力维度（dimension）** | delivery / scale / architecture / data / domain / collaboration |
| **验证计划** | gap → 可验证的面试问题 + 追问 + 判定信号（L3） |
| **coverage** | 判定三值：`demonstrated` / `adjacent` / `not_evidenced` |
| **evidence_ref** | 指向输入原文的证据片段，由代码校验存在性 |
| **AEDT** | Automated Employment Decision Tool，自动化就业决策工具（合规术语） |
| **可溯源** | 每条结论都能指回输入中的具体原文 |

### 11.2 法规速查

| 法规 | 关键点 | 状态（2026-10-22 核实） |
|---|---|---|
| **EU AI Act Annex III(4)** | 招聘/简历筛选/人格测试/申请过滤 = 高风险 | 合规期限 **2027-12-02**（2026 Digital Omnibus 从 2026-08-02 推迟） |
| **EU AI Act Art. 5** | 禁止工作场所情绪识别 | **2025-02-02 已生效** |
| **EU AI Act Art. 11/14/17/26/71** | 技术文档 / 人类监督 / 质量体系 / 部署者义务 / EU 库登记 | 随 Annex III 期限 |
| **EU AI Act 罚则** | 最高 €3500 万或全球营收 7%（禁止类）；€1500 万或 3%（高风险义务） | — |
| **NYC Local Law 144** | 年度独立偏见审计 + 公开摘要 + 使用前 ≥10 天通知 | 生效中 |
| **Illinois AIVIA (820 ILCS 42)** | 告知 + 同意 + 删除权 + 年度偏见审计 | 生效中 |
| **Maryland AEIA** | 披露要求 | 生效中 |
| **Colorado SB 24-205** | 算法问责 | 立法进程 |
| **EEOC** | disparate impact 责任 + AI 招聘工具指引 | 执法中 |

> ⚠️ 法规会变动。实施前请复核；HR 视图页面与 README 必须带"本工具为辅助建议，最终判断由招聘方作出"的声明。

### 11.3 复用的现有代码位置索引

| 需要什么 | 去哪拿 |
|---|---|
| 技能规范化 / 别名 / 包含关系 | `agents/match-agent/app/knowledge_base.py` |
| gap 生成 + severity 分级 | `agents/match-agent/app/matcher.py` 的 `build_gap_list()` |
| 三段加权公式 | `agents/match-agent/app/matcher.py:34-36`（**权重是契约硬约束，不可改**） |
| LLM 降级范式 | `agents/match-agent/app/main.py:60` 的 `_polish_suggestions()` |
| STAR 检测 | `agents/interview-agent/app/interviewer.py:80` |
| 追问策略 | `agents/interview-agent/app/interviewer.py:107` |
| 题目类型决策 | `agents/interview-agent/app/interviewer.py:198` |
| 题目生成 | `agents/interview-agent/app/interviewer.py:211` `generate_question()` |
| 回答判定 | `agents/interview-agent/app/interviewer.py:282` `evaluate_answer()` |
| 编排图节点注册 | `agents/agent-orchestrator/app/graph.py` 的 `NODE_HANDLERS` / `build_graph()` |
| 编排状态扩展 | `agents/agent-orchestrator/app/state.py` 的 `RESULT_KEYS` / `AGENT_NAMES` |
| 任务调度 / SSE / 审计 | `services/ai-task-scheduler/.../AiTaskService.java` |
| taskType 注册 | `services/ai-task-scheduler/src/main/resources/application.yml` 的 `agent-names` |
| 回调注册 | 同上 `callbacks` |
| 网关路由 | `services/gateway-service/src/main/resources/application.yml` |
| 任务落库 + 线程池 | `services/ai-task-scheduler/.../service/AiTaskService.java` 的 `createTask()` |
| 回调分发 | 同上 `notifyCallback()` |
| 前端 API 封装 | `web/src/api/index.ts` |
| 前端任务跟踪 | `web/src/composables/useTaskPolling.ts` |
| 前端 SSE | `web/src/api/sse.ts` |
| 契约检查 | `scripts/verify_contract.py` |
| DB 迁移 | `db/migrations/V1__init_schema.sql`（新增表建议建 `V2__hr_insight.sql`） |

### 11.4 并行开发速查（打印版）

```
┌─ 冻结契约（Android 正在消费，禁止修改）─────────────────────┐
│  /api/v1/auth/{login,register,logout,refresh}              │
│  /api/v1/resume/{upload,list,{id},{id}/ats-score,          │
│                   {id}/versions,{id}/reparse}              │
│  /api/v1/jd/{upload,list,{id},{id}/parse/result,{id}/reparse}│
│  /api/v1/match/{evaluate,{recordId},list}                  │
│  /api/v1/ai-tasks  /api/v1/ai-tasks/{taskId}                │
│  /api/v1/ai-tasks/{taskId}/stream  ← 事件名与 data 结构不动  │
│  包络 {code,message,data,traceId}；data 为 null 会被判失败  │
└─────────────────────────────────────────────────────────────┘

┌─ 文件所有权 ────────────────────────────────────────────────┐
│  🔴 不碰：android/**                                        │
│  🔴 不碰（本期）：scripts/verify_contract.py                 │
│                    docs/API-CONTRACT.md                     │
│                    docs/API-FRONTEND.md                     │
│  🟡 只追加：web/src/api/index.ts、types/index.ts、           │
│              router/index.ts、MainLayout.vue                 │
│  🟢 安全：agents/**、services/*/controller/**（新建）、      │
│          db/migrations/V2__*.sql                            │
└─────────────────────────────────────────────────────────────┘

┌─ 铁律 ──────────────────────────────────────────────────────┐
│  P1 纯增量   P2 不删改既有字段   P3 不改热文件               │
│  P4 不做 git 写操作   P5 跑既有验证不回归   P6 新检查独立成脚本│
└─────────────────────────────────────────────────────────────┘
```

### 11.5 后续演进（本期不做，供规划）

| 阶段 | 内容 | 依赖 |
|---|---|---|
| **E1** | 协作图重构：match 链路改成"解析 ∥ 抽取 → 对齐 → 评分 → **反思循环**"，JD 只解析一次放进共享 state | 解决双路径丢数据的结构性问题 |
| **E2** | 真·多 Agent 协作：动态路由 + checkpoint + human-in-the-loop interrupt | E1 |
| **E3** | 混合检索 RAG：BM25 + dense + reranker，带 eval 证明优于纯 BM25 | 需引入向量库 |
| **E4** | 本地模型接入（vLLM/Ollama + Qwen）→ 面试现场不依赖外网 | 需 GPU 机器 |
| **E5** | 语音模拟面试（ASR + 流式 TTS）。⚠️ **绝对不做情绪识别**（EU AI Act Art. 5） | 需 E4 |
| **E6** | 多租户 / 权限 / 审计（真正产品化时） | 商业化时 |

---

## 12. 附：本文档的核对状态

本文档的事实性内容（架构、文件路径、接口、字段、缺陷 D1-D3）均来自对基线 commit `f92a11a` 的**逐文件读源码核对**，非从既有文档转述。

**法规状态核实于 2026-10-22**，来源为公开法规解读站点与行业分析，实施前请复核。

**实施中发现文档与实现不符时，以实现为准，并更新本文档与 `docs/API-CONTRACT.md`。**
