# CareerPilot Android 客户端

CareerPilot 的原生 Android 端实现。**只包含前端**：所有业务数据来自现有后端的
`/api/v1/**` 网关接口，后端上服务器后只需在 App 内改一次服务器地址，无需重新打包。

技术栈：**Kotlin 2.0 + Jetpack Compose (Material 3) + Navigation Compose + OkHttp + kotlinx.serialization**

---

## 一、快速开始

### 1. 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | AGP 8.7 要求 17；装 21 也可 |
| Android SDK | Platform 35 + Build-Tools 35.0.0 | `compileSdk = 35` |
| Gradle | 8.9 | 仓库自带 wrapper，无需预装 |

### 2. 指向 SDK

在 `android/local.properties` 写入本机 SDK 路径（该文件不入库）：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

### 3. 构建

```powershell
cd android
.\gradlew.bat assembleDebug      # 调试包
.\gradlew.bat assembleRelease    # 发布包（见下方签名说明）
```

产物：

| 变体 | 路径 |
|------|------|
| debug | `android/app/build/outputs/apk/debug/app-debug.apk` |
| release | `android/app/build/outputs/apk/release/app-release.apk` |

### 4. 连上后端

App 默认连 `http://10.0.2.2:8080/api/v1/`（`10.0.2.2` 是模拟器访问宿主机的固定地址）。

- **模拟器**：默认地址直接可用。
- **真机**：登录页右上角齿轮图标（或「我的 → 服务器地址」）改成电脑局域网 IP，
  例如 `http://192.168.1.20:8080`。
- **正式环境**：部署完成后改成 `https://你的域名/api/v1/` 即可。

> 明文 HTTP 在 `network_security_config.xml` 里放行了（开发期局域网需要）。
> 上公网前建议把 `base-config` 的 `cleartextTrafficPermitted` 改成 `false`，强制 HTTPS。

### 5. 校验接口契约

```powershell
.\gradlew.bat testDebugUnitTest
```

`app/src/test/java/com/careerpilot/app/LiveContractTest.kt` 里有 15 个用例，
用**真实后端抓下来的响应体**校验数据模型能否正确解码。

这些用例抓到过两个真问题：

1. **匹配记录里存的是业务 ID**（`res_xxx` / `jd_xxx`）而不是数据库主键 ——
   `docs/API-FRONTEND.md` §1.6 写了这点，但 `web/src/types/index.ts` 的
   `MatchRecord.resumeId: number` 是错的，照着抄会直接反序列化失败。
2. **分数字段是整数 JSON 字面量**（`"finalScore": 54`、`"score": 60`），
   声明成 `Double` 必须能接住，否则 ATS 评分和匹配分数整页空白。

后端上线后想重新核对一遍，用仓库根目录的探测脚本：

```bash
node scripts/probe-api.mjs
```

它会注册一个一次性账号，把每个接口的真实返回打出来（会在库里留一个测试用户）。

---

## 二、功能对照

Android 端完整覆盖了 Web 端（`web/`）的全部页面：

| Web 路由 | Android 页面 | 位置 |
|----------|--------------|------|
| `/login` | 登录 | `ui/auth/LoginScreen.kt` |
| `/register` | 注册 | `ui/auth/RegisterScreen.kt` |
| `/dashboard` | 首页 | `ui/dashboard/` |
| `/resume` | 简历管理 | `ui/resume/ResumeListScreen.kt` |
| `/resume/{id}` | 简历详情（ATS 评分 / 正文 / 版本） | `ui/resume/ResumeDetailScreen.kt` |
| `/jd` | JD 解析 | `ui/jd/JdListScreen.kt` |
| `/jd/{id}` | JD 详情（解析结果 / 技能 / 原文） | `ui/jd/JdDetailScreen.kt` |
| `/match` | 匹配打分 + 历史 | `ui/match/MatchScreen.kt` |
| `/match/{id}` | 匹配详情（三段式分数 + Gap List） | `ui/match/MatchDetailScreen.kt` |
| `/optimize` | 简历优化 | `ui/optimize/` |
| `/interview` | 模拟面试（出题 + 评分两阶段） | `ui/interview/` |
| `/cover-letter` | 生成求职信 | `ui/coverletter/` |
| `/career-plan` | 职业规划 | `ui/careerplan/` |
| `/profile` | 个人中心 + 会员信息 | `ui/profile/` |

导航结构：底部 5 个 Tab（首页 / 简历 / 职位 / 匹配 / 我的），
4 个 AI 工具从首页的「AI 工具」区进入。

---

## 三、与 Web 端共享的接口约定

完整契约见 [`../docs/API-FRONTEND.md`](../docs/API-FRONTEND.md)。要点：

- **基础路径**：`/api/v1/**`，统一包络 `{ code, message, data, traceId }`。
  `code == 0` 才算成功。
- **两种 ID**：URL 路径参数用**数据库主键**（数字），
  传给 AI Agent 的 `bizId` / `jd_id` / `resume_id` 用**业务 ID**（`res_xxx` / `jd_xxx`）。
- **鉴权**：`Authorization: Bearer <accessToken>`。
  401 时客户端用 `refreshToken` 换新令牌并自动重放一次原请求（并发请求共享同一次刷新）。
- **AI 任务**：统一走 `POST /ai-tasks { taskType, bizId, input }`，
  再用 `GET /ai-tasks/{taskId}` 轮询或 `GET /ai-tasks/{taskId}/stream`（SSE）跟踪。

### SSE 客户端

原生 `EventSource` 带不了 `Authorization` 头，所以 `data/api/SseClient.kt`
用 OkHttp 读流并手工解析 `event:` / `data:` 分帧，后端零改动。
SSE 不可用时自动降级为轮询（1.5s 一次，最长 60s），与 Web 端行为一致。

---

## 四、针对 Agent 契约的一处修正

`docs/API-FRONTEND.md` §17.1 记录了一个已知问题：前端传给 Agent 的
`jd_id` / `jd_text` 不在 Agent 的 Pydantic 模型里，会被 `extra='ignore'` **静默丢弃**，
导致「针对这个 JD 优化」「求职信含真实 JD 技能」「面试题命中 JD 技能」三个功能实际不生效。

Agent 真正消费的是 `jd_skills`（见各 Agent 的 `OptimizeRequest` / `QuestionRequest` /
`GenerateRequest`）。本 App 在选定 JD 后会额外拉一次 `GET /jd/{id}/parse/result`，
把解析出的技能名映射成 `jd_skills` 一起传过去：

```kotlin
put("jd_skills", JsonArray(jdSkills.map { JsonPrimitive(it) }))
```

涉及页面：简历优化、模拟面试、生成求职信。

Web 端目前仍未修这一条（`scripts/verify_contract.py` 把这些键列为
`ALLOWED_EXTRA_KEYS`，所以校验脚本是绿的）。建议同步修 Web 端。

---

## 五、代码结构

```
android/app/src/main/java/com/careerpilot/app/
├── CareerPilotApp.kt          Application，初始化 ServiceLocator
├── MainActivity.kt            单 Activity + Compose
├── core/                      基础设施
│   ├── ApiResult.kt            统一调用结果
│   ├── TokenStore.kt           令牌 / 服务器地址持久化
│   └── Format.kt               日期 / 分数 / 状态文案
├── data/
│   ├── model/Models.kt         与后端一一对应的数据模型
│   ├── api/
│   │   ├── ApiClient.kt        OkHttp + 包络解析 + 自动刷新令牌
│   │   └── SseClient.kt        SSE 分帧解析
│   └── repo/
│       ├── CareerPilotRepository.kt   业务接口封装
│       ├── Session.kt                  登录态
│       └── ServiceLocator.kt           轻量服务定位器
└── ui/
    ├── theme/Theme.kt          Material 3 配色与字体
    ├── components/             通用组件（卡片 / 标签 / 下拉 / 复制块 …）
    ├── common/                 AiTaskViewModel（AI 任务通用流程）、FilePicker
    ├── nav/                    路由表 + NavHost
    ├── auth/ dashboard/ resume/ jd/ match/ profile/ settings/
    └── optimize/ interview/ coverletter/ careerplan/
```

### AI 任务的通用流程

`ui/common/AiTaskViewModel.kt` 对应 Web 端的 `useTaskPolling.ts`：

```kotlin
runTask(taskType = "resume_optimize", bizId = jdId, input = input, useStream = true)
```

创建任务 → 跟踪（SSE 优先，失败降级轮询）→ 产出 `output`。
`output` 保持为 `JsonElement`，各页面按需反序列化成自己的类型
（`interview` 一个 taskType 下 question / evaluate 两阶段输出结构不同）。

---

## 六、签名与上架

当前 `release` 构建在**没有** `android/keystore.properties` 时会回落到 debug 证书签名，
保证任何机器上都能直接产出「可安装」的 APK。要出正式包：

```properties
# android/keystore.properties（该文件不入库）
storeFile=../careerpilot-release.jks
storePassword=***
keyAlias=careerpilot
keyPassword=***
```

然后用 `keytool` 生成证书，再执行 `.\gradlew.bat assembleRelease` 即可自动切换到正式签名。

`minSdk = 24`（Android 7.0），`targetSdk = 35`。

---

## 七、已知边界

- **未做多语言**：界面文案硬编码中文，与 Web 端一致。
- **未做离线缓存**：全部数据实时拉取，无 Room / DataStore 持久化（令牌除外）。
- **未做消息推送 / 后台任务**：AI 任务在页面存活期间跟踪，退出页面即中断（后端任务仍会跑完，
  结果可在「匹配历史」或任务列表里看到）。
- **头像依赖网络**：头像 URL 加载失败时回退为昵称首字。
