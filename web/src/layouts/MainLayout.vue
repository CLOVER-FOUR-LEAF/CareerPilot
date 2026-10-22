<template>
  <a-layout class="main-layout">
    <a-layout-header class="header">
      <div class="logo">
        <span class="logo-text">CareerPilot</span>
      </div>
      <a-menu
        :selected-keys="selectedKeys"
        theme="dark"
        mode="horizontal"
        :items="menuItems"
        @click="handleMenuClick"
      />
      <div class="header-right">
        <a-dropdown placement="bottomRight">
          <div class="user-entry">
            <a-avatar :src="userStore.user?.avatar || undefined" style="background-color: #1890ff; cursor: pointer">
              {{ userStore.avatarText }}
            </a-avatar>
            <span class="user-name">{{ userStore.nickname || '未登录' }}</span>
          </div>
          <template #overlay>
            <a-menu @click="handleUserMenuClick">
              <a-menu-item key="profile">
                <UserOutlined /> 个人中心
              </a-menu-item>
              <a-menu-item key="membership">
                <CrownOutlined /> 会员权益
              </a-menu-item>
              <a-menu-divider />
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

    <a-layout-footer class="footer">
      CareerPilot &copy; 2024 智能求职领航员
    </a-layout-footer>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, h, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AimOutlined,
  CrownOutlined,
  DashboardOutlined,
  EditOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  LogoutOutlined,
  MailOutlined,
  RocketOutlined,
  SolutionOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menuRoutes: Record<string, string> = {
  dashboard: '/dashboard',
  resume: '/resume',
  jd: '/jd',
  match: '/match',
  optimize: '/optimize',
  interview: '/interview',
  coverLetter: '/cover-letter',
  careerPlan: '/career-plan',
  hrInsight: '/hr-insight',
}

const menuItems = [
  { key: 'dashboard', icon: () => h(DashboardOutlined), label: '首页' },
  { key: 'resume', icon: () => h(FileTextOutlined), label: '简历管理' },
  { key: 'jd', icon: () => h(FileSearchOutlined), label: 'JD 解析' },
  { key: 'match', icon: () => h(AimOutlined), label: '匹配打分' },
  { key: 'optimize', icon: () => h(EditOutlined), label: '简历优化' },
  { key: 'interview', icon: () => h(SolutionOutlined), label: '模拟面试' },
  { key: 'coverLetter', icon: () => h(MailOutlined), label: '求职信' },
  { key: 'careerPlan', icon: () => h(RocketOutlined), label: '职业规划' },
  { key: 'hrInsight', icon: () => h(TeamOutlined), label: 'HR 洞察' },
]

// 高亮当前路由（顶层面板中 firstSegment 即菜单 key）
const selectedKeys = computed<string[]>(() => {
  const segment = route.path.split('/')[1] || 'dashboard'
  return Object.keys(menuRoutes).includes(segment) ? [segment] : []
})

function handleMenuClick({ key }: { key: string | number }) {
  const target = menuRoutes[String(key)]
  if (target && target !== route.path) {
    router.push(target)
  }
}

function handleUserMenuClick({ key }: { key: string | number }) {
  if (key === 'profile') {
    router.push('/profile')
  } else if (key === 'membership') {
    router.push({ path: '/profile', query: { tab: 'membership' } })
  } else if (key === 'logout') {
    handleLogout()
  }
}

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}

onMounted(async () => {
  // 令牌在但缺少昵称时补一次资料，避免顶部空白
  if (userStore.token && !userStore.nickname) {
    try {
      await userStore.fetchProfile()
    } catch {
      // 忽略：401 时 request.ts 已处理跳转
    }
  }
})
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  padding: 0 24px;
}
.logo {
  margin-right: 24px;
  flex: none;
}
.logo-text {
  color: #fff;
  font-size: 20px;
  font-weight: bold;
}
.header-right {
  margin-left: auto;
  padding-left: 16px;
  background: transparent;
}
.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.user-name {
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
}
.content {
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 128px);
}
.footer {
  text-align: center;
  color: #999;
}
</style>
