package com.careerpilot.app.ui.careerplan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.data.model.CareerPlanResult
import com.careerpilot.app.ui.common.AiTaskViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CareerPlanViewModel : AiTaskViewModel() {

    var currentTitle by mutableStateOf("")
    var yearsOfExperience by mutableStateOf(3)
    var targetIndustry by mutableStateOf("互联网")
    var targetPosition by mutableStateOf("")
    var skillsText by mutableStateOf("")

    val result: CareerPlanResult?
        get() = decodeOutput(CareerPlanResult.serializer())

    fun submit(onDone: (Boolean) -> Unit) {
        if (currentTitle.isBlank() || targetPosition.isBlank() || targetIndustry.isBlank()) {
            errorMessage = "请填写当前职位、目标行业与目标岗位"
            return
        }
        val skills = skillsText.split("、", ",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val input: JsonObject = buildJsonObject {
            put("current_title", currentTitle.trim())
            put("years_of_experience", yearsOfExperience)
            put("target_industry", targetIndustry)
            put("target_position", targetPosition.trim())
            if (skills.isNotEmpty()) put("skills", JsonArray(skills.map { JsonPrimitive(it) }))
        }

        viewModelScope.launch {
            val ok = runTask(taskType = "career_plan", input = input)
            onDone(ok)
        }
    }
}
