package com.careerpilot.app.ui.resume

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.AtsScoreResult
import com.careerpilot.app.data.model.ResumeDetail
import com.careerpilot.app.data.model.ResumeVersion
import com.careerpilot.app.data.repo.ServiceLocator
import kotlinx.coroutines.launch

class ResumeDetailViewModel : ViewModel() {

    private val repo get() = ServiceLocator.repository

    var detail by mutableStateOf<ResumeDetail?>(null)
    var ats by mutableStateOf<AtsScoreResult?>(null)
    var versions by mutableStateOf<List<ResumeVersion>>(emptyList())
    var loading by mutableStateOf(false)
    var reparsing by mutableStateOf(false)
    var error by mutableStateOf("")

    fun load(resumeId: Long) {
        viewModelScope.launch {
            loading = true
            error = ""

            when (val res = repo.getResume(resumeId)) {
                is ApiResult.Success -> detail = res.data
                is ApiResult.Failure -> error = res.message
            }

            // ATS 评分失败不影响正文展示
            when (val res = repo.getAtsScore(resumeId)) {
                is ApiResult.Success -> ats = res.data
                is ApiResult.Failure -> Unit
            }

            when (val res = repo.getResumeVersions(resumeId)) {
                is ApiResult.Success -> versions = res.data
                is ApiResult.Failure -> Unit
            }

            loading = false
        }
    }

    fun reparse(resumeId: Long, onDone: (String?) -> Unit) {
        if (reparsing) return
        viewModelScope.launch {
            reparsing = true
            error = ""
            when (val res = repo.reparseResume(resumeId)) {
                is ApiResult.Success -> {
                    reparsing = false
                    onDone(res.data.taskId)
                }
                is ApiResult.Failure -> {
                    error = res.message
                    reparsing = false
                    onDone(null)
                }
            }
        }
    }
}
