package com.careerpilot.app.ui.optimize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.JdListItem
import com.careerpilot.app.data.model.ResumeOptimizeResult
import com.careerpilot.app.ui.common.AiTaskViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OptimizeViewModel : AiTaskViewModel() {

    var jds by mutableStateOf<List<JdListItem>>(emptyList())
    var selectedJdId by mutableStateOf<Long?>(null)
    var original by mutableStateOf("")
    var tone by mutableStateOf("professional")

    /** 选中的 JD 的技能列表 —— Agent 只认 `jd_skills`，不传的话注入技能恒为空。 */
    private var jdSkills by mutableStateOf<List<String>>(emptyList())
    private var jdText by mutableStateOf("")

    val result: ResumeOptimizeResult?
        get() = decodeOutput(ResumeOptimizeResult.serializer())

    fun loadJds() {
        viewModelScope.launch {
            when (val res = repo.listJds(page = 1, size = 50)) {
                is ApiResult.Success -> jds = res.data.items
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun selectJd(id: Long?) {
        selectedJdId = id
        jdSkills = emptyList()
        jdText = ""
        if (id == null) return
        viewModelScope.launch {
            when (val res = repo.getJdParseResult(id)) {
                is ApiResult.Success -> {
                    jdSkills = res.data.skills.orEmpty().map { it.name }.filter { it.isNotBlank() }
                }
                is ApiResult.Failure -> Unit
            }
            when (val res = repo.getJd(id)) {
                is ApiResult.Success -> jdText = res.data.rawText.orEmpty()
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun submit(onDone: (Boolean) -> Unit) {
        val bullet = original.trim()
        if (bullet.isEmpty()) {
            errorMessage = "请先填写需要优化的经历描述"
            return
        }
        val selected = jds.firstOrNull { it.jdId == selectedJdId }
        val bizId = selected?.jdBizId

        val input: JsonObject = buildJsonObject {
            // resume-optimizer-agent 的入参字段叫 bullet（§7.4），不是 original
            put("bullet", bullet)
            put("tone", tone)
            put("jd_skills", kotlinx.serialization.json.JsonArray(jdSkills.map {
                kotlinx.serialization.json.JsonPrimitive(it)
            }))
            if (!bizId.isNullOrBlank()) put("jd_id", bizId)
            if (jdText.isNotBlank()) put("jd_text", jdText)
        }

        viewModelScope.launch {
            val ok = runTask(
                taskType = "resume_optimize",
                bizId = bizId,
                input = input,
                useStream = true,
            )
            onDone(ok)
        }
    }
}
