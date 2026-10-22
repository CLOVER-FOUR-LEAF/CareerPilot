package com.careerpilot.app.data.api

import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.core.TokenStore
import com.careerpilot.app.data.model.ApiEnvelope
import com.careerpilot.app.data.model.LoginResponse
import com.careerpilot.app.data.model.RefreshParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * 统一网关客户端。
 *
 * - 响应包络 `ApiResponse<T>` 的解析集中在这里，页面拿到的直接是 `data`。
 * - 401 自动刷新令牌并重试一次（并发请求共享同一次刷新）。
 * - baseUrl 可运行时修改，后端换服务器不需要重新打包。
 */
object ApiClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

    @Volatile
    private var tokenStore: TokenStore? = null

    private val refreshLock = Any()

    /** 刷新令牌用的独立客户端：不带鉴权拦截器，避免递归。 */
    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val store = tokenStore
            val token = store?.accessToken

            val request = if (token.isNullOrBlank()) {
                original
            } else {
                original.newBuilder().header("Authorization", "Bearer $token").build()
            }

            val response = chain.proceed(request)
            if (response.code != 401 || token.isNullOrBlank()) {
                response
            } else {
                val newToken = synchronized(refreshLock) {
                    // 别的线程可能已经刷新过了，直接复用
                    val current = store?.accessToken
                    if (!current.isNullOrBlank() && current != token) {
                        current
                    } else {
                        refreshAccessToken()
                    }
                }
                if (newToken.isNullOrBlank()) {
                    response
                } else {
                    response.close()
                    chain.proceed(
                        request.newBuilder().header("Authorization", "Bearer $newToken").build(),
                    )
                }
            }
        }
        .build()

    fun bind(store: TokenStore) {
        tokenStore = store
    }

    // ─── 请求执行 ────────────────────────────────────────────

    suspend fun <T> execute(
        method: String,
        path: String,
        serializer: KSerializer<T>,
        query: Map<String, String?> = emptyMap(),
        body: RequestBody? = null,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(buildUrl(path, query))
                .method(method, body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    ApiResult.Failure(
                        code = response.code,
                        message = errorMessageOf(text, response.code),
                        httpStatus = response.code,
                    )
                } else {
                    parseEnvelope(text, serializer)
                }
            }
        } catch (e: Exception) {
            ApiResult.Failure(-1, e.message ?: "网络错误")
        }
    }

    /** `data` 为空的接口（删除、登出等）。 */
    suspend fun executeEmpty(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: RequestBody? = null,
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(buildUrl(path, query))
                .method(method, body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    ApiResult.Failure(
                        code = response.code,
                        message = errorMessageOf(text, response.code),
                        httpStatus = response.code,
                    )
                } else {
                    ApiResult.Success(Unit)
                }
            }
        } catch (e: Exception) {
            ApiResult.Failure(-1, e.message ?: "网络错误")
        }
    }

    /** JSON 请求体。 */
    fun jsonBody(value: String): RequestBody = value.toRequestBody(JSON_MEDIA_TYPE)

    /** 简历上传用的 multipart 体。 */
    fun uploadBody(
        fileName: String,
        mimeType: String,
        content: ByteArray,
        title: String?,
    ): RequestBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart(
            "file",
            fileName,
            content.toRequestBody(mimeType.toMediaTypeOrNull()),
        )
        if (!title.isNullOrBlank()) {
            builder.addFormDataPart("title", title)
        }
        return builder.build()
    }

    /** SSE 用：返回带鉴权头的 GET 请求。 */
    fun streamRequest(path: String): Request {
        val builder = Request.Builder()
            .url(buildUrl(path, emptyMap()))
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get()
        tokenStore?.accessToken?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return builder.build()
    }

    /** SSE 用：流式客户端。 */
    fun streamingClient(): OkHttpClient = client

    // ─── 内部工具 ────────────────────────────────────────────

    private fun buildUrl(path: String, query: Map<String, String?>): HttpUrl {
        // 去掉结尾的 "/" 再 addPathSegments，否则会拼出 /api/v1//resume/list
        val base = (tokenStore?.baseUrl ?: "http://10.0.2.2:8080/api/v1/").trimEnd('/')
        val builder = base.toHttpUrl().newBuilder()
        val clean = path.trim('/')
        if (clean.isNotEmpty()) {
            builder.addPathSegments(clean)
        }
        query.forEach { (key, value) ->
            if (!value.isNullOrBlank()) {
                builder.addQueryParameter(key, value)
            }
        }
        return builder.build()
    }

    private fun <T> parseEnvelope(text: String, serializer: KSerializer<T>): ApiResult<T> {
        val envelope = runCatching {
            json.decodeFromString(ApiEnvelope.serializer(serializer), text)
        }.getOrElse {
            return ApiResult.Failure(-2, "响应解析失败：${it.message ?: "未知错误"}")
        }
        if (envelope.code != 0) {
            return ApiResult.Failure(
                code = envelope.code,
                message = envelope.message.ifBlank { "请求失败（code=${envelope.code}）" },
                traceId = envelope.traceId,
            )
        }
        val data = envelope.data
            ?: return ApiResult.Failure(envelope.code, "响应数据为空")
        return ApiResult.Success(data)
    }

    private fun errorMessageOf(text: String, status: Int): String {
        if (text.isNotBlank()) {
            runCatching {
                val envelope = json.decodeFromString(
                    ApiEnvelope.serializer(JsonObject.serializer()),
                    text,
                )
                if (envelope.message.isNotBlank()) {
                    return envelope.message
                }
            }
        }
        return when (status) {
            401 -> "登录已过期，请重新登录"
            403 -> "没有访问权限"
            404 -> "接口不存在，请检查服务器地址"
            408, 504 -> "请求超时"
            in 500..599 -> "服务器错误（$status）"
            else -> "请求失败（$status）"
        }
    }

    /**
     * 用 refreshToken 换新的 accessToken。
     * 成功后写回本地存储；失败返回 null（调用方应视为未登录）。
     */
    private fun refreshAccessToken(): String? {
        val store = tokenStore ?: return null
        val refresh = store.refreshToken ?: return null
        if (refresh.isBlank()) return null

        return try {
            val payload = json.encodeToString(RefreshParams.serializer(), RefreshParams(refresh))
            val request = Request.Builder()
                .url(buildUrl("auth/refresh", emptyMap()))
                .post(jsonBody(payload))
                .build()
            plainClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    val text = response.body?.string().orEmpty()
                    val envelope = runCatching {
                        json.decodeFromString(
                            ApiEnvelope.serializer(LoginResponse.serializer()),
                            text,
                        )
                    }.getOrNull()
                    val data = envelope?.data
                    if (envelope == null || envelope.code != 0 || data == null || data.accessToken.isBlank()) {
                        null
                    } else {
                        store.accessToken = data.accessToken
                        if (data.refreshToken.isNotBlank()) {
                            store.refreshToken = data.refreshToken
                        }
                        data.accessToken
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
