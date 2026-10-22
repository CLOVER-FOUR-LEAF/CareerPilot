package com.careerpilot.app.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 展示层的格式化工具。 */
object Format {

    private val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun dateTime(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        return parse(value)?.let { dateTime.format(it) } ?: value
    }

    fun date(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        return parse(value)?.let { dateOnly.format(it) } ?: value
    }

    /** ISO-8601（带时区偏移）→ Date；解析不了就返回 null。 */
    private fun parse(value: String): Date? = runCatching {
        val normalized = value.replace(" ", "T")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
        )
        patterns.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.CHINA).parse(normalized) }.getOrNull()
        }
    }.getOrNull()

    /** 分数展示：null → —，否则保留一位小数去掉多余的 .0 */
    fun score(value: Double?): String = when {
        value == null -> "—"
        value == value.toLong().toDouble() -> value.toLong().toString()
        else -> String.format(Locale.CHINA, "%.1f", value)
    }

    fun percent(value: Double?): String =
        if (value == null) "—" else "${score(value)}%"

    /** 解析状态 → 中文文案。 */
    fun parseStatus(status: String?): String = when (status?.uppercase()) {
        "PENDING" -> "待解析"
        "PROCESSING" -> "解析中"
        "SUCCESS" -> "已完成"
        "FAILED" -> "解析失败"
        else -> status ?: "—"
    }

    /** 任务状态 → 中文文案。 */
    fun taskStatus(status: String?): String = when (status?.uppercase()) {
        "PENDING" -> "排队中"
        "RUNNING" -> "执行中"
        "SUCCESS" -> "已完成"
        "FAILED" -> "执行失败"
        "CANCELLED" -> "已取消"
        else -> status ?: "—"
    }
}
