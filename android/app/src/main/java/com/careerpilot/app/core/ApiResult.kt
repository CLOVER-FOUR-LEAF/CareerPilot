package com.careerpilot.app.core

/** 统一的接口调用结果。 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    data class Failure(
        val code: Int,
        val message: String,
        val traceId: String? = null,
        val httpStatus: Int = 0,
    ) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

/** 取数据，失败返回 null。 */
fun <T> ApiResult<T>.dataOrNull(): T? = (this as? ApiResult.Success)?.data

/** 取错误信息，成功返回 null。 */
fun <T> ApiResult<T>.errorOrNull(): String? = (this as? ApiResult.Failure)?.message
