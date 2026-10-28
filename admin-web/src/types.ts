export type UserRole = 'USER' | 'RECRUITER' | 'ADMIN'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface AdminUser {
  id: number
  userId: string
  email: string
  nickname: string
  role: UserRole
  status: number
  lastLoginAt: string | null
  createdAt: string
  membershipLevel: number | null
  aiQuotaRemaining: number | null
}

export interface Overview {
  totalUsers: number
  recruiterCount: number
  adminCount: number
  todayNewUsers: number
  totalResumes: number
  totalJds: number
  totalAiTasks: number
  runningAiTasks: number
  failedAiTasks: number
  todayAiTasks: number
  totalInsights: number
  paidOrders: number
  revenueCents: number
}

export interface ConfigItem {
  configKey: string
  value: unknown
  description: string | null
  updatedBy: string | null
  updatedAt: string | null
}

/** 近 14 天趋势的一行 */
export interface TrendPoint {
  date: string
  newUsers: number
  aiTasks: number
  resumes: number
  jds: number
  matches: number
}

export interface NameCount {
  name: string
  count: number
}

export interface OverviewTrends {
  daily: TrendPoint[]
  taskTypes: NameCount[]
  roles: NameCount[]
  taskStatus: NameCount[]
  successRate: number | null
}

/** 用户使用概览 */
export interface ActivityUser {
  id: number
  userId: string
  email: string
  nickname: string
  role: UserRole
  status: number
  lastLoginAt: string | null
  createdAt: string
  resumeCount: number
  jdCount: number
  matchCount: number
  aiTaskCount: number
  insightCount: number
  lastActiveAt: string | null
}

/** 行为流水中的一条 */
export interface ActivityEvent {
  action: string
  userId: string | null
  email: string | null
  summary: string
  createdAt: string
}

/** Agent 编排配置（存在 t_platform_config.agent.registry） */
export interface AgentEntry {
  key: string
  name: string
  probeName?: string
  port: number
  enabled: boolean
  model: string
  timeoutSeconds: number
  description?: string
}

export interface AgentOrchestrator {
  enabled: boolean
  port: number
  endpoint: string
  timeoutSeconds: number
}

export interface AgentRegistry {
  orchestrator: AgentOrchestrator
  agents: AgentEntry[]
}

export interface ServiceHealth {
  name: string
  group: 'java' | 'agent'
  url: string
  up: boolean
  status: number
  latencyMs: number
  error?: string
}

export interface AiTask {
  taskId: string
  taskType: string
  status: string
  progress: number
  currentAgent: string | null
  userId: string | null
  durationMs: number | null
  errorMessage: string | null
  createdAt: string
  completedAt: string | null
}

export interface AuditLog {
  auditId: string
  actorId: string
  action: string
  targetType: string | null
  targetId: string | null
  detail: string | null
  ip: string | null
  createdAt: string
}

export interface AuthUser {
  userId: number
  userIdBiz: string
  email: string
  nickname: string
  avatar: string | null
  membershipLevel: string
  role: UserRole
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}
