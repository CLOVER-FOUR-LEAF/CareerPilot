import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { currentUser, isAuthenticated } from '@/utils/storage'
import type { UserRole } from '@/types'

declare module 'vue-router' {
  interface RouteMeta {
    /** 免登录页面 */
    public?: boolean
    /** 浏览器标题后缀 */
    title?: string
    /** 允许访问的角色；不写表示登录即可访问 */
    roles?: UserRole[]
  }
}

const SEEKER_ROLES: UserRole[] = ['USER']
/** 招聘方与管理员共用 HR 工作台 */
const HR_ROLES: UserRole[] = ['RECRUITER', 'ADMIN']
const ALL_ROLES: UserRole[] = ['USER', 'RECRUITER', 'ADMIN']

function currentRole(): UserRole {
  return currentUser.value?.role || 'USER'
}

/** 登录后的默认落地页：两端按子路径隔离 */
function homeFor(role: UserRole): string {
  return role === 'USER' ? '/seeker/dashboard' : '/hr/dashboard'
}

/** 未登录访问受保护页面时，登录页按路径决定展示求职者还是招聘方入口 */
function loginRoleFor(path: string): 'seeker' | 'recruiter' {
  return path.startsWith('/hr') ? 'recruiter' : 'seeker'
}

const routes: RouteRecordRaw[] = [
  {
    // 官网首页：项目介绍 + 双入口，登录与否都可以访问
    path: '/',
    name: 'Landing',
    component: () => import('@/views/Landing.vue'),
    meta: { public: true, title: '智能求职与招聘决策' },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true, title: '求职者注册' },
  },
  {
    // 无权限时的落地页，避免静默跳走让用户以为点错了
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/views/Forbidden.vue'),
    meta: { title: '无访问权限' },
  },

  // ── 求职者端（C 端）─────────────────────────────────────
  {
    path: '/seeker',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/seeker/dashboard',
    meta: { roles: SEEKER_ROLES },
    children: [
      {
        path: 'dashboard',
        name: 'SeekerDashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '求职工作台' },
      },
      {
        path: 'resume',
        name: 'SeekerResume',
        component: () => import('@/views/Resume.vue'),
        meta: { title: '简历分析' },
      },
      {
        path: 'jd',
        name: 'SeekerJd',
        component: () => import('@/views/JdManage.vue'),
        meta: { title: 'JD 解析' },
      },
      {
        path: 'match',
        name: 'SeekerMatch',
        component: () => import('@/views/Match.vue'),
        meta: { title: '匹配打分' },
      },
      {
        path: 'optimize',
        name: 'SeekerOptimize',
        component: () => import('@/views/Optimize.vue'),
        meta: { title: '简历优化' },
      },
      {
        path: 'interview',
        name: 'SeekerInterview',
        component: () => import('@/views/Interview.vue'),
        meta: { title: '模拟面试' },
      },
      {
        path: 'cover-letter',
        name: 'SeekerCoverLetter',
        component: () => import('@/views/CoverLetter.vue'),
        meta: { title: '求职信' },
      },
      {
        path: 'career-plan',
        name: 'SeekerCareerPlan',
        component: () => import('@/views/CareerPlan.vue'),
        meta: { title: '职业规划' },
      },
      {
        path: 'profile',
        name: 'SeekerProfile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心' },
      },
    ],
  },

  // ── 招聘方端（HR 端，与求职者共用前端）──────────────────
  {
    path: '/hr',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/hr/dashboard',
    meta: { roles: HR_ROLES },
    children: [
      {
        path: 'dashboard',
        name: 'HrDashboard',
        component: () => import('@/views/hr/HrDashboard.vue'),
        meta: { title: '招聘工作台' },
      },
      {
        path: 'resumes',
        name: 'HrResumes',
        component: () => import('@/views/hr/HrResumes.vue'),
        meta: { title: '候选人简历' },
      },
      {
        path: 'jds',
        name: 'HrJds',
        component: () => import('@/views/hr/HrJds.vue'),
        meta: { title: '岗位 JD' },
      },
      {
        path: 'match',
        name: 'HrMatch',
        component: () => import('@/views/hr/HrMatch.vue'),
        meta: { title: '岗位匹配' },
      },
      {
        path: 'insight',
        name: 'HrInsight',
        component: () => import('@/views/HrView.vue'),
        meta: { title: '深度洞察' },
      },
      {
        path: 'profile',
        name: 'HrProfile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心' },
      },
    ],
  },

  // ── 旧链接兼容 ──────────────────────────────────────────
  { path: '/dashboard', redirect: '/seeker/dashboard' },
  { path: '/resume', redirect: '/seeker/resume' },
  { path: '/jd', redirect: '/seeker/jd' },
  { path: '/match', redirect: '/seeker/match' },
  { path: '/optimize', redirect: '/seeker/optimize' },
  { path: '/interview', redirect: '/seeker/interview' },
  { path: '/cover-letter', redirect: '/seeker/cover-letter' },
  { path: '/career-plan', redirect: '/seeker/career-plan' },
  { path: '/profile', redirect: () => (currentRole() === 'USER' ? '/seeker/profile' : '/hr/profile') },
  { path: '/hr-insight', redirect: '/hr/insight' },
  { path: '/hr/candidates', redirect: '/hr/resumes' },
  { path: '/hr/jobs', redirect: '/hr/jds' },
  {
    path: '/:pathMatch(.*)*',
    redirect: () => (isAuthenticated() ? homeFor(currentRole()) : '/'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const authed = isAuthenticated()
  const role = currentRole()

  if (!to.meta.public && !authed) {
    return {
      path: '/login',
      query: {
        role: loginRoleFor(to.path),
        redirect: to.fullPath === '/' ? undefined : to.fullPath,
      },
    }
  }
  if (to.meta.public && authed && (to.path === '/login' || to.path === '/register')) {
    return { path: homeFor(role) }
  }

  // 角色不匹配：给一个明确的 403 页面，而不是静默回首页
  if (authed && to.meta.roles && !to.meta.roles.includes(role)) {
    return { path: '/forbidden', query: { from: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} - CareerPilot` : 'CareerPilot - 智能求职领航员'
})

export default router
