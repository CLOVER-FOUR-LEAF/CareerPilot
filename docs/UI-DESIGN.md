# CareerPilot 视觉系统

> 方向：**Editorial dossier / paper ledger**。  
> 纸张底色、墨色文字、朱砂强调、衬线标题、细线网格、硬阴影。  
> 不追求“AI SaaS 渐变卡片”，而追求像一份被认真排版的候选人档案 / 运营年报。

## 1. 设计令牌

定义位置：

- `web/src/styles/design-system.css`
- `admin-web/src/styles/design-system.css`
- Ant Design 主题：`web/src/theme.ts`、`admin-web/src/theme.ts`

| 令牌 | 值 | 用途 |
|---|---|---|
| `--paper` | `#f3efe6` | 页面底色 |
| `--paper-2` | `#ece6da` | 分区 / 表头 / 次级底色 |
| `--paper-3` | `#e4dccb` | 禁用态 / 更深的纸色 |
| `--white` | `#fffdf8` | 卡片与表单容器 |
| `--ink` | `#171714` | 主文字 / 深色面板 |
| `--ink-soft` | `#3d3c36` | 正文 |
| `--muted` | `#7a766b` | 说明 / 标签 |
| `--line` | `#d8d0c0` | 细线 |
| `--line-strong` | `#b9ae99` | 强调边框 |
| `--accent` | `#e4572e` | 朱砂：主按钮 / 强调数字 / 选中态 |
| `--accent-deep` | `#b93a18` | 强调文字 / hover |
| `--accent-soft` | `#fbe3d9` | 提示底色 |
| `--forest` | `#1f3d2b` | 深绿：招聘方 / 次强调 |
| `--forest-soft` | `#dce8df` | 深绿浅底 |
| `--sand` | `#e8c9a0` | 深色面板上的高亮 |

## 2. 字体

| 角色 | 字体 |
|---|---|
| 展示 / 标题 | `Instrument Serif` + `Noto Serif SC` |
| 正文 / UI | `Archivo` + `Noto Sans SC` |
| 标签 / 数字 / 代码 | `IBM Plex Mono` |

- 大标题使用衬线体与负字距（`letter-spacing: -0.03em ~ -0.045em`）。
- 小标签统一 Mono、大写、宽字距（`0.1em` 左右）。
- 中文展示标题依赖 `Noto Serif SC`；未加载时回退到系统衬线。

## 3. 布局语言

- **侧边栏**：264px 墨色导航，序号 `01 / 02 / ...` + 图标 + 名称；选中项用朱砂左边线与淡朱砂底。
- **顶部栏**：纸张半透明 + 模糊，Mono kicker + 衬线页面标题 + 日期 + 用户菜单。
- **内容区**：最大宽度 1500px，页面进入时 10px 上移淡入。
- **卡片**：2px 圆角（近乎直角）、1px 细线、白纸底；hover 使用硬阴影 `4px 4px 0`。
- **网格**：大量使用 `border-left/top` 形成账本式分隔，而不是堆叠圆角卡片。
- **空态**：隐藏 Ant 默认插画，用文字 + 主行动按钮替代。

## 4. 组件覆盖

`design-system.css` 统一覆盖了：

- Button / Card / Table / Tag / Form / Input / Select / Modal / Tabs
- Collapse / Alert / Descriptions / List / Timeline / Pagination
- Dropdown / Message / Popover / Empty / Progress / Avatar / Switch

其中：

- 主按钮 = 朱砂底白字；禁用态 = 纸灰底、灰字。
- Tag = 直角、Mono、10px、大写；蓝色系映射到深绿，橙色系映射到朱砂/沙色。
- 表头 = 纸色底 + Mono 大写 + 细线。
- 表单标签 = Mono 大写、10px、宽字距。
- 数字统计 = 衬线体、40-58px、负字距。

## 5. 页面改造清单

- 官网首页：编辑式 Hero、报告预览、章节编号、问题/能力/步骤/CTA。
- 登录 / 注册：深色品牌面板 + 纸张表单面板 + 朱砂顶线。
- 求职者工作台：大标题问候 + 数字台账 + 主链路三卡 + 工具箱列表。
- 招聘方工作台：同一套台账语言，突出“把简历变成证据”。
- 管理端概览：数字台账 + 14 天柱状趋势 + 任务类型分布 + 快捷入口。
- 其余业务页：由全局 Ant 覆盖统一到同一套视觉；空态改为文字 + 行动按钮。

## 6. 注意事项

- 不要在页面内再引入新的高饱和渐变或圆角大卡片；优先使用令牌与网格分隔。
- 新增页面时，页面标题使用 `a-page-header`，会自动获得衬线标题。
- 需要强调数字时使用 `.display` / 页面已有的 `strong` 结构，不要用 Ant Statistic 的默认蓝色。
- 深色面板上使用 `--paper`、`--sand`、`--accent`，不要使用纯白 `#fff`。
