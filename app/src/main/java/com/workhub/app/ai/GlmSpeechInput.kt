package com.workhub.app.ai

import com.workhub.app.media.PcmWav
import kotlinx.serialization.json.*
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Dedicated ASR: it transcribes the input, rather than answering spoken questions. */
internal object GlmSpeechInput {
    const val MODEL = "glm-asr-2512"
    const val MAX_SECONDS = 30
    fun matches(model: String) = model.trim().equals(MODEL, ignoreCase = true)
    fun body(audio: ByteArray): MultipartBody {
        PcmWav.validate(audio, MAX_SECONDS)
        return MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", MODEL)
            .addFormDataPart("stream", "false")
            .addFormDataPart("file", "voice.wav", audio.toRequestBody("audio/wav".toMediaType()))
            .build()
    }
    fun transcript(body: String): String {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IllegalStateException("语音服务返回格式异常，请检查接口地址")
        val text = (root["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
        require(text.isNotBlank()) { "没有识别到文字，请靠近麦克风并重新录音" }
        return text
    }
}
