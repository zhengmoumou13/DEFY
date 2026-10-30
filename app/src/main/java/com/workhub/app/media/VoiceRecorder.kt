package com.workhub.app.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var pcm: PcmVoiceRecorder? = null

    @Suppress("DEPRECATION")
    fun start(wav: Boolean = false, maxSeconds: Int = 60) {
        discard()
        val folder = File(context.cacheDir, "voice_drafts").apply { mkdirs() }
        val file = File.createTempFile("voice_", if (wav) ".wav" else ".m4a", folder)
        output = file
        if (wav) {
            val current = PcmVoiceRecorder(file, maxSeconds)
            pcm = current
            try { current.start() } catch (error: Exception) { discard(); throw error }
            return
        }
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        recorder = current
        try {
            current.setAudioSource(MediaRecorder.AudioSource.MIC)
            current.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            current.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            current.setAudioChannels(1)
            current.setAudioSamplingRate(16000)
            current.setAudioEncodingBitRate(64000)
            current.setOutputFile(file.path)
            current.prepare()
            current.start()
        } catch (failure: Exception) { discard(); throw failure }
    }

    fun stop(): File {
        pcm?.let { current ->
            try { return current.stop() }
            catch (error: Exception) { output?.delete(); throw error }
            finally { pcm = null }
        }
        val current = requireNotNull(recorder)
        val file = requireNotNull(output)
        try {
            current.stop()
            require(file.length() > 0) { "录音为空" }
            return file
        } catch (failure: Exception) { file.delete(); throw failure }
        finally { current.release(); recorder = null }
    }

    fun discard() {
        pcm?.let { runCatching { it.stop() } }; pcm = null
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        output?.delete()
        output = null
    }
}

fun appendVoiceInput(existing: String, transcript: String, maxLength: Int): String {
    val spoken = transcript.trim()
    require(spoken.isNotBlank()) { "识别内容为空，请重新录音" }
    val combined = if (existing.isBlank()) spoken else existing.trimEnd() + "\n" + spoken
    require(combined.length <= maxLength) { "合并后超过 $maxLength 字，请先精简原文或识别结果" }
    return combined
}
