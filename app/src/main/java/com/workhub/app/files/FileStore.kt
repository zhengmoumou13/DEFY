package com.workhub.app.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.workhub.app.data.FileRecordEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把手机里的文件复制到应用私有目录，实现"本地云盘" */
object FileStore {

    fun dir(context: Context): File =
        File(context.filesDir, "workhub").apply { mkdirs() }

    /** 从系统文件选择器导入，返回文件记录（未入库） */
    suspend fun import(context: Context, uri: Uri): FileRecordEntity? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = resolver.queryName(uri) ?: "文件-${UUID.randomUUID().toString().take(8)}"
            val size = resolver.querySize(uri) ?: 0L
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val safeName = sanitize(name)
            val target = File(dir(context), "${System.currentTimeMillis()}_$safeName")

            val copied = resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            } ?: return@withContext null

            if (copied <= 0L) {
                target.delete()
                return@withContext null
            }
            FileRecordEntity(
                name = safeName,
                path = target.absolutePath,
                size = if (size > 0L) size else copied,
                mimeType = mime
            )
        }

    fun deleteFile(file: File) {
        runCatching { file.delete() }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").ifBlank { "未命名" }

    private fun android.content.ContentResolver.queryName(uri: Uri): String? =
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun android.content.ContentResolver.querySize(uri: Uri): Long? =
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
}
