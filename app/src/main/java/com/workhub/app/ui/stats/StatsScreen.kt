package com.workhub.app.ui.stats

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    repo: AppRepository,
    settingsRepository: com.workhub.app.settings.SettingsRepository,
    navController: NavHostController
) {
    val context = LocalContext.current
    val tasks by repo.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val pomodoros by repo.allPomodoros.collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by repo.allNotes.collectAsStateWithLifecycle(initialValue = emptyList())
    val files by repo.allFiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val fitnessLogs by repo.observePlanLogs("fitness").collectAsStateWithLifecycle(initialValue = emptyList())
    val dietLogs by repo.observePlanLogs("diet").collectAsStateWithLifecycle(initialValue = emptyList())
    val workoutSets by repo.observeAllWorkoutSets().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.workhub.app.settings.UserSettings()
    )
    val checkinStats = com.workhub.app.ui.checkin.computeCheckinStats(fitnessLogs, LocalDate.now(), 7)
    val dietCheckin = com.workhub.app.ui.checkin.computeCheckinStats(dietLogs, LocalDate.now(), 7)
    val weeklyTarget = settings.weeklyTarget.toIntOrNull() ?: 4

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    fun rangeStart(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun rangeEnd(d: LocalDate): Long = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    val todayTasks = tasks.filter { it.dueDate != null && it.dueDate!! in rangeStart(today)..rangeEnd(today) }
    val todayDone = todayTasks.count { it.status == 2 }
    val todayTotal = todayTasks.size

    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekCompleted = tasks.count {
        it.status == 2 && it.dueDate != null && it.dueDate!! in rangeStart(weekStart)..rangeEnd(today)
    }
    val weekCreated = tasks.count { it.createdAt in rangeStart(weekStart)..rangeEnd(today) }
    val weekFocusMinutes = pomodoros
        .filter { it.completedAt in rangeStart(weekStart)..rangeEnd(today) }
        .sumOf { it.durationMinutes }
    val todayFocusMinutes = pomodoros
        .filter { it.completedAt in rangeStart(today)..rangeEnd(today) }
        .sumOf { it.durationMinutes }
    val weekDateKeys = generateSequence(weekStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { it.toString() }
        .toSet()
    val weekFitnessLogs = fitnessLogs.filter { it.date in weekDateKeys }
    val weekWorkoutSets = workoutSets.filter { it.date in weekDateKeys }
    val weekTrainingMinutes = weekFitnessLogs.sumOf { it.durationMinutes ?: 0 }
    val weekTrainingVolume = weekWorkoutSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
    val weekTrainingReps = weekWorkoutSets.sumOf { it.reps ?: 0 }
    val weekExecuted = weekFitnessLogs.count { it.status == 1 || it.status == 2 }
    val weekExecutionRate = if (weekFitnessLogs.isEmpty()) 0 else weekExecuted * 100 / weekFitnessLogs.size
    val highFatigueDays = weekFitnessLogs.count { it.feeling >= 3 }
    val shareSummary = buildString {
        appendLine("🏀 DEFY · 跃过引力 本周训练周报")
        appendLine("训练：$weekExecuted 天｜$weekTrainingMinutes 分钟｜${weekWorkoutSets.size} 组")
        appendLine("总次数/跳数：$weekTrainingReps 次")
        if (weekWorkoutSets.any { it.weightKg != null }) {
            appendLine("负重容量：${String.format("%.0f", weekTrainingVolume)} kg")
        }
        appendLine("计划完成率：$weekExecutionRate%｜高疲劳反馈：$highFatigueDays 天")
        append("用数据看清差距，用计划完成每一次训练。")
    }

    // 最近 7 天完成趋势
    val weekDays = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val dailyDone = weekDays.map { d ->
        tasks.count { it.status == 2 && it.dueDate != null && it.dueDate!! in rangeStart(d)..rangeEnd(d) }
    }
    val maxDaily = dailyDone.maxOrNull()?.coerceAtLeast(1) ?: 1

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("训练与执行复盘") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, shareSummary)
                            context.startActivity(Intent.createChooser(intent, "分享训练周报"))
                        }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "分享训练周报")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                            CircularProgressIndicator(
                                progress = {
                                    if (todayTotal == 0) 0f else todayDone.toFloat() / todayTotal
                                },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 10.dp
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (todayTotal == 0) "0%" else "${(todayDone * 100 / todayTotal)}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "完成率",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(
                                "今日进度",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "完成 $todayDone / $todayTotal 项任务",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "今日专注 ${todayFocusMinutes} 分钟",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip("本周完成", "$weekCompleted 项", Modifier.weight(1f))
                    StatChip("本周新增", "$weekCreated 项", Modifier.weight(1f))
                    StatChip("本周专注", "$weekFocusMinutes 分钟", Modifier.weight(1f))
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "最近 7 天完成趋势",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            weekDays.zip(dailyDone).forEach { (d, count) ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .background(
                                                if (count > 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                            )
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${d.monthValue}/${d.dayOfMonth}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "本周训练负荷",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatChip("训练时长", "$weekTrainingMinutes 分钟", Modifier.weight(1f))
                            StatChip("完成率", "$weekExecutionRate%", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatChip("记录组数", "${weekWorkoutSets.size} 组", Modifier.weight(1f))
                            StatChip("总次数/跳数", "$weekTrainingReps 次", Modifier.weight(1f))
                        }
                        if (weekWorkoutSets.any { it.weightKg != null }) {
                            Text(
                                "负重训练容量 ${String.format("%.0f", weekTrainingVolume)} kg",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            when {
                                weekFitnessLogs.isEmpty() -> "本周还没有训练记录，完成一次训练后会生成负荷反馈。"
                                highFatigueDays >= 2 -> "本周已有 $highFatigueDays 天感到很累或力竭，下一次高冲击训练前优先确认睡眠、腿部酸痛和落地质量。"
                                weekWorkoutSets.isEmpty() -> "已记录训练状态，但逐组数据不足；记录重量与次数后才能更准确比较训练负荷。"
                                else -> "本周疲劳反馈处于可控范围，继续以动作质量和稳定落地作为加量前提。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "本周小结",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "本周你完成了 $weekCompleted 个任务、新增 $weekCreated 个计划，累计专注 $weekFocusMinutes 分钟。" +
                                if (weekFocusMinutes >= 150) "本周专注投入稳定，继续保持当前节奏。"
                                else "记得每天留一点时间给自己，加油！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "本周训练打卡",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "训练 ${checkinStats.executedDays} / $weeklyTarget 天",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "连续 ${checkinStats.streak} 天",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEA580C)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (checkinStats.executedDays.toFloat() / weeklyTarget).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已完成 ${checkinStats.doneCount} 天 · 部分完成 ${checkinStats.partialCount} 天 · 未执行 ${checkinStats.missedCount} 天 · 打卡率 ${checkinStats.rate}%" +
                                (if (dietCheckin.loggedDays > 0) "；饮食打卡 ${dietCheckin.executedDays}/${dietCheckin.loggedDays} 天" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        com.workhub.app.ui.checkin.CheckinCalendar(fitnessLogs, 14)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "内容概览",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatChip("笔记", "${notes.size} 篇", Modifier.weight(1f))
                            StatChip("文件", "${files.size} 个", Modifier.weight(1f))
                            StatChip("专注", "${pomodoros.size} 次", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
