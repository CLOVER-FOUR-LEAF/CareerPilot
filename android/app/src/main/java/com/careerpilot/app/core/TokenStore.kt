package com.careerpilot.app.core

import android.content.Context
import android.content.SharedPreferences
import com.careerpilot.app.BuildConfig

/**
 * 令牌 / 用户信息 / 服务器地址的唯一本地存储。
 *
 * 放在 SharedPreferences 里，应用重启后仍在；文件名与 backup_rules.xml 的排除项一致，
 * 保证换机时不会把令牌同步到云备份。
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    /** 登录用户的资料快照（JSON 字符串），用于顶部昵称等本地展示。 */
    var userJson: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    /** 后端地址，形如 `http://10.0.2.2:8080/api/v1/`。 */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null) ?: BuildConfig.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(value)).apply()

    val isAuthenticated: Boolean get() = !accessToken.isNullOrBlank()

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "careerpilot_prefs"
        private const val KEY_ACCESS_TOKEN = "cp_access_token"
        private const val KEY_REFRESH_TOKEN = "cp_refresh_token"
        private const val KEY_USER = "cp_user"
        private const val KEY_BASE_URL = "cp_base_url"

        /**
         * 把用户填的服务器地址规整成 `scheme://host:port/api/v1/`。
         * 允许只填 `192.168.1.10:8080` 这种裸地址。
         */
        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return BuildConfig.DEFAULT_BASE_URL

            var url = trimmed
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            if (!url.contains("/api/v1")) {
                url = url.trimEnd('/') + "/api/v1/"
            } else {
                if (!url.endsWith("/")) url += "/"
            }
            return url
        }
    }
}
