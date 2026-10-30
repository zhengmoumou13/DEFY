package com.workhub.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object AnalysisPhotos {
    fun cameraFile(context: Context): File = File.createTempFile("capture_", ".jpg", directory(context, "analysis_camera"))

    fun cleanExpired(context: Context) {
        listOf("analysis_photos", "analysis_camera", "voice_drafts").forEach { name ->
            directory(context, name).listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        }
    }

    suspend fun prepare(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val folder = directory(context, "analysis_photos")
        val original = File.createTempFile("source_", ".tmp", folder)
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var output: File? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                original.outputStream().use { stream ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 25_000_000) { "单张原图请小于25MB" }
                        stream.write(buffer, 0, count)
                    }
                }
            } ?: throw IllegalArgumentException("无法读取图片，请重新选择")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "图片格式或尺寸不受支持，请换用 JPG / PNG 照片" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            decoded = BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IllegalArgumentException("图片解码失败，请重新选择")
            val bitmap = requireNotNull(decoded)
            val orientation = ExifInterface(original).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            output = File.createTempFile("photo_", ".jpg", folder)
            output.outputStream().use { require(oriented.compress(Bitmap.CompressFormat.JPEG, 82, it)) { "图片压缩失败" } }
            require(output.length() in 1..2_000_000) { "压缩后图片仍过大，请裁剪后重试" }
            ensureActive()
            output
        } catch (failure: Throwable) {
            output?.delete()
            throw failure
        } finally {
            if (oriented !== decoded) oriented?.recycle()
            decoded?.recycle()
            original.delete()
        }
    }

    suspend fun dataUrls(files: List<File>): List<String> = withContext(Dispatchers.IO) {
        files.map { file ->
            ensureActive()
            require(file.isFile && file.length() in 1..2_000_000) { "照片已过期，请重新选择" }
            "data:image/jpeg;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }
    }

    private fun directory(context: Context, name: String): File = File(context.cacheDir, name).apply { mkdirs() }
}
