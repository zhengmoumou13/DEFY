package com.workhub.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 任务：同时服务于"任务"页（待办清单）和"看板"页（工作流三列） */
@Entity(tableName = "tasks")
@Serializable
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String = "",
    val status: Int = 0,      // 0=待办 1=进行中 2=已完成
    val priority: Int = 1,    // 0=低 1=中 2=高
    val dueDate: Long? = null, // 截止时间（毫秒时间戳），null 表示无期限
    @ColumnInfo(defaultValue = "0")
    val repeatType: Int = 0,  // 0=不重复 1=每天 2=每周 3=每月
    val createdAt: Long = System.currentTimeMillis()
)

/** 笔记：支持简单 Markdown 文本与标签 */
@Entity(tableName = "notes")
@Serializable
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val tags: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 日程：支持提醒（提前 remindMinutes 分钟推送通知） */
@Entity(tableName = "events")
@Serializable
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null,
    val remindMinutes: Int = 15, // 0=不提醒
    val lastNotifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** 文件资料：保存在应用私有目录 workhub/ 下 */
@Entity(tableName = "files")
@Serializable
data class FileRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val size: Long = 0L,
    val mimeType: String = "application/octet-stream",
    val createdAt: Long = System.currentTimeMillis()
)

/** AI 计划：按日期缓存每日饮食/弹跳训练计划 */
@Entity(tableName = "ai_plans")
@Serializable
data class AiPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,          // "diet" 或 "fitness"
    val date: String,          // yyyy-MM-dd
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** 番茄钟专注记录：每次完成一个专注时段记一条 */
@Entity(tableName = "pomodoro")
@Serializable
data class PomodoroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val durationMinutes: Int,
    val completedAt: Long = System.currentTimeMillis()
)

/** AI 助手对话消息 */
@Entity(tableName = "chat_messages")
@Serializable
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,          // "user" / "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** 身体数据记录：扣篮功能性指标，按日期保存 */
@Entity(tableName = "body_records")
@Serializable
data class BodyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                // yyyy-MM-dd
    val heightCm: Double? = null,    // 身高
    val weightKg: Double? = null,    // 体重
    val bodyFatPct: Double? = null,  // 体脂（可选）
    val wingspanCm: Double? = null,  // 臂展
    val standingReachCm: Double? = null, // 站立摸高
    val maxReachCm: Double? = null,  // 助跑摸高
    val squatKg: Double? = null,     // 深蹲 1RM
    val deadliftKg: Double? = null,  // 硬拉 1RM
    val benchKg: Double? = null,     // 卧推 1RM
    val pullupCount: Double? = null, // 引体向上（次数）
    val plankSec: Double? = null,    // 平板支撑（秒）
    val standingJumpCm: Double? = null,         // 立定跳远（cm）
    val standingJumpReachCm: Double? = null,    // 原地纵跳摸高（cm）
    // 旧通用字段（兼容旧数据，界面不再使用）
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val muscleKg: Double? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** 长期计划：健身/饮食多天计划，按开始日期每天显示当天安排 */
@Entity(tableName = "long_term_plans")
@Serializable
data class LongTermPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,            // "fitness" / "diet"
    val name: String,
    val startDate: String,       // yyyy-MM-dd
    val durationDays: Int,
    val goal: String = "",
    val content: String,         // 完整多天计划（"## 第X天" 分节）
    val pausedAt: String? = null,
    val pausedDays: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** 计划执行打卡：饮食/训练计划每天的实际执行情况 */
@Entity(
    tableName = "plan_logs",
    indices = [androidx.room.Index(value = ["type", "date"], unique = true)]
)
@Serializable
data class PlanLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,            // "diet" / "fitness"
    val date: String,            // yyyy-MM-dd
    val status: Int = 0,         // 0=未记录 1=已完成 2=部分完成 3=未执行
    val detail: String = "",     // 实际执行详情
    val advice: String = "",     // AI 明日建议
    // 训练记录（fitness）
    val durationMinutes: Int? = null,  // 实际训练时长（分钟）
    val feeling: Int = 0,             // 0=未填 1=轻松 2=正好 3=很累 4=力竭
    val sleepQuality: Int = 0,
    val energyLevel: Int = 0,
    val sorenessLevel: Int = 0,
    val painLevel: Int = 0,
    val painArea: String = "",
    // 饮食记录（diet）
    val breakfast: String = "",       // 实际早餐
    val lunch: String = "",           // 实际午餐
    val dinner: String = "",          // 实际晚餐
    val snack: String = "",           // 实际加餐
    val waterCups: Int? = null,       // 喝水（杯）
    val updatedAt: Long = System.currentTimeMillis()
)

/** 计划逐项勾选：记录饮食/训练计划里每个要点的实际完成情况 */
@Entity(tableName = "plan_checks")
@Serializable
data class PlanCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,            // "diet" / "fitness" / ...
    val date: String,            // yyyy-MM-dd
    val itemKey: String,         // 计划要点文本的标识
    val label: String,           // 要点文本
    val checked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/** 训练过程记录：实时记录每组动作/重量/次数 */
@Entity(tableName = "workout_sets", indices = [androidx.room.Index(value = ["gymSessionId", "gymSlotKey"], unique = true)])
@Serializable
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,            // yyyy-MM-dd
    val exercise: String,        // 动作名称
    val gymSessionId: String? = null,
    @ColumnInfo(defaultValue = "''") val gymSlotKey: String = "",
    val weightKg: Double? = null,
    val reps: Int? = null,
    val metricType: String = "reps", // reps / duration / distance / height
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val heightCm: Double? = null,
    val rpe: Int? = null,
    val side: String = "",            // left / right / both
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ball_training_records")
@Serializable
data class BallTrainingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val category: String,
    val position: String,
    val attempts: Int = 0,
    val made: Int = 0,
    val durationMinutes: Int = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val movementKey: String = "",
    val planId: Long? = null,
    @ColumnInfo(defaultValue = "''") val planItemId: String = "",
    @ColumnInfo(defaultValue = "0") val durationSeconds: Int = 0
)

@Entity(tableName = "gym_templates")
@Serializable
data class GymTemplateEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val content: String)

@Entity(tableName = "gym_sessions")
@Serializable
data class GymSessionEntity(@PrimaryKey val id: String, val date: String, val content: String,
    val startedAt: Long = System.currentTimeMillis(), val completedAt: Long? = null)

/** A user-owned / separately licensed local demonstration, backed by the regular file archive. */
@Entity(tableName = "gym_media")
@Serializable
data class GymMediaEntity(@PrimaryKey val exerciseId: String, val fileId: Long, val attribution: String = "本地导入")
