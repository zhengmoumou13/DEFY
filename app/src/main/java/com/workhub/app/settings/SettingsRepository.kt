package com.workhub.app.settings

import com.workhub.app.nutrition.DietPlanPreferences
import com.workhub.app.nutrition.TanDietPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.workhub.app.ai.AiConfig
import com.workhub.app.data.BackupSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "settings")

data class UserSettings(
    val apiBaseUrl: String = AiConfig.BASE_URL,
    val apiKey: String = "",
    val model: String = AiConfig.MODEL,
    val heightCm: String = "",
    val weightKg: String = "",
    val goal: String = "减脂",
    val rimHeightCm: String = "305",
    val weeklyTarget: String = "4",
    val checkinRemindEnabled: Boolean = false,
    val checkinRemindHour: Int = 20,
    val onboarded: Boolean = false,
    val visionBaseUrl: String = "",
    val visionApiKey: String = "",
    val visionModel: String = "",
    val speechBaseUrl: String = "",
    val speechApiKey: String = "",
    val speechModel: String = "",
    val dietPlan: DietPlanPreferences = DietPlanPreferences(),
    val themeMode: String = "dark"
)

class SettingsRepository(private val context: Context) {
    private val apiKeyCipher = ApiKeyCipher()
    private val settingsJson = Json { ignoreUnknownKeys = true }

    private object Keys {
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val API_KEY_ENCRYPTED = stringPreferencesKey("api_key_encrypted")
        val MODEL = stringPreferencesKey("model")
        val HEIGHT = stringPreferencesKey("height_cm")
        val WEIGHT = stringPreferencesKey("weight_kg")
        val GOAL = stringPreferencesKey("goal")
        val RIM_HEIGHT = stringPreferencesKey("rim_height_cm")
        val WEEKLY_TARGET = stringPreferencesKey("weekly_target")
        val CHECKIN_REMIND = booleanPreferencesKey("checkin_remind")
        val CHECKIN_HOUR = intPreferencesKey("checkin_hour")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val VISION_BASE_URL = stringPreferencesKey("vision_base_url")
        val VISION_KEY = stringPreferencesKey("vision_key_encrypted")
        val VISION_MODEL = stringPreferencesKey("vision_model")
        val SPEECH_BASE_URL = stringPreferencesKey("speech_base_url")
        val SPEECH_KEY = stringPreferencesKey("speech_key_encrypted")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DIET_PLAN = stringPreferencesKey("tan_diet_plan")
        val SPEECH_MODEL = stringPreferencesKey("speech_model")
    }

    val settings: Flow<UserSettings> = context.dataStore.data
        .onStart { migrateLegacyAiConfig() }
        .catch { emit(emptyPreferences()) }
        .map { p ->
            val textBaseUrl = p[Keys.API_BASE_URL]?.takeIf { it.isNotBlank() } ?: AiConfig.BASE_URL
            val textKey = p[Keys.API_KEY_ENCRYPTED]
                ?.let { runCatching { apiKeyCipher.decrypt(it) }.getOrDefault("") }
                .orEmpty()
            val textModel = p[Keys.MODEL]?.takeIf { it.isNotBlank() } ?: AiConfig.MODEL
            val visionKey = p[Keys.VISION_KEY]
                ?.let { runCatching { apiKeyCipher.decrypt(it) }.getOrDefault("") }
                .orEmpty()
            UserSettings(
                apiBaseUrl = textBaseUrl,
                apiKey = textKey,
                model = textModel,
                heightCm = p[Keys.HEIGHT] ?: "",
                weightKg = p[Keys.WEIGHT] ?: "",
                goal = p[Keys.GOAL] ?: "减脂",
                rimHeightCm = p[Keys.RIM_HEIGHT] ?: "305",
                weeklyTarget = p[Keys.WEEKLY_TARGET] ?: "4",
                checkinRemindEnabled = p[Keys.CHECKIN_REMIND] ?: false,
                checkinRemindHour = p[Keys.CHECKIN_HOUR] ?: 20,
                onboarded = p[Keys.ONBOARDED] ?: false,
                visionBaseUrl = p[Keys.VISION_BASE_URL]?.takeIf { it.isNotBlank() } ?: AiConfig.BASE_URL,
                visionApiKey = visionKey.ifBlank { textKey },
                visionModel = normalizeVisionModel(p[Keys.VISION_MODEL]),
                speechBaseUrl = p[Keys.SPEECH_BASE_URL].orEmpty(),
                speechApiKey = p[Keys.SPEECH_KEY]?.let { runCatching { apiKeyCipher.decrypt(it) }.getOrDefault("") }.orEmpty(),
                speechModel = p[Keys.SPEECH_MODEL].orEmpty(),
                dietPlan = p[Keys.DIET_PLAN]?.let { runCatching { settingsJson.decodeFromString<DietPlanPreferences>(it) }.getOrNull() } ?: DietPlanPreferences(),
                themeMode = if (p[Keys.THEME_MODE] == "light") "light" else "dark"
            )
        }

    // Observe appearance without decrypting unrelated service credentials.
    val themeMode: Flow<String> = context.dataStore.data.map {
        if (it[Keys.THEME_MODE] == "light") "light" else "dark"
    }

    suspend fun saveTheme(mode: String) {
        require(mode == "light" || mode == "dark")
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun saveDietPlan(plan: DietPlanPreferences) {
        require(!plan.enabled || TanDietPlan.target(plan) != null) { "请先完善有效的饮食参数" }
        context.dataStore.edit { it[Keys.DIET_PLAN] = Json.encodeToString(plan) }
    }

    suspend fun setOnboarded() {
        context.dataStore.edit { p -> p[Keys.ONBOARDED] = true }
    }

    private suspend fun migrateLegacyAiConfig() {
        context.dataStore.edit { p ->
            val legacyKey = p[Keys.API_KEY].orEmpty()
            if (p[Keys.API_KEY_ENCRYPTED].isNullOrBlank() && legacyKey.isNotBlank()) {
                p[Keys.API_KEY_ENCRYPTED] = apiKeyCipher.encrypt(legacyKey)
            }
            if (p[Keys.MODEL]?.trim()?.lowercase() in setOf("deepseek", "deepseek ai")) {
                p[Keys.MODEL] = "deepseek-chat"
            }
            p[Keys.VISION_MODEL]?.let { model ->
                normalizeVisionModel(model).let { normalized ->
                    if (normalized != model) p[Keys.VISION_MODEL] = normalized
                }
            }
            p.remove(Keys.API_KEY)
        }
    }

    private fun normalizeVisionModel(model: String?): String = when (model?.trim()?.lowercase()) {
        "deepseek-v4.1-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp" -> AiConfig.VISION_MODEL
        null, "" -> AiConfig.VISION_MODEL
        else -> model.trim()
    }

    suspend fun saveAiConfig(baseUrl: String, apiKey: String, model: String) {
        val encrypted = apiKey.trim().takeIf { it.isNotBlank() }?.let(apiKeyCipher::encrypt)
        context.dataStore.edit { p ->
            p[Keys.API_BASE_URL] = baseUrl.trim()
            if (encrypted == null) p.remove(Keys.API_KEY_ENCRYPTED) else p[Keys.API_KEY_ENCRYPTED] = encrypted
            p.remove(Keys.API_KEY)
            p[Keys.MODEL] = model.trim()
        }
    }

    suspend fun saveMediaConfig(vision: Boolean, baseUrl: String, apiKey: String, model: String) {
        val encrypted = apiKey.trim().takeIf { it.isNotBlank() }?.let(apiKeyCipher::encrypt)
        context.dataStore.edit { prefs ->
            val urlKey = if (vision) Keys.VISION_BASE_URL else Keys.SPEECH_BASE_URL
            val secretKey = if (vision) Keys.VISION_KEY else Keys.SPEECH_KEY
            val modelKey = if (vision) Keys.VISION_MODEL else Keys.SPEECH_MODEL
            prefs[urlKey] = baseUrl.trim()
            prefs[modelKey] = model.trim()
            if (encrypted == null) prefs.remove(secretKey) else prefs[secretKey] = encrypted
        }
    }

    suspend fun save(
        heightCm: String,
        weightKg: String,
        goal: String,
        rimHeightCm: String,
        weeklyTarget: String,
        checkinRemindEnabled: Boolean,
        checkinRemindHour: Int
    ) {
        context.dataStore.edit { p ->
            p[Keys.HEIGHT] = heightCm
            p[Keys.WEIGHT] = weightKg
            p[Keys.GOAL] = goal
            p[Keys.RIM_HEIGHT] = rimHeightCm
            p[Keys.WEEKLY_TARGET] = weeklyTarget
            p[Keys.CHECKIN_REMIND] = checkinRemindEnabled
            p[Keys.CHECKIN_HOUR] = checkinRemindHour
        }
    }

    suspend fun exportBackupSettings(): BackupSettings {
        val current = settings.first()
        return BackupSettings(
            apiBaseUrl = current.apiBaseUrl,
            model = current.model,
            heightCm = current.heightCm,
            weightKg = current.weightKg,
            goal = current.goal,
            rimHeightCm = current.rimHeightCm,
            weeklyTarget = current.weeklyTarget,
            checkinRemindEnabled = current.checkinRemindEnabled,
            checkinRemindHour = current.checkinRemindHour,
            onboarded = current.onboarded,
            visionBaseUrl = current.visionBaseUrl,
            visionModel = current.visionModel,
            speechBaseUrl = current.speechBaseUrl,
            speechModel = current.speechModel,
            dietPlan = current.dietPlan,
            themeMode = current.themeMode
        )
    }

    suspend fun restoreBackupSettings(backup: BackupSettings) {
        context.dataStore.edit { p ->
            if (backup.apiBaseUrl.isNotBlank()) p[Keys.API_BASE_URL] = backup.apiBaseUrl
            if (backup.model.isNotBlank()) p[Keys.MODEL] = backup.model
            p[Keys.HEIGHT] = backup.heightCm
            p[Keys.WEIGHT] = backup.weightKg
            p[Keys.GOAL] = backup.goal
            p[Keys.RIM_HEIGHT] = backup.rimHeightCm
            p[Keys.WEEKLY_TARGET] = backup.weeklyTarget
            p[Keys.CHECKIN_REMIND] = backup.checkinRemindEnabled
            p[Keys.CHECKIN_HOUR] = backup.checkinRemindHour
            p[Keys.ONBOARDED] = backup.onboarded
            p[Keys.THEME_MODE] = if (backup.themeMode == "light") "light" else "dark"
            p[Keys.DIET_PLAN] = Json.encodeToString(backup.dietPlan.copy(enabled = backup.dietPlan.enabled && TanDietPlan.target(backup.dietPlan) != null))
            if (backup.visionBaseUrl.isNotBlank()) {
                if (p[Keys.VISION_BASE_URL]?.trimEnd('/') != backup.visionBaseUrl.trimEnd('/')) p.remove(Keys.VISION_KEY)
                p[Keys.VISION_BASE_URL] = backup.visionBaseUrl
            }
            if (backup.visionModel.isNotBlank()) p[Keys.VISION_MODEL] = backup.visionModel
            if (backup.speechBaseUrl.isNotBlank()) {
                if (p[Keys.SPEECH_BASE_URL]?.trimEnd('/') != backup.speechBaseUrl.trimEnd('/')) p.remove(Keys.SPEECH_KEY)
                p[Keys.SPEECH_BASE_URL] = backup.speechBaseUrl
            }
            if (backup.speechModel.isNotBlank()) p[Keys.SPEECH_MODEL] = backup.speechModel
        }
    }
}
