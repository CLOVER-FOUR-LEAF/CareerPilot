package com.careerpilot.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.careerpilot.app.core.ApiResult
import com.careerpilot.app.data.repo.ServiceLocator
import com.careerpilot.app.data.repo.Session
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repo get() = ServiceLocator.repository

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var nickname by mutableStateOf("")
    var inviteCode by mutableStateOf("")

    var loading by mutableStateOf(false)
    var error by mutableStateOf("")

    val emailValid: Boolean
        get() = email.isNotBlank() && email.contains("@") && email.contains(".")

    val passwordValid: Boolean
        get() = password.length >= 6

    fun login(onSuccess: () -> Unit) {
        if (loading) return
        if (!emailValid) {
            error = "请输入正确的邮箱"
            return
        }
        if (!passwordValid) {
            error = "密码至少 6 位"
            return
        }
        error = ""
        loading = true
        viewModelScope.launch {
            when (val res = repo.login(email.trim(), password)) {
                is ApiResult.Success -> {
                    Session.onLogin(res.data.user)
                    loading = false
                    onSuccess()
                }
                is ApiResult.Failure -> {
                    error = res.message
                    loading = false
                }
            }
        }
    }

    fun register(onSuccess: () -> Unit) {
        if (loading) return
        if (!emailValid) {
            error = "请输入正确的邮箱"
            return
        }
        if (!passwordValid) {
            error = "密码至少 6 位"
            return
        }
        error = ""
        loading = true
        viewModelScope.launch {
            val res = repo.register(
                email = email.trim(),
                password = password,
                nickname = nickname.trim().ifBlank { null },
                inviteCode = inviteCode.trim().ifBlank { null },
            )
            when (res) {
                is ApiResult.Success -> {
                    Session.onLogin(res.data.user)
                    loading = false
                    onSuccess()
                }
                is ApiResult.Failure -> {
                    error = res.message
                    loading = false
                }
            }
        }
    }
}
