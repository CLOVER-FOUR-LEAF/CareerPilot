<template>
  <a-layout class="main-layout">
    <a-layout-header class="header">
      <div class="logo" @click="router.push(userStore.homePath)">
        <span class="logo-text">CareerPilot</span>
        <a-tag v-if="userStore.isLoggedIn" class="role-tag" :color="roleColor">
          {{ userStore.roleLabel }}
        </a-tag>
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
  HomeOutlined,
  LogoutOutlined,
  MailOutlined,
  RocketOutlined,
  SettingOutlined,
  SolutionOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 菜单 key -> 路由；同一份表同时驱动「菜单渲染」和「当前项高亮」 */
const MENU = {
  seekerDashboard: { path: '/seeker/dashboard', label: '工作台', icon: DashboardOutlined },
  seekerResume: { path: '/seeker/resume', label: '简历分析', icon: FileTextOutlined },
  seekerJd: { path: '/seeker/jd', label: 'JD 解析', icon: FileSearchOutlined },
  seekerMatch: { path: '/seeker/match', label: '匹配打分', icon: AimOutlined },
  seekerOptimize: { path: '/seeker/optimize', label: '简历优化', icon: EditOutlined },
  seekerInterview: { path: '/seeker/interview', label: '模拟面试', icon: SolutionOutlined },
  seekerCoverLetter: { path: '/seeker/cover-letter', label: '求职信', icon: MailOutlined },
  seekerCareerPlan: { path: '/seeker/career-plan', label: '职业规划', icon: RocketOutlined },

  hrDashboard: { path: '/hr/dashboard', label: '招聘工作台', icon: TeamOutlined },
  hrResumes: { path: '/hr/resumes', label: '候选人简历', icon: UserOutlined },
  hrJds: { path: '/hr/jds', label: '岗位 JD', icon: FileSearchOutlined },
  hrMatch: { path: '/hr/match', label: '岗位匹配', icon: AimOutlined },
  hrInsight: { path: '/hr/insight', label: '深度洞察', icon: ThunderboltOutlined },
} as const

type MenuKey = keyof typeof MENU

/** 求职者与招聘方看到的是两套菜单，互不混排 */
const visibleKeys = computed<MenuKey[]>(() => {
  if (userStore.canAccessHr) {
    return ['hrDashboard', 'hrResumes', 'hrJds', 'hrMatch', 'hrInsight']
  }
  return [
    'seekerDashboard', 'seekerResume', 'seekerJd', 'seekerMatch',
    'seekerOptimize', 'seekerInterview', 'seekerCoverLetter', 'seekerCareerPlan',
  ]
})

const menuItems = computed(() =>
  visibleKeys.value.map((key) => ({
    key,
    icon: () => h(MENU[key].icon),
    label: MENU[key].label,
  })),
)

const selectedKeys = computed<string[]>(() => {
  const hit = (Object.keys(MENU) as MenuKey[]).find((key) => {
    const path = MENU[key].path
    return route.path === path || route.path.startsWith(path + '/')
  })
  return hit ? [hit] : []
})

const roleColor = computed(() => {
  if (userStore.role === 'ADMIN') return 'red'
  return userStore.isRecruiter ? 'gold' : 'blue'
})

/** 管理端是独立前端（admin-web），默认同主机 3100 端口 */
const adminUrl = import.meta.env.VITE_ADMIN_URL
  || `${window.location.protocol}//${window.location.hostname}:3100`

function handleMenuClick({ key }: { key: string | number }) {
  const target = MENU[String(key) as MenuKey]?.path
  if (target && target !== route.path) {
    router.push(target)
  }
}

function handleUserMenuClick({ key }: { key: string | number }) {
  if (key === 'profile') {
    router.push(userStore.canAccessHr ? '/hr/profile' : '/seeker/profile')
  } else if (key === 'membership') {
    router.push({ path: '/seeker/profile', query: { tab: 'membership' } })
  } else if (key === 'landing') {
    router.push('/')
  } else if (key === 'admin') {
    window.location.href = adminUrl
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
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.logo-text {
  color: #fff;
  font-size: 20px;
  font-weight: bold;
}
.role-tag {
  margin: 0;
  font-size: 12px;
  line-height: 20px;
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
