package com.careerpilot.app.ui.coverletter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.CoverLetterResult
import com.careerpilot.app.data.model.JdListItem
import com.careerpilot.app.data.model.ResumeListItem
import com.careerpilot.app.ui.common.AiTaskViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CoverLetterViewModel : AiTaskViewModel() {

    var jds by mutableStateOf<List<JdListItem>>(emptyList())
    var resumes by mutableStateOf<List<ResumeListItem>>(emptyList())
    var selectedJdId by mutableStateOf<Long?>(null)
    var jobTitle by mutableStateOf("")
    var companyName by mutableStateOf("")
    var tone by mutableStateOf("professional")

    private var jdSkills by mutableStateOf<List<String>>(emptyList())
    private var jdText by mutableStateOf("")

    val result: CoverLetterResult?
        get() = decodeOutput(CoverLetterResult.serializer())

    fun loadOptions() {
        viewModelScope.launch {
            when (val res = repo.listJds(page = 1, size = 50)) {
                is ApiResult.Success -> jds = res.data.items
                is ApiResult.Failure -> Unit
            }
            when (val res = repo.listResumes(page = 1, size = 50)) {
                is ApiResult.Success -> resumes = res.data.items
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
        val title = jobTitle.trim()
        if (title.isEmpty()) {
            errorMessage = "请填写应聘岗位"
            return
        }
        val selected = jds.firstOrNull { it.jdId == selectedJdId }
        val bizId = selected?.jdBizId
        val highlights = resumes.take(3).map { it.title }.filter { it.isNotBlank() }

        val input: JsonObject = buildJsonObject {
            put("job_title", title.ifBlank { selected?.title.orEmpty() })
            // cover-letter-agent 的入参字段是 company_name（§7.6）
            put("company_name", companyName.trim())
            put("tone", tone)
            // 不传 jd_skills / resume_highlights 的话，求职信只会是通用模板
            put("jd_skills", JsonArray(jdSkills.map { JsonPrimitive(it) }))
            put("resume_highlights", JsonArray(highlights.map { JsonPrimitive(it) }))
            if (!bizId.isNullOrBlank()) put("jd_id", bizId)
            if (jdText.isNotBlank()) put("jd_text", jdText)
        }

        viewModelScope.launch {
            val ok = runTask(
                taskType = "cover_letter",
                bizId = bizId,
                input = input,
            )
            onDone(ok)
        }
    }
}
