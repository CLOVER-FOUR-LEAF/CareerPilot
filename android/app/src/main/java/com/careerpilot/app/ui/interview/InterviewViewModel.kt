package com.careerpilot.app.ui.interview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.InterviewEvaluation
import com.careerpilot.app.data.model.InterviewQuestion
import com.careerpilot.app.data.model.JdListItem
import com.careerpilot.app.data.model.ResumeListItem
import com.careerpilot.app.ui.common.AiTaskViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class RoundRecord(
    val round: Int,
    val question: String,
    val answer: String,
    val evaluation: InterviewEvaluation,
)

class InterviewViewModel : AiTaskViewModel() {

    var resumes by mutableStateOf<List<ResumeListItem>>(emptyList())
    var jds by mutableStateOf<List<JdListItem>>(emptyList())
    var selectedResumeId by mutableStateOf<Long?>(null)
    var selectedJdId by mutableStateOf<Long?>(null)
    var totalRounds by mutableStateOf(3)

    var round by mutableStateOf(1)
    var currentQuestion by mutableStateOf<InterviewQuestion?>(null)
    var answer by mutableStateOf("")
    var records by mutableStateOf<List<RoundRecord>>(emptyList())

    /** 上一轮回答，用于让 Agent 生成针对它的追问。 */
    private var previousAnswer by mutableStateOf("")

    private var jdSkills by mutableStateOf<List<String>>(emptyList())
    private var jdText by mutableStateOf("")

    val sessionDone: Boolean get() = round > totalRounds
    val averageScore: String
        get() = if (records.isEmpty()) {
            "0"
        } else {
            String.format("%.1f", records.sumOf { it.evaluation.score } / records.size)
        }

    fun loadOptions() {
        viewModelScope.launch {
            when (val res = repo.listResumes(page = 1, size = 50)) {
                is ApiResult.Success -> resumes = res.data.items
                is ApiResult.Failure -> Unit
            }
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

    private fun buildContext(): JsonObject {
        val selectedJd = jds.firstOrNull { it.jdId == selectedJdId }
        val selectedResume = resumes.firstOrNull { it.resumeId == selectedResumeId }
        return buildJsonObject {
            put("resume_id", selectedResume?.resumeBizId ?: "")
            put("jd_id", selectedJd?.jdBizId ?: "")
            // interview-agent 用 job_title 挑题库
            put("job_title", selectedJd?.title ?: "")
            put("resume_highlights", JsonArray(listOfNotNull(selectedResume?.title).map { JsonPrimitive(it) }))
            // Agent 只认 jd_skills，不传的话出题与具体 JD 无关
            put("jd_skills", JsonArray(jdSkills.map { JsonPrimitive(it) }))
            if (jdText.isNotBlank()) put("jd_text", jdText)
            if (selectedResume != null) put("resume_text", selectedResume.title)
        }
    }

    fun startQuestion(onDone: (Boolean) -> Unit) {
        if (sessionDone) {
            errorMessage = "本场面试已结束"
            return
        }
        val context = buildContext()
        val selectedJd = jds.firstOrNull { it.jdId == selectedJdId }

        viewModelScope.launch {
            answer = ""
            currentQuestion = null
            val input = buildJsonObject {
                context.forEach { (key, value) -> put(key, value) }
                put("phase", "question")
                put("round_no", round)
                // 必须显式带上 total_rounds：Agent 侧默认只有 3
                put("total_rounds", totalRounds)
                if (previousAnswer.isNotBlank()) put("previous_answer", previousAnswer)
            }
            val ok = runTask(
                taskType = "interview",
                bizId = selectedJd?.jdBizId,
                input = input,
            )
            if (ok) {
                currentQuestion = decodeOutput(InterviewQuestion.serializer())
            }
            onDone(ok)
        }
    }

    fun submitAnswer(onDone: (Boolean) -> Unit) {
        val question = currentQuestion ?: return
        val text = answer.trim()
        if (text.isEmpty()) {
            errorMessage = "请先填写回答"
            return
        }
        val context = buildContext()
        val selectedJd = jds.firstOrNull { it.jdId == selectedJdId }
        val currentRound = round

        viewModelScope.launch {
            val input = buildJsonObject {
                context.forEach { (key, value) -> put(key, value) }
                put("phase", "evaluate")
                put("round_no", currentRound)
                put("question", question.question)
                put("answer", text)
            }
            val ok = runTask(
                taskType = "interview",
                bizId = selectedJd?.jdBizId,
                input = input,
            )
            if (ok) {
                val evaluation = decodeOutput(InterviewEvaluation.serializer())
                if (evaluation != null) {
                    records = listOf(
                        RoundRecord(currentRound, question.question, text, evaluation),
                    ) + records
                    round += 1
                    currentQuestion = null
                    previousAnswer = text
                    answer = ""
                }
            }
            onDone(ok)
        }
    }

    fun reset() {
        resetTaskState()
        round = 1
        currentQuestion = null
        answer = ""
        records = emptyList()
        previousAnswer = ""
    }
}
