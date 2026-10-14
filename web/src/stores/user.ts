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
import type { ApiResponse, LoginResponse, UpdateProfileParams, UserProfile } from '@/types'

export interface RegisterPayload {
  email: string
  password: string
  nickname?: string
  inviteCode?: string | null
}

export const useUserStore = defineStore('user', () => {
  // 与 utils/storage 共享同一份 ref 状态，刷新页面 / 静默刷新都不会失联
  const token = accessToken
  const user = currentUser

  const isLoggedIn = computed(() => token.value.length > 0)
  const nickname = computed(() => user.value?.nickname || '')
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
    avatarText,
    login,
    register,
    refreshToken,
    fetchProfile,
    updateProfile,
    logout,
  }
})
