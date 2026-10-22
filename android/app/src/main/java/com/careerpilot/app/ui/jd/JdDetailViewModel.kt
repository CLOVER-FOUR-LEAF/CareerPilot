package com.careerpilot.app.ui.jd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.JdDetail
import com.careerpilot.app.data.model.JdParseResult
import com.careerpilot.app.data.repo.ServiceLocator
import kotlinx.coroutines.launch

class JdDetailViewModel : ViewModel() {

    private val repo get() = ServiceLocator.repository

    var detail by mutableStateOf<JdDetail?>(null)
    var parse by mutableStateOf<JdParseResult?>(null)
    var loading by mutableStateOf(false)
    var reparsing by mutableStateOf(false)
    var error by mutableStateOf("")

    fun load(jdId: Long) {
        viewModelScope.launch {
            loading = true
            error = ""

            when (val res = repo.getJd(jdId)) {
                is ApiResult.Success -> detail = res.data
                is ApiResult.Failure -> error = res.message
            }

            // 解析结果可能还没生成，失败不阻塞页面
            when (val res = repo.getJdParseResult(jdId)) {
                is ApiResult.Success -> parse = res.data
                is ApiResult.Failure -> Unit
            }

            loading = false
        }
    }

    fun reparse(jdId: Long) {
        if (reparsing) return
        viewModelScope.launch {
            reparsing = true
            error = ""
            when (val res = repo.reparseJd(jdId)) {
                is ApiResult.Success -> reparsing = false
                is ApiResult.Failure -> {
                    error = res.message
                    reparsing = false
                }
            }
        }
    }
}
