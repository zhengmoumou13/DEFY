package com.workhub.app.gym

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.workhub.app.data.FileRecordEntity
import com.workhub.app.files.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object GymMediaImport {
    const val MAX_BYTES = 100L * 1024 * 1024
    /** Copy provider data before committing its DB association; never depend on transient URI access. */
    suspend fun copy(context: Context, uri: Uri, exerciseName: String): FileRecordEntity = withContext(Dispatchers.IO) {
        val target = File(FileStore.dir(context), "gym_demo_${UUID.randomUUID()}.mp4")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_BYTES) { "视频超过 100 MB，请先裁剪或压缩。" }
                        output.write(buffer, 0, n)
                    }
                    require(total > 0) { "视频为空，请重新选择。" }
                }
            } ?: error("无法读取该视频，请重新选择。")
            val retriever = MediaMetadataRetriever()
            val actualMime: String
            try {
                retriever.setDataSource(target.absolutePath)
                require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "所选文件不是可读取的视频。" }
                actualMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
            } finally { retriever.release() }
            FileRecordEntity(name = "$exerciseName · 动作演示", path = target.absolutePath, size = target.length(), mimeType = actualMime)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }
}
