package com.workhub.app.ai

import com.workhub.app.settings.UserSettings
import java.io.File
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class SpeechConfigurationTest {
    private val settings = UserSettings(apiBaseUrl="https://text.example/v1", apiKey="text-secret", model="text-model", visionBaseUrl="https://image.example/v1", visionApiKey="image-secret", visionModel="image-model", speechBaseUrl="https://speech.example/v1", speechApiKey="speech-secret", speechModel="speech-model")
    private fun withAudio(block: (File) -> Unit) {
        val file = File.createTempFile("speech-test", ".m4a")
        try { file.writeBytes(byteArrayOf(1,2,3)); block(file) } finally { file.delete() }
    }
    @Test fun transcriptionUsesOnlySpeechUrlCredentialAndModel() = withAudio { file ->
        val request = MediaAiClient.transcriptionRequest(settings, file)
        assertEquals("https://speech.example/v1/audio/transcriptions", request.url.toString())
        assertEquals("Bearer speech-secret", request.header("Authorization"))
        val buffer = Buffer(); request.body!!.writeTo(buffer); val body = buffer.readUtf8()
        assertTrue(body.contains("speech-model")); assertFalse(body.contains("text-model")); assertFalse(body.contains("image-model"))
        assertEquals(3, (request.body as MultipartBody).size)
    }
    @Test fun missingSpeechFieldsNeverFallBackToOtherServices() = withAudio { file ->
        listOf(settings.copy(speechBaseUrl=""), settings.copy(speechApiKey=""), settings.copy(speechModel="")).forEach { config ->
            assertThrows(IllegalArgumentException::class.java) { MediaAiClient.transcriptionRequest(config, file) }
        }
    }
    @Test fun completeEndpointIsNotDuplicatedAndInsecureHostsFail() = withAudio { file ->
        val config = settings.copy(speechBaseUrl="https://speech.example/v1/audio/transcriptions")
        assertEquals(config.speechBaseUrl, MediaAiClient.transcriptionRequest(config,file).url.toString())
        assertThrows(IllegalArgumentException::class.java) { MediaAiClient.transcriptionRequest(settings.copy(speechBaseUrl="http://speech.example/v1"),file) }
    }
}
