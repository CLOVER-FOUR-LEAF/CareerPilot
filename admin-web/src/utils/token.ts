import type { AuthUser } from '@/types'

/**
 * 管理端使用独立的 localStorage key，和用户端（cp_*）刻意隔离：
 * 同一台浏览器同时打开两个前端时，不会互相覆盖身份。
 */
const ACCESS_KEY = 'cp_admin_access_token'
const REFRESH_KEY = 'cp_admin_refresh_token'
const USER_KEY = 'cp_admin_user'

function read(key: string): string {
  try {
    return localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 隐私模式忽略
  }
}

export function getToken(): string {
  return read(ACCESS_KEY)
}

export function getRefreshToken(): string {
  return read(REFRESH_KEY)
}

export function getUser(): AuthUser | null {
  const raw = read(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

export function persist(accessToken: string, refreshToken: string, user: AuthUser) {
  write(ACCESS_KEY, accessToken)
  write(REFRESH_KEY, refreshToken)
  write(USER_KEY, JSON.stringify(user))
}

export function clear() {
  try {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
  } catch {
    // ignore
  }
}
