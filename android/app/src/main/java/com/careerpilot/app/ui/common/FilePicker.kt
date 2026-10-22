package com.careerpilot.app.ui.common

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 从系统文件选择器读回来的文件。 */
data class PickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    val sizeLabel: String
        get() = when {
            bytes.size >= 1024 * 1024 -> String.format("%.1f MB", bytes.size / 1024.0 / 1024.0)
            bytes.size >= 1024 -> String.format("%.1f KB", bytes.size / 1024.0)
            else -> "${bytes.size} B"
        }
}

/** 系统文件选择器不支持「按类型过滤」时的兜底 MIME。 */
object FilePicker {

    /** 上传体积上限，与 Nginx 的 client_max_body_size 保持一致。 */
    const val MAX_BYTES = 20L * 1024 * 1024

    suspend fun read(context: Context, uri: Uri): PickedFile? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = queryDisplayName(resolver, uri) ?: "resume"
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            if (bytes.size > MAX_BYTES) {
                throw IllegalStateException("文件超过 20MB，请压缩后再上传")
            }
            PickedFile(name = name, mimeType = mime, bytes = bytes)
        }.getOrNull()
    }

    fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
