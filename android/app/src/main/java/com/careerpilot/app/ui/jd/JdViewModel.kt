package com.careerpilot.app.ui.jd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.JdListItem
import com.careerpilot.app.data.model.JdUploadParams
import com.careerpilot.app.data.repo.ServiceLocator
import kotlinx.coroutines.launch

class JdViewModel : ViewModel() {

    private val repo get() = ServiceLocator.repository

    var jds by mutableStateOf<List<JdListItem>>(emptyList())
    var loading by mutableStateOf(false)
    var creating by mutableStateOf(false)
    var error by mutableStateOf("")
    var keyword by mutableStateOf("")

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = ""
            when (
                val res = repo.listJds(
                    page = 1,
                    size = 50,
                    keyword = keyword.trim().ifBlank { null },
                )
            ) {
                is ApiResult.Success -> jds = res.data.items
                is ApiResult.Failure -> error = res.message
            }
            loading = false
        }
    }

    fun search(value: String) {
        keyword = value
        refresh()
    }

    fun create(
        jobTitle: String,
        companyName: String,
        salaryRange: String,
        rawText: String,
        onDone: (Boolean) -> Unit,
    ) {
        if (creating) return
        viewModelScope.launch {
            creating = true
            error = ""
            val params = JdUploadParams(
                jobTitle = jobTitle.trim(),
                companyName = companyName.trim().ifBlank { null },
                rawText = rawText.trim(),
                sourceType = "MANUAL",
                salaryRange = salaryRange.trim().ifBlank { null },
            )
            when (val res = repo.createJd(params)) {
                is ApiResult.Success -> {
                    creating = false
                    refresh()
                    onDone(true)
                }
                is ApiResult.Failure -> {
                    error = res.message
                    creating = false
                    onDone(false)
                }
            }
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val res = repo.deleteJd(id)) {
                is ApiResult.Success -> {
                    error = ""
                    refresh()
                    onDone()
                }
                is ApiResult.Failure -> error = res.message
            }
        }
    }
}
