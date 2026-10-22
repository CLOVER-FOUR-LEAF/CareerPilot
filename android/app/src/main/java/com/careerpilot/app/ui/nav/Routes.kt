package com.careerpilot.app.ui.nav

/** 全局路由表。 */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val DASHBOARD = "dashboard"
    const val RESUME = "resume"
    const val JD = "jd"
    const val MATCH = "match"
    const val PROFILE = "profile"

    const val OPTIMIZE = "optimize"
    const val INTERVIEW = "interview"
    const val COVER_LETTER = "cover_letter"
    const val CAREER_PLAN = "career_plan"

    const val RESUME_DETAIL = "resume/{resumeId}"
    const val JD_DETAIL = "jd/{jdId}"
    const val MATCH_DETAIL = "match/{recordId}"
    const val SERVER_SETTINGS = "server_settings"

    fun resumeDetail(id: Long) = "resume/$id"
    fun jdDetail(id: Long) = "jd/$id"
    fun matchDetail(recordId: String) = "match/$recordId"
}

/** 底部导航栏展示的 5 个主 Tab。 */
val TAB_ROUTES = setOf(
    Routes.DASHBOARD,
    Routes.RESUME,
    Routes.JD,
    Routes.MATCH,
    Routes.PROFILE,
)
