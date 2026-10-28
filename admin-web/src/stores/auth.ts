import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { clear, getRefreshToken, getToken, getUser, persist } from '@/utils/token'
import type { AuthUser } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(getUser())
  const token = ref<string>(getToken())

  const isLoggedIn = computed(() => token.value.length > 0)
  const nickname = computed(() => user.value?.nickname || user.value?.email || '管理员')

  async function login(email: string, password: string) {
    const data = await authApi.login({ email, password })

    // 管理端只接受 ADMIN；普通用户/招聘方即使密码正确也不能进
    if (data.user?.role !== 'ADMIN') {
      throw new Error('该账号不是平台管理员')
    }

    persist(data.accessToken, data.refreshToken, data.user)
    user.value = data.user
    token.value = data.accessToken
    return data
  }

  async function logout() {
    const rt = getRefreshToken()
    try {
      if (rt) await authApi.logout(rt)
    } catch {
      // 服务端注销失败也要清本地态
    } finally {
      clear()
      user.value = null
      token.value = ''
    }
  }

  return { user, token, isLoggedIn, nickname, login, logout }
})
