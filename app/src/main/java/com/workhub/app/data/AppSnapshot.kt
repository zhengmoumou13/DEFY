package com.workhub.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 全量数据快照，用于 .dwb 备份与恢复。API Key 永不进入备份。 */
@Serializable
data class AppSnapshot(
    @SerialName("tasks") val tasks: List<TaskEntity> = emptyList(),
    @SerialName("notes") val notes: List<NoteEntity> = emptyList(),
    @SerialName("events") val events: List<EventEntity> = emptyList(),
    @SerialName("files") val files: List<FileRecordEntity> = emptyList(),
    @SerialName("aiPlans") val aiPlans: List<AiPlanEntity> = emptyList(),
    @SerialName("pomodoros") val pomodoros: List<PomodoroEntity> = emptyList(),
    @SerialName("chatMessages") val chatMessages: List<ChatMessageEntity> = emptyList(),
    @SerialName("bodyRecords") val bodyRecords: List<BodyRecordEntity> = emptyList(),
    @SerialName("longTermPlans") val longTermPlans: List<LongTermPlanEntity> = emptyList(),
    @SerialName("planLogs") val planLogs: List<PlanLogEntity> = emptyList(),
    @SerialName("planChecks") val planChecks: List<PlanCheckEntity> = emptyList(),
    @SerialName("workoutSets") val workoutSets: List<WorkoutSetEntity> = emptyList(),
    @SerialName("ballTrainingRecords") val ballTrainingRecords: List<BallTrainingRecordEntity> = emptyList(),
    val gymMedia: List<GymMediaEntity> = emptyList(),
    val gymTemplates: List<GymTemplateEntity> = emptyList(),
    val gymSessions: List<GymSessionEntity> = emptyList(),
    @SerialName("settings") val settings: BackupSettings? = null
)

@Serializable
data class BackupSettings(
    val apiBaseUrl: String = "",
    val model: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val goal: String = "减脂",
    val rimHeightCm: String = "305",
    val weeklyTarget: String = "4",
    val checkinRemindEnabled: Boolean = false,
    val checkinRemindHour: Int = 20,
    val onboarded: Boolean = false,
    val visionBaseUrl: String = "",
    val visionModel: String = "",
    val speechBaseUrl: String = "",
    val speechModel: String = "",
    val dietPlan: com.workhub.app.nutrition.DietPlanPreferences = com.workhub.app.nutrition.DietPlanPreferences(),
    val themeMode: String = "dark"
)
