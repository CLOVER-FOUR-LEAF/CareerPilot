<template>
  <a-layout class="admin-layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible :width="220" theme="dark">
      <div class="brand">
        <span class="brand-main">CareerPilot</span>
        <span v-if="!collapsed" class="brand-sub">管理后台</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        :items="menuItems"
        @click="handleMenuClick"
      />
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <a-dropdown placement="bottomRight">
            <a class="user-entry">
              <a-avatar style="background-color: #ff4d4f">{{ avatarText }}</a-avatar>
              <span class="user-name">{{ auth.nickname }}</span>
            </a>
            <template #overlay>
              <a-menu @click="handleUserMenu">
                <a-menu-item key="logout">
                  <LogoutOutlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AuditOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  FundOutlined,
  LogoutOutlined,
  RobotOutlined,
  SettingOutlined,
  TeamOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)

const MENU = {
  dashboard: { path: '/dashboard', label: '运营概览', icon: DashboardOutlined },
  users: { path: '/users', label: '用户与角色', icon: TeamOutlined },
  activity: { path: '/activity', label: '用户行为', icon: FundOutlined },
  tasks: { path: '/tasks', label: 'AI 任务', icon: ThunderboltOutlined },
  agents: { path: '/agents', label: 'Agent 配置', icon: RobotOutlined },
  configs: { path: '/configs', label: '平台配置', icon: SettingOutlined },
  services: { path: '/services', label: '服务健康', icon: CloudServerOutlined },
  audits: { path: '/audits', label: '操作审计', icon: AuditOutlined },
} as const

type MenuKey = keyof typeof MENU

const menuItems = (Object.keys(MENU) as MenuKey[]).map((key) => ({
  key,
  icon: () => h(MENU[key].icon),
  label: MENU[key].label,
}))

const selectedKeys = computed(() => {
  const segment = route.path.split('/')[1] || 'dashboard'
  return Object.keys(MENU).includes(segment) ? [segment] : []
})

const currentTitle = computed(() => (route.meta.title as string) || '管理后台')
const avatarText = computed(() => auth.nickname.charAt(0).toUpperCase())

function handleMenuClick({ key }: { key: string | number }) {
  const target = MENU[String(key) as MenuKey]?.path
  if (target && target !== route.path) router.push(target)
}

async function handleUserMenu({ key }: { key: string | number }) {
  if (key === 'logout') {
    await auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
.brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
}
.brand-main {
  font-size: 17px;
  font-weight: 600;
}
.brand-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}
.header {
  background: #fff;
  padding: 0 24px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
}
.header-title {
  font-size: 16px;
  font-weight: 500;
}
.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  color: inherit;
}
.user-name {
  font-size: 14px;
}
.content {
  padding: 24px;
  background: #f0f2f5;
}
</style>
