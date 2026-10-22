package com.careerpilot.app.ui.match

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.JdListItem
import com.careerpilot.app.data.model.MatchRecord
import com.careerpilot.app.data.model.ResumeListItem
import com.careerpilot.app.data.repo.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MatchViewModel : ViewModel() {

    private val repo get() = ServiceLocator.repository

    var resumes by mutableStateOf<List<ResumeListItem>>(emptyList())
    var jds by mutableStateOf<List<JdListItem>>(emptyList())
    var history by mutableStateOf<List<MatchRecord>>(emptyList())

    var selectedResumeId by mutableStateOf<Long?>(null)
    var selectedJdId by mutableStateOf<Long?>(null)

    var evaluating by mutableStateOf(false)
    var pollProgress by mutableStateOf(0)
    var record by mutableStateOf<MatchRecord?>(null)
    var loadingOptions by mutableStateOf(false)
    var error by mutableStateOf("")

    fun loadAll() {
        viewModelScope.launch {
            loadingOptions = true
            val resumeRes = repo.listResumes(page = 1, size = 50)
            val jdRes = repo.listJds(page = 1, size = 50)
            val historyRes = repo.listMatches(page = 1, size = 20)

            resumes = (resumeRes as? ApiResult.Success)?.data?.items.orEmpty()
            jds = (jdRes as? ApiResult.Success)?.data?.items.orEmpty()
            history = (historyRes as? ApiResult.Success)?.data?.items.orEmpty()

            error = listOf(resumeRes, jdRes, historyRes)
                .filterIsInstance<ApiResult.Failure>()
                .firstOrNull()
                ?.message
                .orEmpty()

            loadingOptions = false
        }
    }

    /** §4.1 发起评估 → §4.2 轮询记录（1.5s 一次，最多 60s）。 */
    fun evaluate(onFinished: (Boolean) -> Unit) {
        val resumeId = selectedResumeId
        val jdId = selectedJdId
        if (resumeId == null || jdId == null) {
            error = "请先选择简历与 JD"
            return
        }
        if (evaluating) return

        viewModelScope.launch {
            evaluating = true
            error = ""
            record = null
            pollProgress = 0

            when (val res = repo.evaluateMatch(resumeId, jdId)) {
                is ApiResult.Failure -> {
                    error = res.message
                    evaluating = false
                    onFinished(false)
                    return@launch
                }
                is ApiResult.Success -> {
                    val result = pollRecord(res.data.recordId)
                    evaluating = false
                    if (result?.status == "SUCCESS") {
                        onFinished(true)
                        loadAll()
                    } else {
                        onFinished(false)
                    }
                }
            }
        }
    }

    private suspend fun pollRecord(recordId: String): MatchRecord? {
        val deadline = System.currentTimeMillis() + 60_000L
        while (viewModelScope.isActive) {
            when (val res = repo.getMatch(recordId)) {
                is ApiResult.Success -> {
                    record = res.data
                    if (res.data.status == "SUCCESS" ||
                        res.data.status == "FAILED" ||
                        res.data.status == "CANCELLED"
                    ) {
                        pollProgress = 100
                        return res.data
                    }
                }
                is ApiResult.Failure -> Unit // 单次失败继续等
            }
            if (System.currentTimeMillis() > deadline) {
                error = "评估超时，请稍后到历史记录中查看结果"
                return record
            }
            pollProgress = (pollProgress + 5).coerceAtMost(95)
            delay(1_500L)
        }
        return record
    }

    /** 查看一条历史记录。 */
    fun openRecord(recordId: String, onLoaded: (MatchRecord) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.getMatch(recordId)) {
                is ApiResult.Success -> {
                    record = res.data
                    // 记录里存的是业务 ID，这里反查回数据库主键
                    selectedResumeId = resumes.firstOrNull { it.resumeBizId == res.data.resumeId }?.resumeId
                    selectedJdId = jds.firstOrNull { it.jdBizId == res.data.jdId }?.jdId
                    onLoaded(res.data)
                }
                is ApiResult.Failure -> error = res.message
            }
        }
    }
}
