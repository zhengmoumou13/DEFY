package com.workhub.app.ai

import com.workhub.app.media.PcmWav
import com.workhub.app.media.appendVoiceInput
import com.workhub.app.settings.UserSettings
import okio.Buffer
import okhttp3.MultipartBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GlmSpeechInputTest {
    private fun wav(seconds: Int = 1) = PcmWav.header(seconds * 32_000) + ByteArray(seconds * 32_000)
    private val settings = UserSettings(apiKey="text-key", apiBaseUrl="https://text.example/v1", model="text-model", visionApiKey="vision-key", speechBaseUrl="https://speech.example/v4", speechApiKey="speech-key", speechModel="glm-asr-2512")
    @Test fun audioRequestUsesOnlyIndependentSpeechConfiguration() {
        val file = File.createTempFile("voice", ".wav")
        try {
            file.writeBytes(wav())
            val request = MediaAiClient.transcriptionRequest(settings, file)
            assertEquals("https://speech.example/v4/audio/transcriptions",request.url.toString())
            assertEquals("Bearer speech-key",request.header("Authorization"))
            val text = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            assertTrue(text.contains("glm-asr-2512"));assertTrue(text.contains("voice.wav"));assertTrue(text.contains("audio/wav"))
            assertFalse(text.contains("text-key"));assertFalse(text.contains("vision-key"));assertFalse(text.contains("text-model"));assertFalse(text.contains("response_format"))
            val stream = (request.body as MultipartBody).parts.single { it.headers?.get("Content-Disposition")?.contains("name=\"stream\"") == true }
            assertEquals("false", Buffer().also { stream.body.writeTo(it) }.readUtf8())
            listOf(settings.copy(speechApiKey=""),settings.copy(speechBaseUrl=""),settings.copy(speechModel="")).forEach { config ->
                assertThrows(IllegalArgumentException::class.java) { MediaAiClient.transcriptionRequest(config,file) }
            }
        } finally { file.delete() }
    }
    @Test fun completeEndpointAndProbeUseAsrProtocol() {
        val request = ConnectionTester().request(AiServiceKind.SPEECH,"https://speech.example/v4/audio/transcriptions","key"," GLM-ASR-2512 ")
        assertEquals("https://speech.example/v4/audio/transcriptions",request.url.toString())
        val text = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertTrue(text.contains("voice.wav"));assertFalse(text.contains("response_format"));assertFalse(text.contains("messages"))
    }
    @Test fun pcmValidationRejectsM4aTruncationOversizeAndWrongChannels() {
        PcmWav.validate(wav(),30);PcmWav.validate(wav(30),30)
        listOf(byteArrayOf(1,2,3),wav().dropLast(2).toByteArray(),wav(31),wav().also { it[22]=2 }).forEach {
            assertThrows(IllegalArgumentException::class.java) { PcmWav.validate(it,30) }
        }
    }
    @Test fun onlyTranscriptionTextCanBecomeInput() {
        assertEquals("我今天应该练什么？",GlmSpeechInput.transcript("""{"text":"我今天应该练什么？"}"""))
        listOf("{}","""{"text":null}""","""{"text":12}""","""{"choices":[{"message":{"content":"这是训练计划"}}]}""").forEach {
            assertThrows(IllegalArgumentException::class.java) { GlmSpeechInput.transcript(it) }
        }
    }
    @Test fun silenceCanProbeEndpointButCannotBecomeInput() {
        val response = """{"text":""}"""
        assertTrue(ConnectionTester.validateResponse(AiServiceKind.SPEECH,response).contains("实际识别效果"))
        assertThrows(IllegalArgumentException::class.java) { GlmSpeechInput.transcript(response) }
    }
    @Test fun appendingTranscriptPreservesExistingDraftAndRejectsOverflow() {
        assertEquals("原来的文字\n三组深蹲",appendVoiceInput("原来的文字","三组深蹲",100))
        assertThrows(IllegalArgumentException::class.java) { appendVoiceInput("原来的文字","三组深蹲",5) }
    }
}
