package com.workhub.app.gym

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class GymExercise(val id: String, val name: String, val englishName: String, val bodyPart: String,
    val equipment: String, val target: String, val description: String = "", val instructions: List<String> = emptyList(), val hold: Boolean = false)
@Serializable
data class GymPrescription(val exerciseId: String, val slotId: String = UUID.randomUUID().toString(), val sets: Int = 3,
    val reps: Int = 8, val seconds: Int = 30, val weightKg: Double = 0.0, val restSeconds: Int = 90, val timed: Boolean = false) {
    fun valid() = sets in 1..20 && reps in 1..500 && seconds in 1..3600 && weightKg.isFinite() && weightKg in 0.0..1000.0 && restSeconds in 0..900
}
@Serializable
data class GymRoutine(val name: String, val exercises: List<GymPrescription>)
object GymCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(routine: GymRoutine) = json.encodeToString(routine)
    fun decode(content: String) = json.decodeFromString<GymRoutine>(content)
}
object GymCatalogue {
    @Volatile private var cached: List<GymExercise>? = null
    suspend fun load(context: Context): List<GymExercise> = cached ?: withContext(Dispatchers.IO) {
        cached ?: context.assets.open("opengym/exercises.json").bufferedReader().use {
            GymCodec.json.decodeFromString<List<GymExercise>>(it.readText())
        }.also { cached = it }
    }
    suspend fun starters(context: Context): List<GymRoutine> = withContext(Dispatchers.IO) {
        context.assets.open("opengym/starters.json").bufferedReader().use { GymCodec.json.decodeFromString(it.readText()) }
    }
}
fun gymLabel(value: String): String = mapOf("back" to "背部", "chest" to "胸部", "shoulders" to "肩部", "upper arms" to "上臂",
    "lower arms" to "前臂", "upper legs" to "大腿", "lower legs" to "小腿", "waist" to "腰腹", "cardio" to "有氧", "neck" to "颈部",
    "barbell" to "杠铃", "dumbbell" to "哑铃", "body weight" to "自重", "cable" to "绳索", "leverage machine" to "固定器械",
    "smith machine" to "史密斯机", "band" to "弹力带", "kettlebell" to "壶铃", "weighted" to "负重", "assisted" to "辅助器械") [value] ?: value
