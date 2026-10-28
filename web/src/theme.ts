import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

/**
 * CareerPilot 的 Ant Design 主题。
 *
 * 视觉方向：纸张 / 墨色 / 朱砂。硬边、细线、衬线标题。
 * 组件级细节由 styles/design-system.css 统一覆盖，避免依赖
 * Ant Design Vue 版本之间不稳定的 component token 类型。
 */
export const theme: ThemeConfig = {
  token: {
    colorPrimary: '#e4572e',
    colorInfo: '#1f3d2b',
    colorSuccess: '#2f6b4f',
    colorWarning: '#a16207',
    colorError: '#b42318',
    colorTextBase: '#171714',
    colorBgBase: '#fffdf8',
    colorBgLayout: '#f3efe6',
    colorBgContainer: '#fffdf8',
    colorBgElevated: '#fffdf8',
    colorBorder: '#d8d0c0',
    colorBorderSecondary: '#e4dccb',
    borderRadius: 2,
    borderRadiusLG: 2,
    borderRadiusSM: 2,
    fontFamily:
      "'Archivo', 'Noto Sans SC', 'PingFang SC', 'WenQuanYi Zen Hei', sans-serif",
    fontSize: 14,
    controlHeight: 40,
    controlHeightLG: 48,
    boxShadow: 'none',
    boxShadowSecondary: '4px 4px 0 rgba(23, 23, 20, 0.08)',
    wireframe: false,
  },
}
