package com.workhub.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    val temperature: Double? = 0.7,
    val stream: Boolean = false
)

@Serializable
data class ChatChoice(val message: ChatMessage)

@Serializable
data class ChatResponse(val choices: List<ChatChoice>)

/**
 * 通用 OpenAI 兼容接口客户端。
 * 默认适配 DeepSeek（https://api.deepseek.com），也支持 OpenAI、通义等
 * 调用应用内置的 OpenAI 兼容 /chat/completions 服务。
 */
object AiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        maxTokens: Int = 2048,
        temperature: Double = 0.7
    ): String {
        val fullMessages = if (systemPrompt != null) {
            listOf(ChatMessage("system", systemPrompt)) + messages
        } else messages
        val parsedUrl = MediaAiClient.endpoint(baseUrl, "chat/completions")
        val effectiveModel = normalizeModel(model)
        require(effectiveModel.isNotBlank()) { "模型名不能为空，请在 AI 设置中选择模型" }
        val rawBody = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                model = effectiveModel,
                messages = fullMessages,
                maxTokens = maxTokens.coerceIn(1, 16_384),
                temperature = temperatureForModel(effectiveModel, temperature)
            )
        )
        val body = GlmCompatibility.adapt(json.parseToJsonElement(rawBody).jsonObject).toString()
        val request = Request.Builder()
            .url(parsedUrl)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            var lastNetworkError: IOException? = null
            repeat(2) { attempt ->
                try {
                    return@withContext client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            val serverMessage = resp.body?.string()
                                ?.let { GlmCompatibility.errorHint(it) ?: extractServerMessage(it) }
                            val message = when (resp.code) {
                                401, 403 -> "AI 服务认证失败，请稍后重试"
                                429 -> serverMessage ?: "AI 服务配额不足或请求较多，请检查余额后重试"
                                in 500..599 -> "AI 服务暂时不可用，请稍后重试"
                                400 -> "请求参数错误：${serverMessage ?: "请检查接口地址、模型名或模型参数"}"
                                else -> "AI 请求失败（${resp.code}）：${serverMessage ?: "请稍后重试"}"
                            }
                            throw IllegalStateException(message)
                        }
                        val text = resp.body?.string().orEmpty()
                        GlmCompatibility.finalContent(text)
                    }
                } catch (e: IOException) {
                    lastNetworkError = e
                    if (attempt == 1) {
                        throw IllegalStateException("网络连接失败，请检查网络后重试", e)
                    }
                }
            }
            throw IllegalStateException("网络连接失败，请检查网络后重试", lastNetworkError)
        }
    }

    /** 单轮对话便捷方法 */
    suspend fun chatSingle(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): String = chat(baseUrl, apiKey, model, listOf(ChatMessage("user", prompt)))

    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): ConnectionTestResult =
        ConnectionTester().test(AiServiceKind.TEXT, baseUrl, apiKey, model)

    private fun temperatureForModel(model: String, temperature: Double): Double? {
        val normalized = model.trim().lowercase()
        return if (
            normalized.contains("reasoner") ||
            normalized.startsWith("o1") ||
            normalized.startsWith("o3") ||
            normalized.startsWith("o4")
        ) null else temperature.coerceIn(0.0, 1.5)
    }

    private fun normalizeModel(model: String): String {
        return when (model.trim().lowercase()) {
            "deepseek", "deepseek ai" -> "deepseek-chat"
            else -> model.trim()
        }
    }

    private fun extractServerMessage(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return null
        val parsed = runCatching {
            json.parseToJsonElement(trimmed)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.toString()
                ?.trim('"')
        }.getOrNull()
        return (parsed ?: trimmed)
            .replace(Regex("\\s+"), " ")
            .take(180)
            .takeIf { it.isNotBlank() }
    }
}
