package com.workhub.app.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AiServiceKind { TEXT, VISION, SPEECH }
data class ConnectionTestResult(val elapsedMs: Long, val description: String)

/** A small real inference request; never sends user records or follows credential-bearing redirects. */
class ConnectionTester(private val client: OkHttpClient = defaultClient) {
    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(80, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
        // A generated 64 × 64 solid-red PNG, not a user's photograph.
        internal const val TEST_IMAGE = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAeUlEQVR4nO3PQQkAMAzAwCqpf3mTMBF7HINABFzm7H7dcEEDWtCAFjSgBQ1oQQNa0IAWNKAFDWhBA1rQgBY0oAUNaEEDWtCAFjSgBQ1oQQNa0IAWNKAFDWhBA1rQgBY0oAUNaEEDWtCAFjSgBQ1oQQNa0IAWNKAFj13TscEPf7JBMQAAAABJRU5ErkJggg=="
        internal fun silentWav(): ByteArray {
            val samples = 16_000
            return ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(samples * 2)
            }.array()
        }
        internal fun httpError(code: Int): String = when (code) {
            401, 403 -> "认证失败（HTTP $code），请检查该服务的 API Key 和模型权限"
            400, 404, 405, 415, 422 -> "请求不受支持（HTTP $code），请检查接口地址、模型名及服务能力"
            429 -> "额度不足或请求受限（HTTP 429），请检查账户余额后重试"
            in 300..399 -> "接口发生跳转（HTTP $code），请填写最终 HTTPS 地址"
            in 500..599 -> "服务端暂时不可用（HTTP $code），请稍后重试"
            else -> "连接失败（HTTP $code）"
        }
        internal fun validateResponse(kind: AiServiceKind, body: String): String {
            val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: throw IllegalStateException("接口已响应，但返回格式不是兼容的 JSON，请检查接口地址")
            if (kind == AiServiceKind.SPEECH) {
                val value = root["text"] as? JsonPrimitive
                require(value?.isString == true) { "语音接口已响应，但未返回兼容的 text 字段" }
                return "语音接口已接受内置静音片段；实际识别效果需用录音确认"
            }
            val content = runCatching { root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content") as? JsonPrimitive }.getOrNull()
            require(content?.isString == true && !content.content.isBlank()) { GlmCompatibility.emptyReplyMessage(root) }
            if (kind == AiServiceKind.VISION) {
                require(content!!.content.contains("红") || content.content.contains("red", ignoreCase = true)) { "模型已响应，但未识别出测试图颜色，请确认模型支持图片输入" }
                return "图片接口可用，模型已识别内置测试图"
            }
            return "文字接口可用，模型已返回测试回复"
        }
    }

    internal fun request(kind: AiServiceKind, baseUrl: String, key: String, model: String): Request {
        val secret = key.trim()
        require(secret.isNotEmpty() && secret.length <= 4096 && secret.all { it.code in 33..126 }) { "请填写有效的 API Key" }
        require(model.isNotBlank()) { "请填写模型名" }
        val glmAsr = kind == AiServiceKind.SPEECH && GlmSpeechInput.matches(model)
        val suffix = if (kind == AiServiceKind.SPEECH) "audio/transcriptions" else "chat/completions"
        val url = MediaAiClient.endpoint(baseUrl, suffix)
        val body = if (glmAsr) GlmSpeechInput.body(silentWav())
        else if (kind == AiServiceKind.SPEECH) MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model.trim()).addFormDataPart("response_format", "json")
            .addFormDataPart("file", "connection-test.wav", silentWav().toRequestBody("audio/wav".toMediaType())).build()
        else GlmCompatibility.adapt(buildJsonObject {
            put("model", model.trim()); put("stream", false); put("max_tokens", 128)
            put("messages", buildJsonArray { add(buildJsonObject {
                put("role", "user")
                if (kind == AiServiceKind.TEXT) put("content", "连接测试，请只回复 OK。")
                else put("content", buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", "这张图的主要颜色是什么？只回复颜色名称。") })
                    add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:image/png;base64,$TEST_IMAGE") }) })
                })
            }) })
        }, probe = true).toString().toRequestBody("application/json".toMediaType())
        return Request.Builder().url(url).header("Authorization", "Bearer $secret").post(body).build()
    }

    suspend fun test(kind: AiServiceKind, baseUrl: String, key: String, model: String): ConnectionTestResult {
        val request = request(kind, baseUrl, key, model)
        val started = System.nanoTime()
        val body = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val message = when (e) {
                        is SocketTimeoutException -> "连接超时，请检查网络或服务状态后重试"
                        is UnknownHostException -> "无法解析服务域名，请检查地址和网络"
                        is SSLException -> "HTTPS 证书验证失败，请检查服务证书"
                        else -> if (e is java.io.InterruptedIOException) "连接超时，请稍后重试" else "网络连接失败，请检查网络和接口地址"
                    }
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (!it.isSuccessful) throw IllegalStateException(GlmCompatibility.errorHint(it.peekBody(8192).string()) ?: httpError(it.code))
                            val source = it.body?.source() ?: throw IllegalStateException("服务返回为空")
                            source.request(65_537)
                            require(source.buffer.size <= 65_536) { "测试响应过大，请检查接口地址" }
                            source.readUtf8()
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (_: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("响应读取失败，请重试"))
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
        val description = validateResponse(kind, body)
        return ConnectionTestResult((System.nanoTime() - started) / 1_000_000, description)
    }
}
