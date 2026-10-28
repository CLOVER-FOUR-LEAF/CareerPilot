import { get, post, put } from './request'
import type {
  ActivityEvent,
  ActivityUser,
  AdminUser,
  AiTask,
  AuditLog,
  ConfigItem,
  LoginResponse,
  Overview,
  OverviewTrends,
  PageResult,
  ServiceHealth,
  UserRole,
} from '@/types'

export const authApi = {
  login: (data: { email: string; password: string }) =>
    post<LoginResponse>('/auth/login', data),
  logout: (refreshToken: string) =>
    post<null>('/auth/logout', { refreshToken }),
}

export const adminApi = {
  overview: () => get<Overview>('/admin/stats/overview'),

  trends: () => get<OverviewTrends>('/admin/stats/trends'),

  users: (params: { page: number; size: number; keyword?: string; role?: string }) =>
    get<PageResult<AdminUser>>('/admin/users', params),

  activityUsers: (params: { page: number; size: number; keyword?: string; role?: string }) =>
    get<PageResult<ActivityUser>>('/admin/activity/users', params),

  activityEvents: (params: { page: number; size: number; action?: string; keyword?: string }) =>
    get<PageResult<ActivityEvent>>('/admin/activity/events', params),

  updateRole: (id: number, role: UserRole) =>
    put<void>(`/admin/users/${id}/role`, { role }),

  updateStatus: (id: number, status: number) =>
    put<void>(`/admin/users/${id}/status`, { status }),

  audits: (params: { page: number; size: number }) =>
    get<PageResult<AuditLog>>('/admin/users/audits', params),

  tasks: (params: { page: number; size: number; status?: string; taskType?: string }) =>
    get<PageResult<AiTask>>('/admin/tasks', params),

  configs: () => get<ConfigItem[]>('/admin/configs'),

  updateConfig: (key: string, value: unknown) =>
    put<void>(`/admin/configs/${key}`, { value }),

  services: () => get<ServiceHealth[]>('/admin/services/health'),
}
