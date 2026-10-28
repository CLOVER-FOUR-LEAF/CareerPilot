import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/token'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
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
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '运营概览' },
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/Users.vue'),
        meta: { title: '用户与角色' },
      },
      {
        path: 'activity',
        name: 'Activity',
        component: () => import('@/views/Activity.vue'),
        meta: { title: '用户行为' },
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('@/views/Tasks.vue'),
        meta: { title: 'AI 任务' },
      },
      {
        path: 'agents',
        name: 'Agents',
        component: () => import('@/views/Agents.vue'),
        meta: { title: 'Agent 配置' },
      },
      {
        path: 'configs',
        name: 'Configs',
        component: () => import('@/views/Configs.vue'),
        meta: { title: '平台配置' },
      },
      {
        path: 'services',
        name: 'Services',
        component: () => import('@/views/Services.vue'),
        meta: { title: '服务健康' },
      },
      {
        path: 'audits',
        name: 'Audits',
        component: () => import('@/views/Audits.vue'),
        meta: { title: '操作审计' },
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
  const loggedIn = getToken().length > 0
  if (!to.meta.public && !loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && loggedIn) {
    return { path: '/dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - CareerPilot 管理后台` : 'CareerPilot 管理后台'
})

export default router
