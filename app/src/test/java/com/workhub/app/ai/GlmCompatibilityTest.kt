package com.workhub.app.ai

import kotlinx.serialization.json.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class GlmCompatibilityTest {
    private fun payload(model: String, limit: Int = 128) = buildJsonObject { put("model", model); put("max_tokens", limit) }
    @Test fun standardEndpointHandlesBaseAndCompletePaths() {
        listOf(GlmCompatibility.BASE_URL, GlmCompatibility.BASE_URL + "/", GlmCompatibility.BASE_URL + "/chat/completions").forEach {
            val request = ConnectionTester().request(AiServiceKind.TEXT, it, "glm-key", "glm-4.7")
            assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", request.url.toString())
            assertEquals("Bearer glm-key", request.header("Authorization"))
            val json = Json.parseToJsonElement(Buffer().also { b -> request.body!!.writeTo(b) }.readUtf8()).jsonObject
            assertEquals("disabled", json["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        }
    }
    @Test fun fastModelsDisableThinkingButNewModelsDoNot() {
        listOf("glm-4.7", "glm-4.7-flash", "glm-4.6v", "glm-5.2").forEach {
            assertEquals("disabled", GlmCompatibility.adapt(payload(it))["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        }
        listOf("glm-5.3", "glm-5.3-flash").forEach {
            val probe = GlmCompatibility.adapt(payload(it), probe = true)
            assertFalse(probe.containsKey("thinking"))
            assertEquals("low", probe["reasoning_effort"]!!.jsonPrimitive.content)
            assertEquals(4096, probe["max_tokens"]!!.jsonPrimitive.int)
            assertEquals(8192, GlmCompatibility.adapt(payload(it, 2048))["max_tokens"]!!.jsonPrimitive.int)
        }
    }
    @Test fun unrelatedAndOlderModelsKeepTheirParameters() {
        listOf("deepseek-chat", "custom-model", "glm-4-plus", "glm-6").forEach {
            assertEquals(payload(it), GlmCompatibility.adapt(payload(it)))
        }
    }
    @Test fun visionAndTextShareGlmCompatibility() {
        val vision = Json.parseToJsonElement(MediaAiClient.visionBody("glm-4.6v", "检查动作", listOf("data:image/jpeg;base64," + "a".repeat(100)))).jsonObject
        assertEquals("disabled", vision["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("glm-4.6v", vision["model"]!!.jsonPrimitive.content)
    }
    @Test fun reasoningIsNeverTreatedAsAFinalAnswer() {
        assertEquals("计划正文", GlmCompatibility.finalContent("""{"choices":[{"message":{"content":"计划正文","reasoning_content":"private reasoning"},"finish_reason":"stop"}]}"""))
        val truncated = """{"choices":[{"message":{"content":null,"reasoning_content":"private reasoning"},"finish_reason":"length"}]}"""
        val error = assertThrows(IllegalStateException::class.java) { GlmCompatibility.finalContent(truncated) }
        assertTrue(error.message!!.contains("长度上限"));assertFalse(error.message!!.contains("private reasoning"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { ConnectionTester.validateResponse(AiServiceKind.TEXT, truncated) }.message!!.contains("长度上限"))
    }
    @Test fun billingErrorsExplainStandardApiWithoutEchoingServerData() {
        val hint = GlmCompatibility.errorHint("""{"error":{"code":"1113","message":"private api key"}}""")!!
        assertTrue(hint.contains("标准 API")); assertTrue(hint.contains("Coding Plan"));assertFalse(hint.contains("private"))
        assertNull(GlmCompatibility.errorHint("<html>bad gateway</html>"))
    }
}
