package com.workhub.app.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile

/** Only owns the microphone between start and stop. PCM writes never run on the UI thread. */
internal class PcmVoiceRecorder(private val file: File, private val maxSeconds: Int) {
    private var input: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var failure: Exception? = null

    @SuppressLint("MissingPermission") // VoiceInputDialog checks RECORD_AUDIO before starting.
    fun start() {
        val size = AudioRecord.getMinBufferSize(PcmWav.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(size > 0) { "设备不支持此录音格式" }
        val audio = AudioRecord(MediaRecorder.AudioSource.MIC, PcmWav.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(size, 3200) * 2)
        input = audio
        try {
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "无法初始化麦克风" }
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未开始录音" }
            running = true
            worker = Thread({
                try {
                    RandomAccessFile(file, "rw").use { output ->
                        output.setLength(0); output.write(PcmWav.header(0))
                        val buffer = ByteArray(maxOf(size, 3200))
                        var total = 0
                        val limit = PcmWav.RATE * 2 * maxSeconds
                        while (running && total < limit) {
                            val count = audio.read(buffer, 0, minOf(buffer.size, limit - total))
                            if (count < 0) {
                                if (running) error("麦克风读取失败") else break
                            }
                            if (count > 0) { output.write(buffer, 0, count); total += count }
                        }
                        output.seek(0); output.write(PcmWav.header(total))
                    }
                } catch (error: Exception) { failure = error }
                finally { running = false; runCatching { audio.stop() } }
            }, "voice-pcm-writer").apply { isDaemon = true; start() }
        } catch (error: Exception) { audio.release(); input = null; throw error }
    }

    fun stop(): File {
        running = false
        runCatching { input?.stop() } // Unblock the read before joining the writer.
        try {
            worker?.join(2000)
            check(worker?.isAlive != true) { "录音停止超时，请重试" }
            failure?.let { throw it }
            check(file.length() >= PcmWav.HEADER_SIZE + PcmWav.RATE / 2) { "录音过短，请重试" }
            return file
        } finally { input?.release(); input = null }
    }
}
