# Nacos 接入评估（先看结论，再谈做法）

> 结论：**Nacos 可以装，但不建议和三端拆分放在同一批做。**
> 先让拆分后的 6+1 个 Java 服务、8 个 Agent 稳定跑一段时间，再单独接 Nacos。
> 本期管理端已经用「配置 + 并发健康探测」解决了「看着方便」的问题，不依赖 Nacos。

---

## 1. Nacos 能解决什么，不能解决什么

| 能力 | 对当前项目的价值 | 现实情况 |
|---|---|---|
| 服务发现（注册/订阅） | 中 | 现在 14 个进程的地址写死在环境变量/配置里，改端口要改多处 |
| 配置中心（动态配置） | 高 | 模型开关、功能开关现在散落在各进程环境变量里，改一次要重启 |
| 控制台可视化 | 低 | 管理端的「服务健康」页已经能看到 14 个进程的 UP/DOWN 与延迟 |
| 配置版本/灰度 | 中 | 当前只有 `t_platform_config` 一张表，没有推送机制 |
| Python 服务纳管 | 低 | 官方 SDK 生态弱于 Java，接入成本高于收益 |

**关键判断**：Nacos 的价值在「服务发现 + 配置下发」，
不在「多一个控制台」。如果只装但不改调用方式，它只是个没人用的注册表。

---

## 2. 为什么现在不接

1. **会改动调用路径**：网关从 `http://${HOST}:${PORT}` 改成 `lb://service-name`，
   服务间调用从固定 URL 改成服务名。任何一处漏改都是启动期才暴露的 500。
2. **Nacos 会成为新的单点**：14 个进程本来就靠 `start.sh` 和健康检查保活，
   再引入一个必须最先启动、最后退出的组件，本地开发的心智负担会上升。
3. **Python 侧收益低**：`nacos-sdk-python` 不是官方主推路线，
   8 个 Agent 注册上去主要换来一个列表，不如保持静态地址 + orchestrator 统一分发。
4. **版本兼容要验证**：当前是 Spring Boot 3.5 + Spring Cloud 2025.0.x，
   Spring Cloud Alibaba 的版本矩阵必须实测，不能照搬 2023.x 的教程。

管理端当前的健康页已经覆盖了「我打开一个页面就能看到谁挂了」这个真实需求：

```
GET /api/v1/admin/services/health
  → 并发探测 6 个 Java + 8 个 Agent，3 秒内返回
```

接入 Nacos 后，这个接口只需要把数据源从「配置里的地址」换成「Nacos 实例列表」，
前端不用改。这是刻意留的替换点。

---

## 3. 推荐的分阶段接入

### 阶段 0（现在）：保持静态地址

- Java 服务地址走环境变量（已有）
- Agent 地址走 orchestrator 环境变量（已有）
- 管理端健康页负责可视化（已实现）

### 阶段 1：只接 Java 侧服务发现

1. 起 Nacos（单机模式，数据落 MySQL 或内置 Derby）：

   ```bash
   docker run -d --name careerpilot-nacos \
     -e MODE=standalone \
     -p 8848:8848 -p 9848:9848 \
     nacos/nacos-server:v2.5.1
   ```

2. 父 POM 引入与当前 Spring Cloud 版本匹配的 `spring-cloud-alibaba-dependencies`
   （**先做兼容性验证**：Boot 3.5 / Cloud 2025.0.x 对应的 SCA 版本）。
3. 各 Java 服务加 `spring-cloud-starter-alibaba-nacos-discovery` +
   `spring-cloud-starter-loadbalancer`，`spring.application.name` 就是注册名。
4. 网关路由从固定 URL 改成：

   ```yaml
   - id: user-center
     uri: lb://user-center
   ```

5. 保留环境变量兜底：用 `spring.profiles.active=nacos` 开启；
   默认 profile 仍走固定地址，本地没起 Nacos 也能跑。

### 阶段 2：配置中心

1. `t_platform_config` 继续作为「事实来源」，管理端写库；
2. 增加一个同步器：库变更 → 调 Nacos 发布配置（或用 Nacos 作为唯一源，库只做审计）；
3. Java 服务用 `spring.config.import=nacos:...` 读取；
4. Agent 侧用 `nacos-sdk-python` 拉取配置，或在 orchestrator 启动时注入。

**注意**：配置中心一旦接上，「管理端保存了但 Agent 没读到」会变成最难排查的一类问题。
所以必须先有：配置版本号、下发确认、生效状态展示。

### 阶段 3（可选）：Python Agent 注册

只有在「Agent 数量继续增长 / 需要按负载路由」时才值得做。
否则 orchestrator 的静态映射表更简单、故障面更小。

---

## 4. 接入后管理端要跟着改的地方

| 页面/接口 | 改动 |
|---|---|
| `/api/v1/admin/services/health` | 数据源从配置列表改为 Nacos 命名服务查询 |
| `/api/v1/admin/configs` | 保存后增加「下发状态」字段 |
| 服务健康页 | 增加实例数、权重、健康实例比例 |

---

## 5. 一句话建议

**先把三端跑稳，再让 Nacos 解决它真正擅长的问题。**
如果只是为了「看着方便」，管理端的健康页已经够用；
如果是为了「改配置不用重启」，那阶段 2 才是重点，而它依赖阶段 1 先落地。
