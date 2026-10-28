# 平台三端拆分（当前实现）

> 三端结构、角色边界与双入口已完成；HR 侧「候选人简历 → 岗位匹配 → 深度洞察」链路已接通。
> 管理端补齐了用户行为视图与 Agent / 模型配置页。
> 仍刻意不做：职位投递、站内沟通、全站人才库检索、企业租户与支付。

---

## 1. 端与账号模型

| 端 | 前端 | 后端入口 | 角色 |
|---|---|---|---|
| 求职者 | `web/`（与招聘方共用） | `/api/v1/**`（除 hr/admin） | `USER` |
| 招聘方 HR | `web/`（与求职者共用） | `/api/v1/hr/**` | `RECRUITER` |
| 平台运营 | `admin-web/`（独立） | `/api/v1/admin/**` | `ADMIN` |

**关键决策：角色是授权维度，不是账号类型。**
把求职者提升为 `RECRUITER` 后，原账号的简历、JD、匹配记录全部保留，同时多出 HR 工作台。
这样避免了「一个人只能是一种身份」带来的重复注册和数据割裂。

管理端不复制业务逻辑：它只做运营概览、用户与角色、平台配置、任务排查、服务健康、审计。
业务能力仍然留在 resume / jd / match / scheduler 各自的服务里。

---

## 1.1 三态首页

`/` 会根据登录态与角色渲染三种首页：

| 状态 | 首页内容 | 主入口 |
|---|---|---|
| 未登录 | 项目介绍 + 求职者/招聘方双入口 | 两个登录按钮 |
| 已登录 USER | 只介绍求职者能力、推荐下一步与工作台直达 | `/seeker/dashboard` |
| 已登录 RECRUITER | 只介绍招聘方能力、推荐下一步与工作台直达 | `/hr/dashboard` |

登录后首页还会读取该账号的简历 / JD / 匹配（招聘方额外读洞察）数量，给出「推荐下一步」。

## 2. 代码结构

```
CareerPilot/
├── web/                     用户端（官网 + 求职者 + 招聘方）
│   └── src/
│       ├── router/index.ts  路由 meta.roles 是唯一的权限声明处
│       ├── layouts/MainLayout.vue  菜单按角色渲染
│       └── views/
│           ├── Landing.vue   官网首页：能力介绍 + 求职者/招聘者双入口
│           ├── Login.vue     按 ?role=seeker|recruiter 切换入口与角色校验
│           ├── Dashboard/Resume/JdManage/...   /seeker/** 求职者
│           ├── HrView.vue                     /hr/insight 深度洞察
│           └── hr/
│               ├── HrDashboard.vue  招聘工作台
│               ├── HrResumes.vue    候选人简历（本地导入）
│               ├── HrJds.vue        岗位 JD（匹配基准）
│               └── HrMatch.vue      多简历 × 单 JD 批量匹配
│
├── admin-web/               管理端（独立前端，端口 3100）
│   └── src/
│       ├── router/index.ts
│       ├── layouts/AdminLayout.vue
│       └── views/
│           ├── Dashboard.vue   运营概览 + 14 天趋势 + 任务类型分布
│           ├── Users.vue       用户与角色（改角色 / 封禁）
│           ├── Activity.vue    用户使用概览 + 行为流水
│           ├── Agents.vue      LLM / 向量 / TTS / ASR / Agent 编排配置
│           ├── Configs.vue     平台配置原始 JSON（功能开关 / 品牌 / 合规）
│           ├── Services.vue    14 个进程的健康总览
│           ├── Tasks.vue       AI 任务排查
│           └── Audits.vue      管理操作审计
│
└── services/
    ├── user-center/          登录注册；JWT 带 role claim
    ├── admin-service/        ★ 新增，8086，只服务 ADMIN
    └── gateway-service/      入口鉴权 + 路由级授权
```

---

## 3. 鉴权链路

```
浏览器
  │ Authorization: Bearer <accessToken>（payload 内含 role）
  ▼
gateway-service
  │ 1. 验签，取出 sub / role
  │ 2. 路由级授权：
  │      /api/v1/admin/**  仅 ADMIN
  │      /api/v1/hr/**     仅 RECRUITER / ADMIN
  │ 3. 剥掉客户端自带的 X-User-Id / X-User-Role，再注入真实值
  ▼
下游服务
  │ 只信任 X-User-Id / X-User-Role
  │ 数据归属仍然按 X-User-Id 二次校验（两层缺一不可）
  ▼
admin-service 额外做一次 ADMIN 校验：**自己验 JWT 签名**，不信任 X-User-Role
（直连 8086 伪造请求头只会得到 401/403）
```

**角色变更需要重新登录**：role 写在 accessToken 里，旧令牌在过期前仍然是旧角色。
这是无状态 JWT 的固有取舍；如果要立即生效，需要在网关查一次用户角色（会把网关变成瓶颈），
当前刻意不做。

---

## 4. 数据库

`db/migrations/V3__platform_roles.sql` + `V4__agent_configs_activity.sql`：

| 变更 | 说明 |
|---|---|
| `t_user.role` | `USER` / `RECRUITER` / `ADMIN`，默认 `USER` |
| `t_platform_config` | key -> JSON 的平台配置（LLM / 向量 / TTS / ASR / Agent 编排 / 功能开关 / 品牌 / 合规） |
| `t_admin_audit_log` | 管理端敏感操作留痕 |

V4 是幂等迁移，`start-all.sh infra` 每次启动都会执行，只补缺失行、不覆盖运营已改的值。
用户行为不另建日志表，直接聚合 `t_user` / `t_resume` / `t_jd` / `t_match_record` / `t_ai_task` / `t_hr_insight`。

自助注册可以选择 `USER`（求职者）或 `RECRUITER`（招聘方）；`ADMIN` 仍然只能由管理端授予。

---

## 5. 本地启动与账号

```bash
cp scripts/local/linux/env.example scripts/local/.env
./scripts/local/linux/start-all.sh infra      # MySQL / Redis + 迁移
cd services && mvn -q -DskipTests package && cd ..
./scripts/local/linux/start-all.sh all
./scripts/local/linux/bootstrap-admin.sh      # 引导管理员 / 演示 HR
```

| 地址 | 说明 |
|---|---|
| http://localhost:3000 | 官网首页（产品介绍 + 双入口） |
| http://localhost:3000/login?role=seeker | 求职者登录 |
| http://localhost:3000/login?role=recruiter | 招聘方登录 |
| http://localhost:3000/seeker/dashboard | 求职者工作台 |
| http://localhost:3000/hr/dashboard | 招聘方工作台 |
| http://localhost:3100 | 管理端（概览 / 用户行为 / Agent 配置 / 服务健康 / 审计） |
| http://localhost:8080 | 网关 |

| 账号 | 密码 | 角色 |
|---|---|---|
| `user@demo.local` | `User1234` | USER |
| `hr@demo.local` | `Abcd1234` | RECRUITER |
| `admin@demo.local` | `Admin1234` | ADMIN |

> 演示账号由 `bootstrap-admin.sh` 创建并写角色；`user@demo.local` 可自行注册。

---

## 6. 本期已完成

- 官网首页 `/`：产品介绍、解决什么问题、两端能力、工作原理、双登录入口
- `/seeker/**`：求职者工作台、简历分析、JD 解析、匹配打分、简历优化、模拟面试、求职信、职业规划
- `/hr/**`：招聘工作台、候选人简历导入、岗位 JD、批量岗位匹配、深度洞察与验证问题
- 管理端：运营概览（14 天趋势 / 任务类型分布）、用户与角色、用户行为、AI 任务、Agent 配置、平台配置、服务健康、操作审计
- `V4__agent_configs_activity.sql`：补齐 LLM / 向量 / TTS / ASR / Agent 编排 / 筛选合规默认配置

## 7. 本期明确没做

- 企业 / 租户（`org_id`）、席位、企业账单
- 职位发布、投递、候选人主动投递与站内沟通 —— 这不是招聘平台
- HR 跨用户检索简历、自动录用 / 淘汰结论 —— 合规边界，刻意不做
- 订单 / 支付 / 额度扣减（表已就位）
- Android 端适配（按约定不动 `android/**`）
- 配置下发给 Agent：`t_platform_config` 是配置事实来源，但 Agent 进程当前仍读部署环境变量，
  保存后需按部署流程下发或重启生效。管理端 Agent 配置页已明确标注这一点。
