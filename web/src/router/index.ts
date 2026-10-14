import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { isAuthenticated } from '@/utils/storage'

declare module 'vue-router' {
  interface RouteMeta {
    /** 免登录页面 */
    public?: boolean
    /** 浏览器标题后缀 */
    title?: string
  }
}

const routes: RouteRecordRaw[] = [
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
    meta: { public: true, title: '注册' },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'resume',
        name: 'Resume',
        component: () => import('@/views/Resume.vue'),
        meta: { title: '简历管理' },
      },
      {
        path: 'jd',
        name: 'JdManage',
        component: () => import('@/views/JdManage.vue'),
        meta: { title: 'JD 解析' },
      },
      {
        path: 'match',
        name: 'Match',
        component: () => import('@/views/Match.vue'),
        meta: { title: '匹配打分' },
      },
      {
        path: 'optimize',
        name: 'Optimize',
        component: () => import('@/views/Optimize.vue'),
        meta: { title: '简历优化' },
      },
      {
        path: 'interview',
        name: 'Interview',
        component: () => import('@/views/Interview.vue'),
        meta: { title: '模拟面试' },
      },
      {
        path: 'cover-letter',
        name: 'CoverLetter',
        component: () => import('@/views/CoverLetter.vue'),
        meta: { title: '求职信' },
      },
      {
        path: 'career-plan',
        name: 'CareerPlan',
        component: () => import('@/views/CareerPlan.vue'),
        meta: { title: '职业规划' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const authed = isAuthenticated()

  if (!to.meta.public && !authed) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.meta.public && authed && (to.path === '/login' || to.path === '/register')) {
    return { path: '/dashboard' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} - CareerPilot` : 'CareerPilot - 智能求职领航员'
})

export default router
