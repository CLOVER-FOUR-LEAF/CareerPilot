package com.careerpilot.app.data.repo

import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.core.TokenStore
import com.careerpilot.app.data.api.ApiClient
import com.careerpilot.app.data.model.AiTask
import com.careerpilot.app.data.model.AtsScoreResult
import com.careerpilot.app.data.model.JdDetail
import com.careerpilot.app.data.model.JdListItem
import com.careerpilot.app.data.model.JdParseResult
import com.careerpilot.app.data.model.JdUploadParams
import com.careerpilot.app.data.model.JdUploadResult
import com.careerpilot.app.data.model.LoginParams
import com.careerpilot.app.data.model.LoginResponse
import com.careerpilot.app.data.model.LogoutParams
import com.careerpilot.app.data.model.MatchEvaluateResult
import com.careerpilot.app.data.model.MatchRecord
import com.careerpilot.app.data.model.Membership
import com.careerpilot.app.data.model.PageResult
import com.careerpilot.app.data.model.RegisterParams
import com.careerpilot.app.data.model.ReparseResult
import com.careerpilot.app.data.model.ResumeDetail
import com.careerpilot.app.data.model.ResumeListItem
import com.careerpilot.app.data.model.ResumeUploadResult
import com.careerpilot.app.data.model.ResumeVersion
import com.careerpilot.app.data.model.UpdateProfileParams
import com.careerpilot.app.data.model.UserProfile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 业务接口封装。
 *
 * 所有路径都对应 `docs/API-FRONTEND.md` 里的对外 API；
 * URL 用**数据库主键**，传给 AI Agent 的 `bizId` 用**业务 ID**。
 */
class CareerPilotRepository(private val tokenStore: TokenStore) {

    private val json get() = ApiClient.json

    // ─── §1 认证 ────────────────────────────────────────────

    suspend fun login(email: String, password: String): ApiResult<LoginResponse> {
        val payload = json.encodeToString(LoginParams.serializer(), LoginParams(email, password))
        val result = ApiClient.execute(
            method = "POST",
            path = "auth/login",
            serializer = LoginResponse.serializer(),
            body = ApiClient.jsonBody(payload),
        )
        if (result is ApiResult.Success) persistAuth(result.data)
        return result
    }

    suspend fun register(
        email: String,
        password: String,
        nickname: String?,
        inviteCode: String?,
    ): ApiResult<LoginResponse> {
        val payload = json.encodeToString(
            RegisterParams.serializer(),
            RegisterParams(email, password, nickname, inviteCode),
        )
        val result = ApiClient.execute(
            method = "POST",
            path = "auth/register",
            serializer = LoginResponse.serializer(),
            body = ApiClient.jsonBody(payload),
        )
        if (result is ApiResult.Success) persistAuth(result.data)
        return result
    }

    suspend fun logout(): ApiResult<Unit> {
        val refresh = tokenStore.refreshToken
        val payload = json.encodeToString(LogoutParams.serializer(), LogoutParams(refresh))
        val result = ApiClient.executeEmpty(
            method = "POST",
            path = "auth/logout",
            body = ApiClient.jsonBody(payload),
        )
        tokenStore.clearAuth()
        return result
    }

    private fun persistAuth(data: LoginResponse) {
        tokenStore.accessToken = data.accessToken
        if (data.refreshToken.isNotBlank()) {
            tokenStore.refreshToken = data.refreshToken
        }
        data.user?.let {
            tokenStore.userJson = json.encodeToString(
                com.careerpilot.app.data.model.AuthUser.serializer(),
                it,
            )
        }
    }

    // ─── §1 用户资料 ────────────────────────────────────────

    suspend fun getProfile(): ApiResult<UserProfile> = ApiClient.execute(
        method = "GET",
        path = "user/profile",
        serializer = UserProfile.serializer(),
    )

    suspend fun updateProfile(params: UpdateProfileParams): ApiResult<UserProfile> {
        val payload = json.encodeToString(UpdateProfileParams.serializer(), params)
        return ApiClient.execute(
            method = "PUT",
            path = "user/profile",
            serializer = UserProfile.serializer(),
            body = ApiClient.jsonBody(payload),
        )
    }

    suspend fun getMembership(): ApiResult<Membership> = ApiClient.execute(
        method = "GET",
        path = "user/membership",
        serializer = Membership.serializer(),
    )

    // ─── §2 简历 ────────────────────────────────────────────

    suspend fun uploadResume(
        fileName: String,
        mimeType: String,
        content: ByteArray,
        title: String?,
    ): ApiResult<ResumeUploadResult> = ApiClient.execute(
        method = "POST",
        path = "resume/upload",
        serializer = ResumeUploadResult.serializer(),
        body = ApiClient.uploadBody(fileName, mimeType, content, title),
    )

    suspend fun listResumes(
        page: Int = 1,
        size: Int = 20,
        keyword: String? = null,
    ): ApiResult<PageResult<ResumeListItem>> = ApiClient.execute(
        method = "GET",
        path = "resume/list",
        serializer = PageResult.serializer(ResumeListItem.serializer()),
        query = mapOf("page" to page.toString(), "size" to size.toString(), "keyword" to keyword),
    )

    suspend fun getResume(id: Long): ApiResult<ResumeDetail> = ApiClient.execute(
        method = "GET",
        path = "resume/$id",
        serializer = ResumeDetail.serializer(),
    )

    suspend fun deleteResume(id: Long): ApiResult<Unit> = ApiClient.executeEmpty(
        method = "DELETE",
        path = "resume/$id",
    )

    suspend fun getAtsScore(id: Long): ApiResult<AtsScoreResult> = ApiClient.execute(
        method = "GET",
        path = "resume/$id/ats-score",
        serializer = AtsScoreResult.serializer(),
    )

    suspend fun getResumeVersions(id: Long): ApiResult<List<ResumeVersion>> = ApiClient.execute(
        method = "GET",
        path = "resume/$id/versions",
        serializer = ListSerializer(ResumeVersion.serializer()),
    )

    suspend fun reparseResume(id: Long): ApiResult<ReparseResult> = ApiClient.execute(
        method = "POST",
        path = "resume/$id/reparse",
        serializer = ReparseResult.serializer(),
    )

    // ─── §3 JD ──────────────────────────────────────────────

    suspend fun createJd(params: JdUploadParams): ApiResult<JdUploadResult> {
        val payload = json.encodeToString(JdUploadParams.serializer(), params)
        return ApiClient.execute(
            method = "POST",
            path = "jd/upload",
            serializer = JdUploadResult.serializer(),
            body = ApiClient.jsonBody(payload),
        )
    }

    suspend fun listJds(
        page: Int = 1,
        size: Int = 20,
        keyword: String? = null,
        company: String? = null,
    ): ApiResult<PageResult<JdListItem>> = ApiClient.execute(
        method = "GET",
        path = "jd/list",
        serializer = PageResult.serializer(JdListItem.serializer()),
        query = mapOf(
            "page" to page.toString(),
            "size" to size.toString(),
            "keyword" to keyword,
            "company" to company,
        ),
    )

    suspend fun getJd(id: Long): ApiResult<JdDetail> = ApiClient.execute(
        method = "GET",
        path = "jd/$id",
        serializer = JdDetail.serializer(),
    )

    suspend fun deleteJd(id: Long): ApiResult<Unit> = ApiClient.executeEmpty(
        method = "DELETE",
        path = "jd/$id",
    )

    suspend fun getJdParseResult(id: Long): ApiResult<JdParseResult> = ApiClient.execute(
        method = "GET",
        path = "jd/$id/parse/result",
        serializer = JdParseResult.serializer(),
    )

    suspend fun reparseJd(id: Long): ApiResult<ReparseResult> = ApiClient.execute(
        method = "POST",
        path = "jd/$id/reparse",
        serializer = ReparseResult.serializer(),
    )

    // ─── §4 匹配 ────────────────────────────────────────────

    suspend fun evaluateMatch(resumeId: Long, jdId: Long): ApiResult<MatchEvaluateResult> {
        val payload = buildJsonObject {
            put("resumeId", resumeId)
            put("jdId", jdId)
        }.toString()
        return ApiClient.execute(
            method = "POST",
            path = "match/evaluate",
            serializer = MatchEvaluateResult.serializer(),
            body = ApiClient.jsonBody(payload),
        )
    }

    suspend fun getMatch(recordId: String): ApiResult<MatchRecord> = ApiClient.execute(
        method = "GET",
        path = "match/$recordId",
        serializer = MatchRecord.serializer(),
    )

    suspend fun listMatches(
        page: Int = 1,
        size: Int = 20,
    ): ApiResult<PageResult<MatchRecord>> = ApiClient.execute(
        method = "GET",
        path = "match/list",
        serializer = PageResult.serializer(MatchRecord.serializer()),
        query = mapOf("page" to page.toString(), "size" to size.toString()),
    )

    // ─── §5 AI 任务 ─────────────────────────────────────────

    suspend fun createTask(
        taskType: String,
        bizId: String?,
        input: JsonObject,
    ): ApiResult<AiTask> {
        val payload = buildJsonObject {
            put("taskType", taskType)
            if (!bizId.isNullOrBlank()) put("bizId", bizId)
            put("input", input)
        }.toString()
        return ApiClient.execute(
            method = "POST",
            path = "ai-tasks",
            serializer = AiTask.serializer(),
            body = ApiClient.jsonBody(payload),
        )
    }

    suspend fun getTask(taskId: String): ApiResult<AiTask> = ApiClient.execute(
        method = "GET",
        path = "ai-tasks/$taskId",
        serializer = AiTask.serializer(),
    )
}
