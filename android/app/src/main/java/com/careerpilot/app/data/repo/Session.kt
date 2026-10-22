package com.careerpilot.app.data.repo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.careerpilot.app.core.TokenStore
import com.careerpilot.app.data.api.ApiClient
import com.careerpilot.app.data.model.AuthUser

/**
 * 全局会话状态（当前用户 + 是否已登录）。
 *
 * 令牌本身存在 SharedPreferences，这里只放内存态，供 Compose 观察。
 */
object Session {

    var authenticated by mutableStateOf(false)
        private set

    var currentUser by mutableStateOf<AuthUser?>(null)
        private set

    fun restore(store: TokenStore) {
        authenticated = store.isAuthenticated
        currentUser = store.userJson?.let { raw ->
            runCatching {
                ApiClient.json.decodeFromString(AuthUser.serializer(), raw)
            }.getOrNull()
        }
    }

    fun onLogin(user: AuthUser?) {
        authenticated = true
        currentUser = user
    }

    fun onLogout() {
        authenticated = false
        currentUser = null
    }

    /** 顶部展示用的名字。 */
    val displayName: String
        get() = currentUser?.nickname?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.takeIf { it.isNotBlank() }
            ?: "用户"

    /** 头像占位文字。 */
    val initials: String
        get() {
            val name = currentUser?.nickname?.trim().orEmpty()
            if (name.isNotEmpty()) return name.take(1)
            val mail = currentUser?.email?.trim().orEmpty()
            return if (mail.isNotEmpty()) mail.take(1).uppercase() else "?"
        }
}
