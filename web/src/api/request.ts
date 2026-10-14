import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import type { ApiResponse, LoginResponse } from '@/types'
import { accessToken, clearAuth, persistAuth, refreshToken } from '@/utils/storage'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 静默请求：业务错误不弹 message（由调用方自行处理） */
    silent?: boolean
    /** 401 重试标记，避免无限刷新 */
    _retried?: boolean
  }
}

/** 对外暴露的客户端：拦截器直接返回包络，因此返回值就是 ApiResponse */
export interface ApiClient {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
  /** multipart/form-data 上传 */
  upload<T = unknown>(url: string, form: FormData, config?: AxiosRequestConfig): Promise<ApiResponse<T>>
}

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

// ─── 请求拦截：附加 JWT（X-User-Id 由网关注入，前端不发送） ────
instance.interceptors.request.use(
  (config) => {
    if (accessToken.value) {
      config.headers.Authorization = `Bearer ${accessToken.value}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ─── 刷新令牌：并发请求共享同一个 Promise ────────────────────
let refreshing: Promise<string | null> | null = null

const refreshClient = axios.create({ baseURL: '/api/v1', timeout: 15000 })

async function refreshAccessToken(): Promise<string | null> {
  if (!refreshToken.value) return null
  try {
    const res = await refreshClient.post<ApiResponse<LoginResponse>>('/auth/refresh', {
      refreshToken: refreshToken.value,
    })
    const body = res.data
    if (body && body.code === 0 && body.data && body.data.accessToken) {
      persistAuth(body.data)
      return body.data.accessToken
    }
    return null
  } catch {
    return null
  }
}

function ensureRefreshed(): Promise<string | null> {
  if (!refreshing) {
    refreshing = refreshAccessToken().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

/** 供 store 复用的刷新入口（内部对并发调用做了去重） */
export function refreshSession(): Promise<string | null> {
  return ensureRefreshed()
}

async function redirectToLogin() {
  const current = window.location.pathname + window.location.search
  const query = current && current !== '/login' ? `?redirect=${encodeURIComponent(current)}` : ''
  try {
    // 动态引入，避免 request → router → views → api 的循环依赖
    const { default: router } = await import('@/router')
    await router.replace({ path: '/login', query: query ? { redirect: current } : {} })
  } catch {
    window.location.href = `/login${query}`
  }
}

// ─── 响应拦截：返回整个包络（业务取 res.data） ──────────────
instance.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    // 网关 401 是非包络结构，其余接口都是包络
    if (res && typeof res.code === 'number' && res.code !== 0) {
      const err = new Error(res.message || '请求失败') as Error & { code: number; traceId?: string }
      err.code = res.code
      err.traceId = res.traceId
      if (!response.config.silent) {
        message.error(res.message || '请求失败')
      }
      return Promise.reject(err)
    }
    // 运行时返回整个包络（业务取 res.data），类型上按 AxiosResponse 放行
    return res as unknown as AxiosResponse
  },
  async (error: AxiosError) => {
    const status = error.response?.status
    const config = error.config as AxiosRequestConfig | undefined

    if (status === 401) {
      // 先尝试一次刷新令牌重试
      if (config && !config._retried) {
        config._retried = true
        const token = await ensureRefreshed()
        if (token) {
          return instance.request(config)
        }
      }
      clearAuth()
      if (!window.location.pathname.startsWith('/login')) {
        message.warning('登录已过期，请重新登录')
        await redirectToLogin()
      }
      return Promise.reject(error)
    }

    if (!config?.silent) {
      message.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

export default instance as unknown as ApiClient
