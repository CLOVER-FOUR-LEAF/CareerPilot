package com.careerpilot.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ─── 通用包络 ────────────────────────────────────────────────

/** 所有 Java 服务返回的统一包络（`ApiResponse<T>`）。 */
@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
    val traceId: String? = null,
)

/** 分页包络。 */
@Serializable
data class PageResult<T>(
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 0,
    val items: List<T> = emptyList(),
)

// ─── §1 user-center ─────────────────────────────────────────

@Serializable
data class AuthUser(
    val userId: Long = 0,
    val userIdBiz: String = "",
    val email: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val membershipLevel: String = "FREE",
)

@Serializable
data class LoginResponse(
    val accessToken: String = "",
    val refreshToken: String = "",
    val tokenType: String = "Bearer",
    val expiresIn: Long = 0,
    val user: AuthUser? = null,
)

@Serializable
data class LoginParams(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterParams(
    val email: String,
    val password: String,
    val nickname: String? = null,
    val inviteCode: String? = null,
)

@Serializable
data class RefreshParams(
    val refreshToken: String,
)

@Serializable
data class LogoutParams(
    val refreshToken: String? = null,
)

@Serializable
data class UserProfile(
    val userId: Long = 0,
    val email: String = "",
    val phone: String? = null,
    val nickname: String = "",
    val avatar: String? = null,
    val gender: String? = null,
    val birthYear: Int? = null,
    val city: String? = null,
    val currentTitle: String? = null,
    val yearsOfExperience: Int? = null,
    val educationLevel: String? = null,
    val school: String? = null,
    val major: String? = null,
    val graduationYear: Int? = null,
    val targetIndustry: String? = null,
    val targetPosition: String? = null,
    val targetSeniority: String? = null,
    val targetCity: List<String>? = null,
    val targetSalaryMin: Int? = null,
    val targetSalaryMax: Int? = null,
    val skills: List<String>? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/**
 * 资料更新请求。字段全为可空，配合 `explicitNulls = false`：
 * 未修改的字段不会出现在 JSON 里，不会把服务端的数据清空。
 */
@Serializable
data class UpdateProfileParams(
    val nickname: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val birthYear: Int? = null,
    val city: String? = null,
    val currentTitle: String? = null,
    val yearsOfExperience: Int? = null,
    val educationLevel: String? = null,
    val school: String? = null,
    val major: String? = null,
    val graduationYear: Int? = null,
    val targetIndustry: String? = null,
    val targetPosition: String? = null,
    val targetSeniority: String? = null,
    val targetCity: List<String>? = null,
    val targetSalaryMin: Int? = null,
    val targetSalaryMax: Int? = null,
    val skills: List<String>? = null,
)

@Serializable
data class Membership(
    val level: String = "FREE",
    val levelName: String = "",
    val expireAt: String? = null,
    val autoRenew: Boolean = false,
    val aiQuotaRemaining: Int = 0,
    val aiQuotaTotal: Int = 0,
)

// ─── §2 resume-service ──────────────────────────────────────

@Serializable
data class ResumeListItem(
    val resumeId: Long = 0,
    val resumeBizId: String = "",
    val title: String = "",
    val atsScore: Int? = null,
    val parseStatus: String = "PENDING",
    val isPrimary: Boolean = false,
    val updatedAt: String = "",
)

@Serializable
data class ResumeUploadResult(
    val resumeId: Long = 0,
    val resumeBizId: String = "",
    val versionId: String = "",
    val title: String = "",
    val parseStatus: String = "PENDING",
    val taskId: String = "",
    val createdAt: String = "",
)

@Serializable
data class ResumeDetail(
    val resumeId: Long = 0,
    val resumeBizId: String = "",
    val title: String = "",
    val atsScore: Int? = null,
    val parseStatus: String = "PENDING",
    val textContent: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** ATS 评分结果 —— 后端不做二次包装，这里保持 Agent 的原结构（蛇形命名）。 */
@Serializable
data class AtsScoreResult(
    @SerialName("resume_id") val resumeId: String = "",
    @SerialName("overall_score") val overallScore: Double = 0.0,
    val dimensions: List<AtsDimension>? = null,
    val skills: List<AtsSkill>? = null,
    val suggestions: List<AtsSuggestion>? = null,
    @SerialName("section_count") val sectionCount: Int = 0,
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("has_contact") val hasContact: Boolean = false,
    val updatedAt: String? = null,
)

@Serializable
data class AtsDimension(
    val name: String = "",
    val score: Double = 0.0,
    val weight: Double = 0.0,
    val feedback: String = "",
)

@Serializable
data class AtsSkill(
    val name: String = "",
    val category: String = "",
    val proficiency: String = "",
)

@Serializable
data class AtsSuggestion(
    val category: String = "",
    val priority: String = "",
    val title: String = "",
    val detail: String = "",
)

@Serializable
data class ResumeVersion(
    val versionId: String = "",
    val versionNo: Int = 0,
    val atsScore: Int? = null,
    val changeNote: String? = null,
    val createdAt: String = "",
)

@Serializable
data class ReparseResult(
    val taskId: String = "",
    val parseStatus: String = "PENDING",
)

// ─── §3 jd-service ──────────────────────────────────────────

@Serializable
data class JdUploadParams(
    val jobTitle: String,
    val companyName: String? = null,
    val rawText: String,
    val sourceType: String = "MANUAL",
    val sourceUrl: String? = null,
    val salaryRange: String? = null,
)

@Serializable
data class JdUploadResult(
    val jdId: Long = 0,
    val jdBizId: String = "",
    val title: String = "",
    val company: String? = null,
    val parseStatus: String = "PENDING",
    val taskId: String = "",
    val createdAt: String = "",
)

@Serializable
data class JdListItem(
    val jdId: Long = 0,
    val jdBizId: String = "",
    val title: String = "",
    val company: String? = null,
    val parseStatus: String = "PENDING",
    val sourceUrl: String? = null,
    val rawTextPreview: String = "",
    val createdAt: String = "",
)

@Serializable
data class JdDetail(
    val jdId: Long = 0,
    val jdBizId: String = "",
    val title: String = "",
    val company: String? = null,
    val salaryRange: String? = null,
    val rawText: String = "",
    val sourceUrl: String? = null,
    val sourceType: String = "MANUAL",
    val parseStatus: String = "PENDING",
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class JdSkill(
    val name: String = "",
    val category: String = "",
    val importance: String = "",
    @SerialName("years_required") val yearsRequired: Int? = null,
)

/** JD 解析结果 —— Agent `JdChunk` 原结构。 */
@Serializable
data class JdParseResult(
    @SerialName("job_title") val jobTitle: String = "",
    @SerialName("company_name") val companyName: String? = null,
    val department: String? = null,
    val location: String? = null,
    @SerialName("employment_type") val employmentType: String? = null,
    @SerialName("salary_min") val salaryMin: Int? = null,
    @SerialName("salary_max") val salaryMax: Int? = null,
    @SerialName("salary_currency") val salaryCurrency: String? = null,
    @SerialName("experience_min_years") val experienceMinYears: Int? = null,
    @SerialName("experience_max_years") val experienceMaxYears: Int? = null,
    @SerialName("education_required") val educationRequired: String? = null,
    val skills: List<JdSkill>? = null,
    val responsibilities: List<String>? = null,
    val requirements: List<String>? = null,
    val benefits: List<String>? = null,
    @SerialName("raw_text") val rawText: String = "",
    @SerialName("parse_score") val parseScore: Double = 0.0,
    @SerialName("parse_method") val parseMethod: String = "",
    val seniority: String? = null,
    val industry: String? = null,
    val createdAt: String? = null,
)

// ─── §4 match-service ───────────────────────────────────────

@Serializable
data class MatchGap(
    val gapType: String = "",
    val gapContent: String = "",
    val severity: Int = 0,
    val suggestion: String = "",
)

@Serializable
data class MatchRecord(
    val recordId: String = "",
    /**
     * 匹配记录里存的是**业务 ID**（`res_xxx` / `jd_xxx`）而不是数据库主键。
     * 参见 `docs/API-FRONTEND.md` §1.6：MatchService 落库时写的是业务 ID。
     */
    val resumeId: String = "",
    val jdId: String = "",
    val status: String = "",
    val taskId: String? = null,
    val semanticScore: Double? = null,
    val keywordScore: Double? = null,
    val businessScore: Double? = null,
    val finalScore: Double? = null,
    val matchedSkills: List<String>? = null,
    val gapList: List<MatchGap>? = null,
    val errorMessage: String? = null,
    val createdAt: String = "",
    val completedAt: String? = null,
)

@Serializable
data class MatchEvaluateParams(
    val resumeId: Long,
    val jdId: Long,
)

@Serializable
data class MatchEvaluateResult(
    val recordId: String = "",
    val resumeId: Long = 0,
    val jdId: Long = 0,
    val status: String = "",
    val taskId: String = "",
    val createdAt: String = "",
)

// ─── §5 ai-task-scheduler ───────────────────────────────────

/**
 * AI 任务快照。
 *
 * `output` 保持成 `JsonElement`：同一个 taskType 下不同阶段的输出结构并不相同
 * （例如 interview 的 question 阶段是题目，evaluate 阶段是评分），
 * 由各页面按需反序列化成具体类型。
 */
@Serializable
data class AiTask(
    val taskId: String = "",
    val userId: String = "",
    val taskType: String = "",
    val bizId: String? = null,
    val status: String = "",
    val progress: Int? = null,
    val currentAgent: String? = null,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val durationMs: Long? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** SSE 推送体（§5.4）。 */
@Serializable
data class TaskStreamEvent(
    val taskId: String = "",
    val status: String = "",
    val progress: Int? = null,
    val currentAgent: String? = null,
    val errorMessage: String? = null,
    val output: JsonElement? = null,
)

// ─── §7 Agent 输出结构 ──────────────────────────────────────

@Serializable
data class ResumeOptimizeResult(
    val original: String = "",
    val rewritten: String = "",
    @SerialName("injected_skills") val injectedSkills: List<String>? = null,
    @SerialName("star_breakdown") val starBreakdown: StarBreakdown? = null,
    @SerialName("change_note") val changeNote: String = "",
)

@Serializable
data class StarBreakdown(
    val situation: String = "",
    val task: String = "",
    val action: String = "",
    val result: String = "",
)

@Serializable
data class InterviewQuestion(
    @SerialName("round_no") val roundNo: Int = 0,
    val question: String = "",
    @SerialName("question_type") val questionType: String = "",
    val focus: String = "",
)

@Serializable
data class InterviewEvaluation(
    val score: Double = 0.0,
    val feedback: String = "",
    @SerialName("star_coverage") val starCoverage: StarCoverage? = null,
    val followup: String = "",
)

@Serializable
data class StarCoverage(
    val situation: Boolean = false,
    val task: Boolean = false,
    val action: Boolean = false,
    val result: Boolean = false,
)

@Serializable
data class CoverLetterResult(
    val tone: String = "",
    val content: String = "",
    @SerialName("highlighted_skills") val highlightedSkills: List<String>? = null,
    @SerialName("word_count") val wordCount: Int = 0,
)

@Serializable
data class CareerPlanResult(
    val summary: String = "",
    val roadmap: List<CareerPlanPhase>? = null,
    @SerialName("skill_gap") val skillGap: List<String>? = null,
    val citations: List<CareerPlanCitation>? = null,
)

@Serializable
data class CareerPlanPhase(
    val phase: String = "",
    val goal: String = "",
    val actions: List<String>? = null,
    val milestone: String = "",
    @SerialName("doc_ids") val docIds: List<String>? = null,
)

@Serializable
data class CareerPlanCitation(
    @SerialName("doc_id") val docId: String = "",
    val source: String = "",
    val version: String = "",
)
