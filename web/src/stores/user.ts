import { defineStore } from 'pinia'
import { computed } from 'vue'
import { authApi, userApi } from '@/api'
import { refreshSession } from '@/api/request'
import {
  accessToken,
  clearAuth,
  currentUser,
  persistAuth,
  refreshToken as storedRefreshToken,
  setUser,
} from '@/utils/storage'
import type { ApiResponse, LoginResponse, UpdateProfileParams, UserProfile, UserRole } from '@/types'

export interface RegisterPayload {
  email: string
  password: string
  nickname?: string
  inviteCode?: string | null
  role?: 'USER' | 'RECRUITER'
}

export const useUserStore = defineStore('user', () => {
  // 与 utils/storage 共享同一份 ref 状态，刷新页面 / 静默刷新都不会失联
  const token = accessToken
  const user = currentUser

  const isLoggedIn = computed(() => token.value.length > 0)
  const nickname = computed(() => user.value?.nickname || '')
  /** 角色缺失时按最小权限处理，避免旧 token 意外获得 HR 入口 */
  const role = computed<UserRole>(() => user.value?.role || 'USER')
  const isSeeker = computed(() => role.value === 'USER')
  const isRecruiter = computed(() => role.value === 'RECRUITER')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const canAccessHr = computed(() => isRecruiter.value || isAdmin.value)
  /** 登录后的默认落地页：两端按子路径隔离 */
  const homePath = computed(() => (isSeeker.value ? '/seeker/dashboard' : '/hr/dashboard'))
  const roleLabel = computed(() => {
    switch (role.value) {
      case 'RECRUITER': return '招聘方'
      case 'ADMIN': return '平台管理员'
      default: return '求职者'
    }
  })
  const avatarText = computed(() => {
    const source = user.value?.nickname || user.value?.email || 'U'
    return source.charAt(0).toUpperCase()
  })

  function applyAuth(res: ApiResponse<LoginResponse>): LoginResponse {
    persistAuth(res.data)
    return res.data
  }

  async function login(email: string, password: string) {
    const res = await authApi.login({ email, password })
    return applyAuth(res)
  }

  /** §1.1 注册即登录，直接落令牌，不再跳回登录页 */
  async function register(payload: RegisterPayload) {
    const res = await authApi.register({
      email: payload.email,
      password: payload.password,
      nickname: payload.nickname,
      inviteCode: payload.inviteCode ?? null,
      role: payload.role ?? 'USER',
    })
    return applyAuth(res)
  }

  /** 静默刷新，成功返回 true */
  async function refreshToken(): Promise<boolean> {
    const token = await refreshSession()
    return token !== null
  }

  async function fetchProfile(): Promise<UserProfile> {
    const res = await userApi.getProfile()
    // 顶栏昵称 / 头像跟着资料走，省掉一次额外请求
    if (user.value && (res.data.nickname || res.data.avatar)) {
      setUser({
        ...user.value,
        nickname: res.data.nickname || user.value.nickname,
        avatar: res.data.avatar ?? user.value.avatar,
      })
    }
    return res.data
  }

  async function updateProfile(payload: UpdateProfileParams): Promise<UserProfile> {
    const res = await userApi.updateProfile(payload)
    return res.data
  }

  async function logout() {
    const rt = storedRefreshToken.value
    try {
      if (rt) await authApi.logout(rt)
    } catch {
      // 服务端注销失败也要清掉本地态
    } finally {
      clearAuth()
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    nickname,
    role,
    roleLabel,
    isSeeker,
    isRecruiter,
    isAdmin,
    canAccessHr,
    homePath,
    avatarText,
    login,
    register,
    refreshToken,
    fetchProfile,
    updateProfile,
    logout,
  }
})
