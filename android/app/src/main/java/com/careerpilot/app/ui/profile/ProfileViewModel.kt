package com.careerpilot.app.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.model.Membership
import com.careerpilot.app.data.model.UpdateProfileParams
import com.careerpilot.app.data.model.UserProfile
import com.careerpilot.app.data.repo.ServiceLocator
import com.careerpilot.app.data.repo.Session
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val repo get() = ServiceLocator.repository

    var profile by mutableStateOf<UserProfile?>(null)
    var membership by mutableStateOf<Membership?>(null)
    var loading by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf("")
    var message by mutableStateOf("")

    // 编辑表单
    var nickname by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set
    var city by mutableStateOf("")
        private set
    var currentTitle by mutableStateOf("")
        private set
    var yearsOfExperience by mutableStateOf("")
        private set
    var educationLevel by mutableStateOf("")
        private set
    var school by mutableStateOf("")
        private set
    var major by mutableStateOf("")
        private set
    var graduationYear by mutableStateOf("")
        private set
    var targetIndustry by mutableStateOf("")
        private set
    var targetPosition by mutableStateOf("")
        private set
    var skillsText by mutableStateOf("")
        private set

    fun load() {
        viewModelScope.launch {
            loading = true
            error = ""
            when (val res = repo.getProfile()) {
                is ApiResult.Success -> {
                    profile = res.data
                    fillForm(res.data)
                }
                is ApiResult.Failure -> error = res.message
            }
            when (val res = repo.getMembership()) {
                is ApiResult.Success -> membership = res.data
                is ApiResult.Failure -> Unit
            }
            loading = false
        }
    }

    private fun fillForm(data: UserProfile) {
        nickname = data.nickname
        phone = data.phone.orEmpty()
        city = data.city.orEmpty()
        currentTitle = data.currentTitle.orEmpty()
        yearsOfExperience = data.yearsOfExperience?.toString().orEmpty()
        educationLevel = data.educationLevel.orEmpty()
        school = data.school.orEmpty()
        major = data.major.orEmpty()
        graduationYear = data.graduationYear?.toString().orEmpty()
        targetIndustry = data.targetIndustry.orEmpty()
        targetPosition = data.targetPosition.orEmpty()
        skillsText = data.skills.orEmpty().joinToString("、")
    }

    fun onNicknameChange(v: String) { nickname = v }
    fun onPhoneChange(v: String) { phone = v }
    fun onCityChange(v: String) { city = v }
    fun onCurrentTitleChange(v: String) { currentTitle = v }
    fun onYearsChange(v: String) { yearsOfExperience = v.filter { it.isDigit() }.take(2) }
    fun onEducationChange(v: String) { educationLevel = v }
    fun onSchoolChange(v: String) { school = v }
    fun onMajorChange(v: String) { major = v }
    fun onGraduationYearChange(v: String) { graduationYear = v.filter { it.isDigit() }.take(4) }
    fun onTargetIndustryChange(v: String) { targetIndustry = v }
    fun onTargetPositionChange(v: String) { targetPosition = v }
    fun onSkillsChange(v: String) { skillsText = v }

    fun save(onSaved: () -> Unit) {
        if (saving) return
        viewModelScope.launch {
            saving = true
            error = ""
            message = ""
            val params = UpdateProfileParams(
                nickname = nickname.trim().ifBlank { null },
                phone = phone.trim().ifBlank { null },
                city = city.trim().ifBlank { null },
                currentTitle = currentTitle.trim().ifBlank { null },
                yearsOfExperience = yearsOfExperience.trim().toIntOrNull(),
                educationLevel = educationLevel.trim().ifBlank { null },
                school = school.trim().ifBlank { null },
                major = major.trim().ifBlank { null },
                graduationYear = graduationYear.trim().toIntOrNull(),
                targetIndustry = targetIndustry.trim().ifBlank { null },
                targetPosition = targetPosition.trim().ifBlank { null },
                skills = skillsText.split("、", ",", "，")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { null },
            )
            when (val res = repo.updateProfile(params)) {
                is ApiResult.Success -> {
                    profile = res.data
                    fillForm(res.data)
                    Session.currentUser?.let { user ->
                        Session.onLogin(
                            user.copy(nickname = res.data.nickname, email = res.data.email),
                        )
                    }
                    message = "资料已保存"
                    saving = false
                    onSaved()
                }
                is ApiResult.Failure -> {
                    error = res.message
                    saving = false
                }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            Session.onLogout()
            onDone()
        }
    }
}
