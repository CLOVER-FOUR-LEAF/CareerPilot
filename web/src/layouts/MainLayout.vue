<template>
  <div class="app-shell">
    <div v-if="mobileOpen" class="scrim" @click="mobileOpen = false" />

    <aside class="sidebar" :class="{ open: mobileOpen }">
      <div class="brand-block">
        <router-link to="/" class="brand" @click="mobileOpen = false">
          <span class="brand-mark">CP</span>
          <span class="brand-copy">
            <span class="brand-name">CareerPilot</span>
            <span class="brand-tagline">Evidence-led career intelligence</span>
          </span>
        </router-link>
        <div class="role-chip">
          <span class="role-dot" />
          {{ roleChip }}
        </div>
      </div>

      <nav class="nav">
        <span class="nav-kicker">{{ userStore.canAccessHr ? 'Recruiter console' : 'Candidate console' }}</span>
        <router-link
          v-for="(item, index) in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          @click="mobileOpen = false"
        >
          <span class="nav-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <component :is="item.icon" class="nav-icon" />
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <router-link to="/" class="side-action" @click="mobileOpen = false">
          <HomeOutlined /> 官网首页
        </router-link>
        <button type="button" class="side-action" @click="handleLogout">
          <LogoutOutlined /> 退出登录
        </button>
        <div class="sidebar-meta">CAREERPILOT · v1.0</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <button type="button" class="menu-toggle" @click="mobileOpen = !mobileOpen">
          <CloseOutlined v-if="mobileOpen" />
          <MenuOutlined v-else />
        </button>

        <div class="topbar-title-block">
          <span class="topbar-kicker">{{ userStore.canAccessHr ? 'Hiring desk' : 'Career desk' }}</span>
          <h1 class="topbar-title">{{ currentLabel }}</h1>
        </div>

        <div class="topbar-actions">
          <span class="topbar-date">{{ today }}</span>
          <a-dropdown placement="bottomRight">
            <button type="button" class="user-entry">
              <a-avatar :src="userStore.user?.avatar || undefined" :size="32">
                {{ userStore.avatarText }}
              </a-avatar>
              <span class="user-name">{{ userStore.nickname || '未登录' }}</span>
              <DownOutlined class="chevron" />
            </button>
            <template #overlay>
              <a-menu @click="handleUserMenuClick">
                <a-menu-item key="profile">
                  <UserOutlined /> 个人中心
                </a-menu-item>
                <a-menu-item v-if="userStore.isSeeker" key="membership">
                  <CrownOutlined /> 会员权益
                </a-menu-item>
                <a-menu-item key="landing">
                  <HomeOutlined /> 官网首页
                </a-menu-item>
                <a-menu-item v-if="userStore.isAdmin" key="admin">
                  <SettingOutlined /> 管理后台
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
        <span>CareerPilot · 智能求职与招聘决策</span>
        <span>{{ userStore.canAccessHr ? '辅助建议，最终判断由招聘方作出' : '分析结果仅作辅助参考' }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AimOutlined,
  CloseOutlined,
  CrownOutlined,
  DashboardOutlined,
  DownOutlined,
  EditOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  HomeOutlined,
  LogoutOutlined,
  MailOutlined,
  MenuOutlined,
  RocketOutlined,
  SettingOutlined,
  SolutionOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import type { Component } from 'vue'

interface MenuEntry {
  path: string
  label: string
  icon: Component
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const mobileOpen = ref(false)

const SEEKER_MENU: MenuEntry[] = [
  { path: '/seeker/dashboard', label: '工作台', icon: DashboardOutlined },
  { path: '/seeker/resume', label: '简历分析', icon: FileTextOutlined },
  { path: '/seeker/jd', label: 'JD 解析', icon: FileSearchOutlined },
  { path: '/seeker/match', label: '匹配打分', icon: AimOutlined },
  { path: '/seeker/optimize', label: '简历优化', icon: EditOutlined },
  { path: '/seeker/interview', label: '模拟面试', icon: SolutionOutlined },
  { path: '/seeker/cover-letter', label: '求职信', icon: MailOutlined },
  { path: '/seeker/career-plan', label: '职业规划', icon: RocketOutlined },
]

const HR_MENU: MenuEntry[] = [
  { path: '/hr/dashboard', label: '招聘工作台', icon: TeamOutlined },
  { path: '/hr/resumes', label: '候选人简历', icon: UserOutlined },
  { path: '/hr/jds', label: '岗位 JD', icon: FileSearchOutlined },
  { path: '/hr/match', label: '岗位匹配', icon: AimOutlined },
  { path: '/hr/insight', label: '深度洞察', icon: ThunderboltOutlined },
]

const menuItems = computed(() => (userStore.canAccessHr ? HR_MENU : SEEKER_MENU))

const roleChip = computed(() => userStore.roleLabel)

const currentLabel = computed(() => {
  const hit = menuItems.value.find((item) => isActive(item.path))
  return hit?.label || (route.meta.title as string) || '工作台'
})

const today = computed(() => {
  const date = new Date()
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
})

function isActive(path: string) {
  return route.path === path || route.path.startsWith(path + '/')
}

function handleUserMenuClick({ key }: { key: string | number }) {
  if (key === 'profile') {
    router.push(userStore.canAccessHr ? '/hr/profile' : '/seeker/profile')
  } else if (key === 'membership') {
    router.push({ path: '/seeker/profile', query: { tab: 'membership' } })
  } else if (key === 'landing') {
    router.push('/')
  } else if (key === 'admin') {
    window.location.href = import.meta.env.VITE_ADMIN_URL
      || `${window.location.protocol}//${window.location.hostname}:3100`
  } else if (key === 'logout') {
    handleLogout()
  }
}

async function handleLogout() {
  await userStore.logout()
  router.push('/')
}

onMounted(async () => {
  // 令牌在但缺少昵称时补一次资料，避免顶部空白
  if (userStore.token && !userStore.nickname) {
    try {
      await userStore.fetchProfile()
    } catch {
      // 401 时 request.ts 已处理跳转
    }
  }
})
</script>

<style scoped>
.app-shell {
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
    radial-gradient(circle at 0% 0%, rgba(228, 87, 46, 0.18), transparent 34%),
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
  position: relative;
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
  background: var(--forest);
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
  max-width: 1500px;
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
