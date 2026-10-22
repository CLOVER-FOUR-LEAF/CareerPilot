package com.careerpilot.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.api.ApiClient
import com.careerpilot.app.data.api.SseClient
import com.careerpilot.app.data.model.AiTask
import com.careerpilot.app.data.repo.ServiceLocator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * AI 任务的通用流程：创建 → 跟踪 → 产出。
 *
 * 对应 Web 端的 `useTaskPolling`（`web/src/composables/useTaskPolling.ts`）：
 * 默认轮询 1500ms、最长等待 60s；`useStream = true` 时优先走 SSE，失败自动降级为轮询。
 */
abstract class AiTaskViewModel : ViewModel() {

    protected val repo get() = ServiceLocator.repository

    var taskId by mutableStateOf("")
        private set
    var status by mutableStateOf("")
        private set
    var progress by mutableStateOf(0)
        private set
    var currentAgent by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf("")
    var output by mutableStateOf<JsonElement?>(null)
        private set
    var running by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set

    val succeeded: Boolean get() = finished && status == "SUCCESS"

    protected fun resetTaskState() {
        taskId = ""
        status = ""
        progress = 0
        currentAgent = ""
        errorMessage = ""
        output = null
        running = false
        finished = false
    }

    /** 创建并跟踪一个 AI 任务，返回是否成功。 */
    protected suspend fun runTask(
        taskType: String,
        bizId: String? = null,
        input: kotlinx.serialization.json.JsonObject,
        useStream: Boolean = false,
        pollIntervalMs: Long = 1_500L,
        timeoutMs: Long = 60_000L,
    ): Boolean {
        resetTaskState()
        running = true
        try {
            val created = repo.createTask(taskType, bizId, input)
            if (created is ApiResult.Failure) {
                errorMessage = created.message
                return false
            }
            val task = (created as ApiResult.Success).data
            taskId = task.taskId
            applyTask(task)
            if (!isTerminal(task.status)) {
                track(task.taskId, useStream, pollIntervalMs, timeoutMs)
            }
            return status == "SUCCESS"
        } finally {
            running = false
            finished = true
        }
    }

    /** 把任务输出反序列化成具体类型；结构不匹配时返回 null。 */
    protected fun <T> decodeOutput(deserializer: DeserializationStrategy<T>): T? {
        val element = output ?: return null
        return runCatching {
            ApiClient.json.decodeFromJsonElement(deserializer, element)
        }.getOrNull()
    }

    private suspend fun track(
        taskId: String,
        useStream: Boolean,
        pollIntervalMs: Long,
        timeoutMs: Long,
    ) {
        if (useStream) {
            val completed = SseClient.stream(taskId) { event, payload ->
                applyStreamEvent(event, payload)
            }
            if (completed) {
                fetchFinal(taskId)
                return
            }
            // 流不可用（后端未开启 / 网络中断）→ 降级轮询
        }
        poll(taskId, pollIntervalMs, timeoutMs)
    }

    private suspend fun poll(taskId: String, pollIntervalMs: Long, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (currentCoroutineContext().isActive) {
            if (System.currentTimeMillis() > deadline) {
                if (errorMessage.isBlank()) {
                    errorMessage = "任务执行超时，请稍后重试"
                }
                return
            }
            when (val res = repo.getTask(taskId)) {
                is ApiResult.Success -> {
                    applyTask(res.data)
                    if (isTerminal(res.data.status)) return
                }
                is ApiResult.Failure -> {
                    // 单次查询失败不终止跟踪，继续等待
                }
            }
            delay(pollIntervalMs)
        }
    }

    private suspend fun fetchFinal(taskId: String) {
        when (val res = repo.getTask(taskId)) {
            is ApiResult.Success -> applyTask(res.data)
            is ApiResult.Failure -> Unit
        }
    }

    private fun applyTask(task: AiTask) {
        if (task.status.isNotBlank()) status = task.status
        task.progress?.let { progress = it }
        if (!task.currentAgent.isNullOrBlank()) currentAgent = task.currentAgent
        if (task.output != null) output = task.output
        if (!task.errorMessage.isNullOrBlank()) errorMessage = task.errorMessage
        if (task.status == "SUCCESS") progress = 100
    }

    private fun applyStreamEvent(event: String, payload: com.careerpilot.app.data.model.TaskStreamEvent) {
        if (payload.status.isNotBlank()) status = payload.status
        payload.progress?.let { progress = it }
        if (!payload.currentAgent.isNullOrBlank()) currentAgent = payload.currentAgent
        when (event) {
            "done" -> {
                if (payload.output != null) output = payload.output
                progress = 100
            }
            "error" -> {
                errorMessage = payload.errorMessage ?: "任务执行失败"
                status = "FAILED"
            }
        }
    }

    private fun isTerminal(status: String): Boolean =
        status == "SUCCESS" || status == "FAILED" || status == "CANCELLED"
}
