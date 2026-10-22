package com.careerpilot.app.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.repo.ServiceLocator
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    private val repo get() = ServiceLocator.repository

    var resumeCount by mutableStateOf(0)
    var jdCount by mutableStateOf(0)
    var matchCount by mutableStateOf(0)
    var loading by mutableStateOf(true)
    var error by mutableStateOf("")

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = ""
            // 三个计数接口并发拉取
            val resumes = repo.listResumes(page = 1, size = 1)
            val jds = repo.listJds(page = 1, size = 1)
            val matches = repo.listMatches(page = 1, size = 1)

            resumeCount = (resumes as? ApiResult.Success)?.data?.total ?: 0
            jdCount = (jds as? ApiResult.Success)?.data?.total ?: 0
            matchCount = (matches as? ApiResult.Success)?.data?.total ?: 0

            // 统计失败不影响页面主体，只提示一次
            val failure = listOf(resumes, jds, matches)
                .filterIsInstance<ApiResult.Failure>()
                .firstOrNull()
            error = failure?.message.orEmpty()

            loading = false
        }
    }
}
