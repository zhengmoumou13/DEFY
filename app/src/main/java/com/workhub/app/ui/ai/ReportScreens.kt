package com.workhub.app.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.ai.AiPrompts
import com.workhub.app.data.AppRepository
import com.workhub.app.data.TaskEntity
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import java.time.LocalDate
import java.time.ZoneId

/** 今日日报：结合当天真实数据生成 */
@Composable
fun DailyReportScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val tasks by repo.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val events by repo.allEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    val pomodoros by repo.allPomodoros.collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by repo.allNotes.collectAsStateWithLifecycle(initialValue = emptyList())

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    val dayTasks = tasks.filter { it.dueDate != null && it.dueDate!! in dayStart..dayEnd }
    val dayEvents = events.filter { it.startTime in dayStart..dayEnd }
    val dayPomos = pomodoros.filter { it.completedAt in dayStart..dayEnd }
    val notesToday = notes.count { it.createdAt in dayStart..dayEnd }

    val prompt = {
        val taskLines = if (dayTasks.isEmpty()) "无" else dayTasks.joinToString("\n") { t ->
            val mark = when (t.status) { 2 -> "✅ 已完成"; 1 -> "🔄 进行中"; else -> "⬜ 未开始" }
            "$mark ${t.title}"
        }
        val eventLines = dayEvents.joinToString("、") { it.title }
        AiPrompts.dailyReport(
            taskSummary = taskLines,
            eventSummary = eventLines,
            focusMinutes = dayPomos.sumOf { it.durationMinutes },
            focusSessions = dayPomos.size,
            notesCount = notesToday
        )
    }

    AiSceneScreen(
        repo = repo, settingsRepository = settingsRepository, navController = navController,
        type = "daily", title = "今日日报", icon = Icons.Filled.Today,
        gradient = listOf(Color(0xFF17110F), Color(0xFFD94124)),
        topicEnabled = false, topicPlaceholder = "",
        buildPrompt = { _, _, _, _ -> prompt() },
        contextCard = {
            DataSummaryCard(
                title = "今天的数据",
                lines = listOf(
                    "任务：完成 ${dayTasks.count { it.status == 2 }} / ${dayTasks.size}",
                    "日程：${if (dayEvents.isEmpty()) "无" else dayEvents.joinToString("、") { it.title }}",
                    "专注：${dayPomos.sumOf { it.durationMinutes }} 分钟（${dayPomos.size} 轮）",
                    "笔记：新增 $notesToday 篇"
                )
            )
        }
    )
}

/** 工作周报：结合本周统计数据生成 */
@Composable
fun WeeklyReportScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val tasks by repo.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val pomodoros by repo.allPomodoros.collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by repo.allNotes.collectAsStateWithLifecycle(initialValue = emptyList())

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekEnd = today
    fun rangeStart(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun rangeEnd(d: LocalDate): Long = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    val weekCompleted = tasks.filter {
        it.status == 2 && it.dueDate != null && it.dueDate!! in rangeStart(weekStart)..rangeEnd(weekEnd)
    }
    val weekCreated = tasks.filter { it.createdAt in rangeStart(weekStart)..rangeEnd(weekEnd) }
    val weekPomos = pomodoros.filter { it.completedAt in rangeStart(weekStart)..rangeEnd(weekEnd) }
    val notesThisWeek = notes.count { it.createdAt in rangeStart(weekStart)..rangeEnd(weekEnd) }
    val topTasks = weekCompleted.sortedByDescending { it.priority }.take(6).joinToString("、") { it.title }

    val prompt = {
        AiPrompts.weeklyReport(
            weekRange = "${weekStart.monthValue}月${weekStart.dayOfMonth}日 - ${weekEnd.monthValue}月${weekEnd.dayOfMonth}日",
            completedCount = weekCompleted.size,
            createdCount = weekCreated.size,
            focusMinutes = weekPomos.sumOf { it.durationMinutes },
            focusSessions = weekPomos.size,
            notesCount = notesThisWeek,
            topTasks = topTasks
        )
    }

    AiSceneScreen(
        repo = repo, settingsRepository = settingsRepository, navController = navController,
        type = "weekly", title = "工作周报", icon = Icons.Filled.Assessment,
        gradient = listOf(Color(0xFF111719), Color(0xFF315D6B)),
        topicEnabled = false, topicPlaceholder = "",
        buildPrompt = { _, _, _, _ -> prompt() },
        initialDateKey = weekStart.toString(),
        contextCard = {
            DataSummaryCard(
                title = "本周数据（${weekStart.monthValue}月${weekStart.dayOfMonth}日 起）",
                lines = listOf(
                    "完成任务：${weekCompleted.size} 项",
                    "新增任务：${weekCreated.size} 项",
                    "专注：${weekPomos.sumOf { it.durationMinutes }} 分钟（${weekPomos.size} 轮）",
                    "新增笔记：$notesThisWeek 篇"
                )
            )
        }
    )
}

@Composable
private fun DataSummaryCard(title: String, lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

/** 训练打卡周报：结合一周打卡与身体数据变化 */
@Composable
fun CheckinReportScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val fitnessLogs by repo.observePlanLogs("fitness").collectAsStateWithLifecycle(initialValue = emptyList())
    val bodyRecords by repo.allBodyRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())

    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val stats = com.workhub.app.ui.checkin.computeCheckinStats(fitnessLogs, today, 7)
    val target = settings.weeklyTarget.toIntOrNull() ?: 4

    val ascending = bodyRecords.asReversed()
    val jumpChange = buildString {
        val latest = ascending.firstOrNull()?.maxReachCm
        val weekAgo = ascending.firstOrNull { r -> runCatching { LocalDate.parse(r.date) }.getOrNull() != null && !LocalDate.parse(r.date).isBefore(today.minusDays(6)) }?.maxReachCm
        latest?.let { l ->
            val base = weekAgo ?: l
            val d = l - base
            append("最新 ${String.format("%.0f", l)}cm（${if (d >= 0) "+" else ""}${String.format("%.1f", d)}cm）")
        } ?: append("未记录")
    }
    val weightChange = buildString {
        val latest = ascending.firstOrNull()?.weightKg
        val weekAgo = ascending.firstOrNull { r -> runCatching { LocalDate.parse(r.date) }.getOrNull() != null && !LocalDate.parse(r.date).isBefore(today.minusDays(6)) }?.weightKg
        latest?.let { l ->
            val base = weekAgo ?: l
            val d = l - base
            append("最新 ${String.format("%.1f", l)}kg（${if (d >= 0) "+" else ""}${String.format("%.1f", d)}kg）")
        } ?: append("未记录")
    }
    val recentTitles = fitnessLogs.take(5).joinToString("；") { log ->
        "${log.date}：${com.workhub.app.ui.ai.logStatusText(log.status)}${if (log.detail.isNotBlank()) " ${log.detail.take(12)}" else ""}"
    }

    val prompt = {
        com.workhub.app.ai.AiPrompts.checkinWeekly(
            weekRange = "${weekStart.monthValue}月${weekStart.dayOfMonth}日 - ${today.monthValue}月${today.dayOfMonth}日",
            trainedDays = stats.loggedDays,
            doneCount = stats.doneCount,
            partialCount = stats.partialCount,
            missedCount = stats.missedCount,
            rate = stats.rate,
            streak = stats.streak,
            target = target,
            jumpChange = jumpChange,
            weightChange = weightChange,
            recentTitles = recentTitles
        )
    }

    AiSceneScreen(
        repo = repo, settingsRepository = settingsRepository, navController = navController,
        type = "checkin_weekly", title = "训练打卡周报", icon = Icons.Filled.FitnessCenter,
        gradient = listOf(Color(0xFF10201B), Color(0xFF178066)),
        topicEnabled = false, topicPlaceholder = "",
        buildPrompt = { _, _, _, _ -> prompt() },
        initialDateKey = weekStart.toString(),
        contextCard = {
            DataSummaryCard(
                title = "本周打卡数据（${weekStart.monthValue}月${weekStart.dayOfMonth}日起）",
                lines = listOf(
                    "训练打卡：${stats.executedDays} / $target 天（完成率 ${stats.rate}%）",
                    "执行情况：已完成 ${stats.doneCount} · 部分 ${stats.partialCount} · 未执行 ${stats.missedCount}",
                    "连续打卡：${stats.streak} 天",
                    "摸高：$jumpChange · 体重：$weightChange"
                )
            )
        }
    )
}
