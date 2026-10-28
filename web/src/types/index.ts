/**
 * 全局共享类型定义
 * 严格对应 docs/API-CONTRACT.md
 */

// ─── 通用包络 ────────────────────────────────────────────────

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  items: T[]
}

/** 业务错误（拦截器抛出的） */
export interface ApiError extends Error {
  code: number
  traceId?: string
}

// ─── §1 user-center ─────────────────────────────────────────

export type MembershipLevel = 'FREE' | 'PRO' | 'ULTRA'

/** 授权角色：求职者 / 招聘方 / 平台管理员 */
export type UserRole = 'USER' | 'RECRUITER' | 'ADMIN'

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

export interface LoginParams {
  email: string
  password: string
}

export interface RegisterParams {
  email: string
  password: string
  nickname?: string
  inviteCode?: string | null
}

export interface RefreshParams {
  refreshToken: string
}

export interface LogoutParams {
  refreshToken?: string | null
}

export interface UserProfile {
  userId: number
  email: string
  phone: string | null
  nickname: string
  avatar: string | null
  role: UserRole
  gender: string | null
  birthYear: number | null
  city: string | null
  currentTitle: string | null
  yearsOfExperience: number | null
  educationLevel: string | null
  school: string | null
  major: string | null
  graduationYear: number | null
  targetIndustry: string | null
  targetPosition: string | null
  targetSeniority: string | null
  targetCity: string[]
  targetSalaryMin: number | null
  targetSalaryMax: number | null
  skills: string[]
  createdAt: string
  updatedAt: string
}

export type UpdateProfileParams = Partial<Omit<UserProfile, 'userId' | 'email' | 'createdAt' | 'updatedAt'>>

export interface Membership {
  level: string
  levelName: string
  expireAt: string | null
  autoRenew: boolean
  aiQuotaRemaining: number
  aiQuotaTotal: number
}

// ─── §2 resume-service ──────────────────────────────────────

export type ParseStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'

export interface ResumeListItem {
  resumeId: number
  resumeBizId: string
  title: string
  atsScore: number | null
  parseStatus: ParseStatus
  isPrimary: boolean
  updatedAt: string
}

export interface ResumeUploadResult {
  resumeId: number
  resumeBizId: string
  versionId: string
  title: string
  parseStatus: ParseStatus
  taskId: string
  createdAt: string
}

export interface ResumeDetail {
  resumeId: number
  resumeBizId: string
  title: string
  atsScore: number | null
  parseStatus: ParseStatus
  textContent: string
  createdAt: string
  updatedAt: string
}

/** §2.4 Agent AnalysisResult 原结构（蛇形命名，后端不二次包装） */
export interface AtsDimension {
  name: string
  score: number
  weight: number
  feedback: string
}

export interface AtsSkill {
  name: string
  category: string
  proficiency: string
}

export interface AtsSuggestion {
  category: string
  priority: string
  title: string
  detail: string
}

export interface AtsScoreResult {
  resume_id: string
  overall_score: number
  dimensions: AtsDimension[]
  skills: AtsSkill[]
  suggestions: AtsSuggestion[]
  section_count: number
  word_count: number
  has_contact: boolean
  updatedAt?: string
}

export interface ResumeVersion {
  versionId: string
  versionNo: number
  atsScore: number | null
  changeNote: string | null
  createdAt: string
}

export interface ReparseResult {
  taskId: string
  parseStatus: ParseStatus
}

// ─── §3 jd-service ──────────────────────────────────────────

export type JdSourceType = 'MANUAL' | 'URL' | 'FILE'

export interface JdUploadParams {
  jobTitle: string
  companyName?: string | null
  rawText: string
  sourceType?: JdSourceType
  sourceUrl?: string | null
  salaryRange?: string | null
}

export interface JdUploadResult {
  jdId: number
  jdBizId: string
  title: string
  company: string | null
  parseStatus: ParseStatus
  taskId: string
  createdAt: string
}

export interface JdListItem {
  jdId: number
  jdBizId: string
  title: string
  company: string | null
  parseStatus: ParseStatus
  sourceUrl: string | null
  rawTextPreview: string
  createdAt: string
}

export interface JdDetail {
  jdId: number
  jdBizId: string
  title: string
  company: string | null
  salaryRange: string | null
  rawText: string
  sourceUrl: string | null
  sourceType: JdSourceType
  parseStatus: ParseStatus
  createdAt: string
  updatedAt: string
}

export interface JdSkill {
  name: string
  category: string
  importance: string
  years_required: number | null
}

/** §3.3 Agent JdChunk 原结构 */
export interface JdParseResult {
  job_title: string
  company_name: string | null
  department: string | null
  location: string | null
  employment_type: string | null
  salary_min: number | null
  salary_max: number | null
  salary_currency: string | null
  experience_min_years: number | null
  experience_max_years: number | null
  education_required: string | null
  skills: JdSkill[]
  responsibilities: string[]
  requirements: string[]
  benefits: string[]
  raw_text: string
  parse_score: number
  parse_method: string
  seniority: string | null
  industry: string | null
  createdAt: string
}

// ─── §4 match-service ───────────────────────────────────────

export interface MatchGap {
  gapType: string
  gapContent: string
  severity: number
  suggestion: string
}

export interface MatchRecord {
  recordId: string
  resumeId: number
  jdId: number
  status: string
  taskId: string | null
  semanticScore: number | null
  keywordScore: number | null
  businessScore: number | null
  finalScore: number | null
  matchedSkills: string[]
  gapList: MatchGap[]
  errorMessage?: string | null
  createdAt: string
  completedAt: string | null
}

export type MatchListItem = Omit<MatchRecord, 'gapList'>

export interface MatchEvaluateParams {
  resumeId: number
  jdId: number
}

export interface MatchEvaluateResult {
  recordId: string
  resumeId: number
  jdId: number
  status: string
  taskId: string
  createdAt: string
}

// ─── §5 ai-task-scheduler ───────────────────────────────────

export type AiTaskType =
  | 'jd_parse'
  | 'resume_analyze'
  | 'match_score'
  | 'resume_optimize'
  | 'interview'
  | 'cover_letter'
  | 'career_plan'

export type AiTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'

export interface AiTask<TOutput = unknown> {
  taskId: string
  userId: string
  taskType: AiTaskType | string
  bizId: string | null
  status: AiTaskStatus
  progress: number | null
  currentAgent: string | null
  input: Record<string, unknown> | null
  output: TOutput | null
  errorMessage: string | null
  retryCount: number
  durationMs: number | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AiTaskCreateParams {
  taskType: AiTaskType
  bizId?: string | null
  input: Record<string, unknown>
}

/** §5.4 SSE 推送体 */
export interface TaskStreamEvent<TOutput = unknown> {
  taskId: string
  status: AiTaskStatus
  progress?: number
  currentAgent?: string | null
  errorMessage?: string | null
  output?: TOutput
}

// ─── §7 Agent 输出结构 ──────────────────────────────────────

export interface ResumeOptimizeResult {
  original: string
  rewritten: string
  injected_skills: string[]
  star_breakdown: {
    situation: string
    task: string
    action: string
    result: string
  }
  change_note: string
}

export type InterviewPhase = 'question' | 'evaluate'

export interface InterviewQuestion {
  round_no: number
  question: string
  question_type: string
  focus: string
}

export interface StarCoverage {
  situation: boolean
  task: boolean
  action: boolean
  result: boolean
}

export interface InterviewEvaluation {
  score: number
  feedback: string
  star_coverage: StarCoverage
  followup: string
}

export type CoverLetterTone = 'professional' | 'passionate' | 'humble'

export interface CoverLetterResult {
  tone: CoverLetterTone | string
  content: string
  highlighted_skills: string[]
  word_count: number
}

export interface CareerPlanPhase {
  phase: string
  goal: string
  actions: string[]
  milestone: string
  doc_ids: string[]
}

export interface CareerPlanCitation {
  doc_id: string
  source: string
  version: string
}

export interface CareerPlanResult {
  summary: string
  roadmap: CareerPlanPhase[]
  skill_gap: string[]
  citations: CareerPlanCitation[]
}

// ─── §8 HR 侧（招聘方视角）────────────────────────────────────

/**
 * 覆盖判定。只有这三个值。
 * not_evidenced 的含义是「本次提问未获得证据」，**不是**「候选人不具备该能力」。
 */
export type VerificationVerdict = 'demonstrated' | 'adjacent' | 'not_evidenced'

export interface VerificationSignal {
  verdict: VerificationVerdict
  evidence: string
  keywords: string[]
}

export interface VerificationProbe {
  question: string
  trigger: string
}

export interface VerificationQuestion {
  id: string
  gap_ref: string
  dimension: string
  dimension_name: string
  severity: number
  question: string
  intent: string
  probes: VerificationProbe[]
  signals: VerificationSignal[]
  rule_generated: boolean
}

export interface GapAttribution {
  gap_ref: string
  gap_type: string
  gap_content: string
  severity: number
  gap_category: string
  dimension: string
  dimension_name: string
  required_level: number
  evidence_level: number
  gap_kind: string
  explanation: string
  evidence_ref: string
  llm_used: boolean
}

export interface CapabilityCoverage {
  dimension: string
  dimension_name: string
  required_level: number
  evidence_level: number
  gap_refs: string[]
}

export interface HrMatchOverview {
  final_score: number
  semantic_score: number
  keyword_score: number
  business_score: number
  matched_skills: string[]
}

export interface HrInsightReport {
  match_overview: HrMatchOverview
  capability_coverage: CapabilityCoverage[]
  attributions: GapAttribution[]
  verification_plan: VerificationQuestion[]
  /** 免责与审计声明 —— 由后端代码拼接，不由模型生成 */
  disclaimer: string
  audit_note: string
  gap_count: number
  question_count: number
  dimension_count: number
}

export interface HrInsightCreated {
  insightId: string
  taskId: string | null
  status: string
}

export interface HrInsightListItem {
  insightId: string
  jdId: string | null
  resumeId: string | null
  status: string
  createdAt: string
}

export interface HrVerifySubmitted {
  recordId: string
  taskId: string | null
  status: string
  insightId: string
  vqId: string
}

export interface HrInsightDetail {
  insightId: string
  taskId: string | null
  jdId: string | null
  resumeId: string | null
  status: string
  report: HrInsightReport | null
  capabilityCoverage: CapabilityCoverage[]
  gapPlan: VerificationQuestion[]
  createdAt: string
}

export interface HrVerifyResult {
  vq_id: string
  coverage: VerificationVerdict
  confidence: number
  matched_signal_index: number
  reason: string
  evidence_ref: string
  should_probe: boolean
  suggested_probes: string[]
}

export interface VerificationRecord {
  recordId: string
  vqId: string
  roundNo: number
  question: string | null
  answer: string | null
  coverage: VerificationVerdict | null
  confidence: number | null
  reason: string | null
  evidenceRef: string | null
  createdAt: string
}

export interface JdCalibration {
  capability_model: {
    dimension: string
    dimension_name: string
    required_level: number
    evidence_level: number
  }[]
  hard_gates: {
    item: string
    type: string
    one_vote_veto: boolean
  }[]
  over_filtering_signals: {
    signal: string
    risk: string
  }[]
  vague_phrases: {
    phrase: string
    why_vague: string
    suggestion: string
  }[]
  disclaimer: string
}
