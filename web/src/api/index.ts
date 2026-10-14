import request from './request'
import type {
  AiTask,
  AiTaskCreateParams,
  AtsScoreResult,
  JdDetail,
  JdListItem,
  JdParseResult,
  JdUploadParams,
  JdUploadResult,
  LoginParams,
  LoginResponse,
  MatchEvaluateParams,
  MatchEvaluateResult,
  MatchListItem,
  MatchRecord,
  Membership,
  PageResult,
  RegisterParams,
  ReparseResult,
  ResumeDetail,
  ResumeListItem,
  ResumeUploadResult,
  ResumeVersion,
  UpdateProfileParams,
  UserProfile,
} from '@/types'

export interface ListParams {
  page?: number
  size?: number
  keyword?: string
}

// ─── §1 auth ─────────────────────────────────────────────────

export const authApi = {
  /** §1.1 注册即登录，返回完整登录响应 */
  register: (data: RegisterParams) => request.post<LoginResponse>('/auth/register', data),
  /** §1.2 登录 */
  login: (data: LoginParams) => request.post<LoginResponse>('/auth/login', data),
  /** §1.3 退出登录（携带 refreshToken 供服务端加黑名单） */
  logout: (refreshToken?: string | null) =>
    request.post<null>('/auth/logout', refreshToken ? { refreshToken } : {}),
  /** §1.4 刷新令牌 */
  refresh: (refreshToken: string) => request.post<LoginResponse>('/auth/refresh', { refreshToken }),
}

// ─── §1 user ─────────────────────────────────────────────────

export const userApi = {
  /** §1.5 读取资料 */
  getProfile: () => request.get<UserProfile>('/user/profile'),
  /** §1.5 更新资料 */
  updateProfile: (data: UpdateProfileParams) => request.put<UserProfile>('/user/profile', data),
  /** §1.6 会员信息 */
  getMembership: () => request.get<Membership>('/user/membership'),
}

// ─── §2 resume ───────────────────────────────────────────────

export const resumeApi = {
  /** §2.1 上传（multipart） */
  upload: (file: File, title?: string) => {
    const form = new FormData()
    form.append('file', file)
    if (title) form.append('title', title)
    return request.upload<ResumeUploadResult>('/resume/upload', form)
  },
  /** §2.2 列表（分页） */
  list: (params?: ListParams) =>
    request.get<PageResult<ResumeListItem>>('/resume/list', { params: normalizeList(params) }),
  /** §2.3 详情（路径参数为数据库主键） */
  getById: (id: number | string) => request.get<ResumeDetail>(`/resume/${id}`),
  delete: (id: number | string) => request.delete<null>(`/resume/${id}`),
  /** §2.4 ATS 评分（AnalysisResult 原结构） */
  getAtsScore: (id: number | string) =>
    request.get<AtsScoreResult>(`/resume/${id}/ats-score`, { silent: true }),
  /** §2.5 版本列表 */
  getVersions: (id: number | string) => request.get<ResumeVersion[]>(`/resume/${id}/versions`),
  /** §2.6 重新分析 */
  reparse: (id: number | string) => request.post<ReparseResult>(`/resume/${id}/reparse`),
}

// ─── §3 jd ───────────────────────────────────────────────────

export const jdApi = {
  /** §3.1 新建 JD */
  upload: (data: JdUploadParams) => request.post<JdUploadResult>('/jd/upload', data),
  /** §3.2 列表（分页） */
  list: (params?: ListParams & { company?: string }) =>
    request.get<PageResult<JdListItem>>('/jd/list', { params: normalizeList(params) }),
  /** §3.4 详情 */
  getById: (id: number | string) => request.get<JdDetail>(`/jd/${id}`),
  delete: (id: number | string) => request.delete<null>(`/jd/${id}`),
  /** §3.3 解析结果（JdChunk 原结构） */
  getParseResult: (id: number | string) =>
    request.get<JdParseResult>(`/jd/${id}/parse/result`, { silent: true }),
  /** §3.4 重新解析 */
  reparse: (id: number | string) => request.post<ReparseResult>(`/jd/${id}/reparse`),
}

// ─── §4 match ────────────────────────────────────────────────

export const matchApi = {
  /** §4.1 发起评估（两个 ID 均为数据库主键） */
  evaluate: (data: MatchEvaluateParams) => request.post<MatchEvaluateResult>('/match/evaluate', data),
  /** §4.2 评估详情 */
  get: (recordId: string) => request.get<MatchRecord>(`/match/${recordId}`, { silent: true }),
  /** §4.3 评估历史（分页，省略 gapList） */
  list: (params?: ListParams) =>
    request.get<PageResult<MatchListItem>>('/match/list', { params: normalizeList(params) }),
}

// ─── §5 ai-tasks ─────────────────────────────────────────────

export const aiTaskApi = {
  /** §5.1 创建任务，立即返回任务快照 */
  create: <T = unknown>(data: AiTaskCreateParams) => request.post<AiTask<T>>('/ai-tasks', data),
  /** §5.2 查询任务 */
  get: <T = unknown>(taskId: string) => request.get<AiTask<T>>(`/ai-tasks/${taskId}`, { silent: true }),
}

// ─── 工具 ────────────────────────────────────────────────────

/** 去掉空值，避免往 URL 上拼 undefined */
function normalizeList<T extends object>(params?: T): Record<string, unknown> | undefined {
  if (!params) return undefined
  const source = params as unknown as Record<string, unknown>
  const out: Record<string, unknown> = {}
  Object.entries(source).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') out[key] = value
  })
  return Object.keys(out).length ? out : undefined
}

export { createSseStream } from './sse'
