package com.workhub.app.ai

import kotlinx.serialization.json.*

/** Parameters shared by real requests and connection probes. No provider key is inferred. */
internal object GlmCompatibility {
    const val BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
    const val DEFAULT_MODEL = "glm-4.7"

    private fun supportsFastMode(model: String): Boolean = Regex(
        "glm-(4\\.[567](?:v)?|5(?:\\.[12])?)(?:-(?:air|airx|flash|flashx|turbo))?",
        RegexOption.IGNORE_CASE
    ).matches(model.trim())

    fun adapt(body: JsonObject, probe: Boolean = false): JsonObject {
        val model = body["model"]?.jsonPrimitive?.content.orEmpty().lowercase()
        if (!model.startsWith("glm-")) return body
        return buildJsonObject {
            body.forEach { (key, value) -> put(key, value) }
            when {
                supportsFastMode(model) -> put("thinking", buildJsonObject { put("type", "disabled") })
                model == "glm-5.3" || model.startsWith("glm-5.3-") -> {
                    // GLM-5.3 rejects disabled thinking. Reserve space for reasoning and the final answer.
                    if (probe) put("reasoning_effort", "low")
                    put("max_tokens", maxOf(body["max_tokens"]?.jsonPrimitive?.intOrNull ?: 0, if (probe) 4096 else 8192))
                }
            }
        }
    }

    fun emptyReplyMessage(body: JsonObject): String {
        val choice = (body["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        return if (choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull == "length")
            "模型输出已达到长度上限，未完成正文；请更换模型或缩短输入后重试"
        else "模型未返回可读正文，请检查模型名和接口兼容性"
    }

    fun finalContent(body: String): String {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IllegalStateException("AI 返回格式异常，请检查接口地址")
        val message = ((root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("message") as? JsonObject
        val content = message?.get("content") as? JsonPrimitive
        return content?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(emptyReplyMessage(root))
    }

    fun errorHint(body: String): String? {
        val code = runCatching { Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content }.getOrNull()
        return if (code == "1113") "智谱标准 API 余额不足；Coding Plan 额度不能用于本 App，请检查标准 API 账户余额" else null
    }
}
