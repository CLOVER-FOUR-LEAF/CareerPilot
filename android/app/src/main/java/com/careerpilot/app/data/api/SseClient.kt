package com.careerpilot.app.data.api

import com.careerpilot.app.data.model.TaskStreamEvent
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * SSE 客户端（契约 §5.4 `GET /api/v1/ai-tasks/{taskId}/stream`）。
 *
 * 原生 EventSource 无法携带 Authorization 头，这里直接用 OkHttp 读流并手工解析
 * `event:` / `data:` 分帧，后端零改动。
 */
object SseClient {

    /**
     * 订阅任务进度流。
     *
     * @param onEvent 每收到一帧回调一次；返回 true 表示流正常结束（拿到 done / error）。
     * @return true 流正常走完；false 表示流不可用，调用方应降级为轮询。
     */
    suspend fun stream(
        taskId: String,
        onEvent: suspend (event: String, payload: TaskStreamEvent) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val request = ApiClient.streamRequest("ai-tasks/$taskId/stream")
        val call = ApiClient.streamingClient().newCall(request)

        // 协程被取消时立刻断开连接，避免线程卡在 read 上
        val handle: DisposableHandle? = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val source = body.source()

                var event = "message"
                val data = StringBuilder()

                while (!source.exhausted()) {
                    if (!currentCoroutineContext().isActive) return@withContext false

                    val line = source.readUtf8Line() ?: break

                    if (line.isEmpty()) {
                        // 空行 = 一帧结束
                        if (data.isNotEmpty()) {
                            val payload = runCatching {
                                ApiClient.json.decodeFromString(
                                    TaskStreamEvent.serializer(),
                                    data.toString(),
                                )
                            }.getOrNull()
                            data.setLength(0)
                            if (payload != null) {
                                onEvent(event, payload)
                                if (event == "done" || event == "error") {
                                    return@withContext true
                                }
                            }
                        }
                        event = "message"
                        continue
                    }

                    if (line.startsWith(":")) continue // 注释行，忽略

                    val idx = line.indexOf(':')
                    val field = if (idx == -1) line else line.substring(0, idx)
                    var value = if (idx == -1) "" else line.substring(idx + 1)
                    if (value.startsWith(" ")) value = value.substring(1)

                    when (field) {
                        "event" -> event = value
                        "data" -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(value)
                        }
                    }
                }
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            handle?.dispose()
        }
    }
}
