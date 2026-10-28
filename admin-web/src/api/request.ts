import axios from 'axios'
import { message } from 'ant-design-vue'
import { clear, getToken } from '@/utils/token'
import type { ApiResponse } from '@/types'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 20000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    // 管理端所有接口都走统一包络，code !== 0 一律当业务失败
    if (body && typeof body.code === 'number' && body.code !== 0) {
      message.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      clear()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    } else if (status === 403) {
      message.error('需要平台管理员权限')
    } else {
      message.error(error.response?.data?.message || error.message || '请求失败')
    }
    return Promise.reject(error)
  },
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.get<ApiResponse<T>>(url, { params })
  return res.data.data
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.put<ApiResponse<T>>(url, data)
  return res.data.data
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.post<ApiResponse<T>>(url, data)
  return res.data.data
}

export default http
