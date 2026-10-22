package com.careerpilot.app.ui.resume

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.ResumeListItem
import com.careerpilot.app.data.repo.ServiceLocator
import com.careerpilot.app.ui.common.FilePicker
import kotlinx.coroutines.launch

class ResumeViewModel : ViewModel() {

    private val repo get() = ServiceLocator.repository

    var resumes by mutableStateOf<List<ResumeListItem>>(emptyList())
    var loading by mutableStateOf(false)
    var uploading by mutableStateOf(false)
    var error by mutableStateOf("")
    var keyword by mutableStateOf("")

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = ""
            when (val res = repo.listResumes(page = 1, size = 50, keyword = keyword.trim().ifBlank { null })) {
                is ApiResult.Success -> resumes = res.data.items
                is ApiResult.Failure -> error = res.message
            }
            loading = false
        }
    }

    fun search(value: String) {
        keyword = value
        refresh()
    }

    /** 上传简历文件。 */
    fun upload(context: Context, uri: Uri, title: String?, onDone: (String?) -> Unit) {
        if (uploading) return
        viewModelScope.launch {
            uploading = true
            error = ""
            val picked = FilePicker.read(context, uri)
            if (picked == null) {
                error = "读取文件失败，请重试"
                uploading = false
                onDone(null)
                return@launch
            }
            when (
                val res = repo.uploadResume(
                    fileName = picked.name,
                    mimeType = picked.mimeType,
                    content = picked.bytes,
                    title = title?.trim()?.ifBlank { null },
                )
            ) {
                is ApiResult.Success -> {
                    uploading = false
                    refresh()
                    onDone(res.data.title.ifBlank { picked.name })
                }
                is ApiResult.Failure -> {
                    error = res.message
                    uploading = false
                    onDone(null)
                }
            }
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val res = repo.deleteResume(id)) {
                is ApiResult.Success -> {
                    error = ""
                    refresh()
                    onDone()
                }
                is ApiResult.Failure -> error = res.message
            }
        }
    }

    fun clearError() {
        error = ""
    }
}
