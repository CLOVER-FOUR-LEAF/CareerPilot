import { ref } from 'vue'
import type { AuthUser } from '@/types'

/**
 * 令牌 / 用户信息的唯一数据源。
 * request.ts、stores/user.ts、router 守卫共用这里的 ref，保证刷新令牌后内存态与
 * localStorage 始终一致。
 */

const ACCESS_TOKEN_KEY = 'cp_access_token'
const REFRESH_TOKEN_KEY = 'cp_refresh_token'
const USER_KEY = 'cp_user'

function readRaw(key: string): string {
  try {
    return localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

function writeRaw(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 隐私模式下 localStorage 可能不可用，忽略
  }
}

function removeRaw(key: string) {
  try {
    localStorage.removeItem(key)
  } catch {
    // ignore
  }
}

function readUser(): AuthUser | null {
  const raw = readRaw(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    removeRaw(USER_KEY)
    return null
  }
}

export const accessToken = ref<string>(readRaw(ACCESS_TOKEN_KEY))
export const refreshToken = ref<string>(readRaw(REFRESH_TOKEN_KEY))
export const currentUser = ref<AuthUser | null>(readUser())

export function setAccessToken(token: string) {
  accessToken.value = token
  writeRaw(ACCESS_TOKEN_KEY, token)
}

export function setRefreshToken(token: string) {
  refreshToken.value = token
  writeRaw(REFRESH_TOKEN_KEY, token)
}

export function setUser(user: AuthUser | null) {
  currentUser.value = user
  if (user) {
    writeRaw(USER_KEY, JSON.stringify(user))
  } else {
    removeRaw(USER_KEY)
  }
}

/** 登录 / 注册 / 刷新的统一落盘入口 */
export function persistAuth(payload: { accessToken: string; refreshToken?: string; user?: AuthUser }) {
  setAccessToken(payload.accessToken)
  if (payload.refreshToken) setRefreshToken(payload.refreshToken)
  if (payload.user) setUser(payload.user)
}

export function clearAuth() {
  accessToken.value = ''
  refreshToken.value = ''
  currentUser.value = null
  removeRaw(ACCESS_TOKEN_KEY)
  removeRaw(REFRESH_TOKEN_KEY)
  removeRaw(USER_KEY)
}

export function isAuthenticated(): boolean {
  return accessToken.value.length > 0
}
