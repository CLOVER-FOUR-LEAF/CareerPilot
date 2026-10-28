<template>
  <div class="admin-shell">
    <div v-if="mobileOpen" class="scrim" @click="mobileOpen = false" />

    <aside class="sidebar" :class="{ open: mobileOpen }">
      <div class="brand-block">
        <a class="brand" href="javascript:void(0)" @click="router.push('/dashboard')">
          <span class="brand-mark">CP</span>
          <span class="brand-copy">
            <span class="brand-name">CareerPilot</span>
            <span class="brand-tagline">Platform control room</span>
          </span>
        </a>
        <div class="role-chip">
          <span class="role-dot" />
          Platform admin
        </div>
      </div>

      <nav class="nav">
        <span class="nav-kicker">Operations</span>
        <a
          v-for="(item, index) in MENU"
          :key="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          href="javascript:void(0)"
          @click="go(item.path)"
        >
          <span class="nav-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <component :is="item.icon" class="nav-icon" />
          <span class="nav-label">{{ item.label }}</span>
        </a>
      </nav>

      <div class="sidebar-footer">
        <a class="side-action" :href="userWebUrl">
          <ExportOutlined /> 用户端首页
        </a>
        <button type="button" class="side-action" @click="handleLogout">
          <LogoutOutlined /> 退出登录
        </button>
        <div class="sidebar-meta">ADMIN · {{ today }}</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <button type="button" class="menu-toggle" @click="mobileOpen = !mobileOpen">
          <CloseOutlined v-if="mobileOpen" />
          <MenuOutlined v-else />
        </button>

        <div class="topbar-title-block">
          <span class="topbar-kicker">Control room / {{ sectionCode }}</span>
          <h1 class="topbar-title">{{ currentTitle }}</h1>
        </div>

        <div class="topbar-actions">
          <span class="topbar-date">{{ today }}</span>
          <a-dropdown placement="bottomRight">
            <button type="button" class="user-entry">
              <a-avatar :size="32">{{ avatarText }}</a-avatar>
              <span class="user-name">{{ auth.nickname }}</span>
              <DownOutlined class="chevron" />
            </button>
            <template #overlay>
              <a-menu @click="handleUserMenu">
                <a-menu-item key="user-web">
                  <ExportOutlined /> 打开用户端
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout">
                  <LogoutOutlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>

      <footer class="app-footer">
        <span>CareerPilot · Platform administration</span>
        <span>配置与敏感操作全程留痕</span>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AuditOutlined,
  CloseOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  DownOutlined,
  ExportOutlined,
  FundOutlined,
  LogoutOutlined,
  MenuOutlined,
  RobotOutlined,
  SettingOutlined,
  TeamOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import type { Component } from 'vue'

interface MenuEntry {
  path: string
  label: string
  icon: Component
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mobileOpen = ref(false)

const MENU: MenuEntry[] = [
  { path: '/dashboard', label: '运营概览', icon: DashboardOutlined },
  { path: '/users', label: '用户与角色', icon: TeamOutlined },
  { path: '/activity', label: '用户行为', icon: FundOutlined },
  { path: '/tasks', label: 'AI 任务', icon: ThunderboltOutlined },
  { path: '/agents', label: 'Agent 配置', icon: RobotOutlined },
  { path: '/configs', label: '平台配置', icon: SettingOutlined },
  { path: '/services', label: '服务健康', icon: CloudServerOutlined },
  { path: '/audits', label: '操作审计', icon: AuditOutlined },
]

const userWebUrl = import.meta.env.VITE_USER_WEB_URL
  || `${window.location.protocol}//${window.location.hostname}:3000`

const currentTitle = computed(() => {
  const hit = MENU.find((item) => isActive(item.path))
  return hit?.label || (route.meta.title as string) || '控制台'
})

const sectionCode = computed(() => {
  const hit = MENU.find((item) => isActive(item.path))
  return hit ? String(MENU.indexOf(hit) + 1).padStart(2, '0') : '00'
})

const avatarText = computed(() => (auth.nickname || 'A').charAt(0).toUpperCase())

const today = computed(() => {
  const date = new Date()
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
})

function isActive(path: string) {
  return route.path === path || route.path.startsWith(path + '/')
}

function go(path: string) {
  mobileOpen.value = false
  if (route.path !== path) router.push(path)
}

async function handleUserMenu({ key }: { key: string | number }) {
  if (key === 'user-web') {
    window.location.href = userWebUrl
  } else if (key === 'logout') {
    await auth.logout()
    router.push('/login')
  }
}

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-shell {
  min-height: 100vh;
  display: flex;
  background: transparent;
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 30;
  width: 264px;
  display: flex;
  flex-direction: column;
  color: var(--white);
  background-color: #171714;
  background-image:
    radial-gradient(circle at 0% 0%, rgba(228, 87, 46, 0.16), transparent 34%),
    linear-gradient(180deg, rgba(255, 253, 248, 0.03), transparent 30%);
  border-right: 1px solid #2b2b27;
}

.brand-block {
  padding: 26px 22px 20px;
  border-bottom: 1px solid rgba(255, 253, 248, 0.1);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--white);
  text-decoration: none;
}

.brand-mark {
  width: 40px;
  height: 40px;
  flex: none;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 253, 248, 0.28);
  color: var(--sand);
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.brand-copy {
  min-width: 0;
}

.brand-name {
  display: block;
  font-family: var(--font-display);
  font-size: 22px;
  line-height: 1;
  letter-spacing: -0.01em;
}

.brand-tagline {
  display: block;
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: rgba(255, 253, 248, 0.42);
  white-space: nowrap;
}

.role-chip {
  margin-top: 18px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 9px;
  border: 1px solid rgba(255, 253, 248, 0.16);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(255, 253, 248, 0.7);
}

.role-dot {
  width: 6px;
  height: 6px;
  background: var(--accent);
  border-radius: 50%;
}

.nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-kicker {
  padding: 0 10px 12px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(255, 253, 248, 0.3);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 10px;
  border-left: 2px solid transparent;
  color: rgba(255, 253, 248, 0.62);
  text-decoration: none;
  transition: background 0.16s ease, color 0.16s ease, border-color 0.16s ease;
}

.nav-item:hover {
  color: var(--white);
  background: rgba(255, 253, 248, 0.055);
}

.nav-item.active {
  color: var(--white);
  background: rgba(228, 87, 46, 0.16);
  border-left-color: var(--accent);
}

.nav-index {
  width: 18px;
  flex: none;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.05em;
  color: rgba(255, 253, 248, 0.3);
}

.nav-item.active .nav-index {
  color: var(--sand);
}

.nav-icon {
  font-size: 15px;
}

.nav-label {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.sidebar-footer {
  padding: 16px 22px 20px;
  border-top: 1px solid rgba(255, 253, 248, 0.1);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.side-action {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border: 0;
  background: none;
  color: rgba(255, 253, 248, 0.55);
  font-family: var(--font-body);
  font-size: 12px;
  text-decoration: none;
  cursor: pointer;
  text-align: left;
}

.side-action:hover {
  color: var(--sand);
}

.sidebar-meta {
  margin-top: 12px;
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.16em;
  color: rgba(255, 253, 248, 0.24);
}

.main {
  flex: 1;
  min-width: 0;
  margin-left: 264px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 78px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 32px;
  background: rgba(243, 239, 230, 0.88);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--line);
}

.topbar-title-block {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.topbar-kicker {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--muted);
}

.topbar-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 25px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.topbar-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 18px;
}

.topbar-date {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--muted);
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 7px;
  border: 1px solid transparent;
  background: none;
  color: var(--ink);
  cursor: pointer;
}

.user-entry:hover {
  border-color: var(--line);
  background: var(--white);
}

.user-entry :deep(.ant-avatar) {
  background: var(--accent);
  font-size: 12px;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 600;
}

.chevron {
  font-size: 9px;
  color: var(--muted);
}

.content {
  flex: 1;
  width: 100%;
  max-width: 1560px;
  margin: 0 auto;
  padding: 30px 32px 40px;
}

.app-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 32px 22px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line-strong);
  background: var(--white);
  color: var(--ink);
  cursor: pointer;
}

.scrim {
  display: none;
}

@media (max-width: 980px) {
  .sidebar {
    transform: translateX(-100%);
    transition: transform 0.24s ease;
  }
  .sidebar.open {
    transform: translateX(0);
  }
  .main {
    margin-left: 0;
  }
  .menu-toggle {
    display: inline-flex;
  }
  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 25;
    background: rgba(23, 23, 20, 0.42);
  }
  .content {
    padding: 22px 18px 32px;
  }
  .topbar {
    padding: 0 18px;
  }
  .topbar-date {
    display: none;
  }
  .app-footer {
    padding: 14px 18px 20px;
  }
}
</style>
