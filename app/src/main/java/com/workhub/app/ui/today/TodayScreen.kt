package com.workhub.app.ui.today

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.EventEntity
import com.workhub.app.data.TaskEntity
import com.workhub.app.ui.EmptyHint
import com.workhub.app.ui.Format
import com.workhub.app.ui.theme.GradientEnd
import com.workhub.app.ui.theme.GradientStart
import com.workhub.app.ui.theme.PriorityHigh
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    repo: AppRepository,
    settingsRepository: com.workhub.app.settings.SettingsRepository,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { repo.refreshRepeatingTasks() }

    val openTasks by repo.openTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val events by repo.allEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    val bodyRecords by repo.allBodyRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.workhub.app.settings.UserSettings()
    )
    val todayStr = LocalDate.now().toString()
    val fitnessLog by repo.observePlanLog("fitness", todayStr).collectAsStateWithLifecycle(initialValue = null)
    val dietLog by repo.observePlanLog("diet", todayStr).collectAsStateWithLifecycle(initialValue = null)
    val rim = settings.rimHeightCm.toDoubleOrNull() ?: 305.0
    val dunkReference = rim + 15.0
    val latestBody = bodyRecords.firstOrNull { it.maxReachCm != null }
    val fitnessLogs by repo.observePlanLogs("fitness").collectAsStateWithLifecycle(initialValue = emptyList())
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    // 欢迎弹窗：等 DataStore 真实值加载后再判断，避免每次启动都弹
    var settingsLoaded by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(false) }
    var showQuickTest by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        settingsRepository.settings.first()
        settingsLoaded = true
    }
    LaunchedEffect(settingsLoaded, settings.onboarded) {
        if (settingsLoaded && !settings.onboarded) showWelcome = true
    }
    val dunkProgressText = latestBody?.maxReachCm?.let { maxReach ->
        val rimGap = rim - maxReach
        val dunkGap = dunkReference - maxReach
        when {
            dunkGap <= 0 -> "已达到建议扣篮摸高线 · 继续提升控球与稳定性"
            rimGap <= 0 -> "已达到篮筐高度 · 距建议扣篮线 ${String.format("%.0f", dunkGap)}cm"
            else -> "距摸框 ${String.format("%.0f", rimGap)}cm · 距建议扣篮线 ${String.format("%.0f", dunkGap)}cm"
        }
    }
    val verticalJumpText = latestBody?.maxReachCm?.let { maxReach ->
        latestBody.standingReachCm?.let { standingReach ->
            "助跑垂直 ${String.format("%.0f", maxReach - standingReach)}cm"
        }
    }

    val today = LocalDate.now()
    val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    val todayEvents = events.filter { it.startTime in dayStart..dayEnd }

    // 全部待办任务（含无日期），实时排序：逾期 → 今天到期 → 其他/无日期
    val sortedOpen = openTasks.sortedWith(
        compareBy<TaskEntity>(
            { if (it.dueDate != null && it.dueDate!! < dayStart) 0 else if (it.dueDate != null && it.dueDate!! in dayStart..dayEnd) 1 else 2 },
            { it.dueDate ?: Long.MAX_VALUE }
        )
    )
    val shownTasks = sortedOpen.take(4)
    val totalOpen = sortedOpen.size

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Format.greeting()}，今天继续向上", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                TrainingHero(fitnessLog?.status, dunkProgressText, verticalJumpText) {
                    navController.navigate("fitness")
                }
            }
            item {
                val monday = today.with(java.time.DayOfWeek.MONDAY)
                val completed = fitnessLogs.filter { it.status == 1 }.map { it.date }.toSet()
                val count = (0..6).count { monday.plusDays(it.toLong()).let { day -> !day.isAfter(today) && day.toString() in completed } }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("本周节奏", style = MaterialTheme.typography.titleSmall)
                                Text("已完成 $count 天 · 目标 ${settings.weeklyTarget} 天", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { navController.navigate("stats") }) { Text("看趋势") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            (0..6).forEach { index ->
                                val day = monday.plusDays(index.toLong())
                                val done = !day.isAfter(today) && day.toString() in completed
                                val isToday = day == today
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                                        contentDescription = "$day，${if (done) "训练已完成" else if (isToday) "今天，未完成" else "未完成"}"
                                    }) {
                                    Text(listOf("一", "二", "三", "四", "五", "六", "日")[index],
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Box(Modifier.padding(top = 6.dp).size(32.dp).clip(CircleShape)
                                        .background(if (done) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                                        contentAlignment = Alignment.Center) {
                                        Text(if (done) "✓" else day.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                                            color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("训练与恢复", null, { navController.navigate("ai_hub") }) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeAction(Icons.Filled.SportsBasketball, "有球训练", "记录每一组命中", Modifier.weight(1f)) { navController.navigate("ball_training") }
                        HomeAction(Icons.Filled.Restaurant, "饮食恢复", if (dietLog?.status == 1) "今日已完成" else "补给与饮食记录", Modifier.weight(1f)) { navController.navigate("diet") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeAction(Icons.Filled.Bolt, "记录摸高", "更新弹跳基线", Modifier.weight(1f)) { showQuickTest = true }
                        HomeAction(Icons.Filled.ChatBubble, "问教练", "动作与训练建议", Modifier.weight(1f)) { navController.navigate("chat") }
                    }
                }
            }
            item {
                TextButton(onClick = { showSchedule = !showSchedule }, modifier = Modifier.fillMaxWidth()) {
                    Text("${if (showSchedule) "收起" else "展开"}今日安排 · $totalOpen 项待办 · ${todayEvents.size} 项日程")
                }
            }
            if (showSchedule) {
                item { SectionTitle("待办事项", totalOpen, { navController.navigate("tasks") }) }
                if (shownTasks.isEmpty()) {
                    item { Text("今天没有待办，留些时间给恢复。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(shownTasks, key = { "task_${it.id}" }) { task ->
                    TaskRow(
                        task = task,
                        onClick = { navController.navigate("task_edit?taskId=${task.id}") },
                        onToggle = {
                            scope.launch {
                                repo.updateTask(task.copy(status = if (task.status == 2) 0 else 2))
                            }
                        }
                    )
                }
                if (totalOpen > shownTasks.size) {
                    item {
                        TextButton(
                            onClick = { navController.navigate("tasks") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("还有 ${totalOpen - shownTasks.size} 项待办，查看全部 →")
                        }
                    }
                }
                item {
                    SectionTitle(
                        title = "今日日程",
                        count = todayEvents.size,
                        onSeeAll = { navController.navigate("calendar") },
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                if (todayEvents.isEmpty()) {
                    item { EmptyHint("今天没有其他日程", emoji = "📅") }
                }
                items(todayEvents.take(3), key = { "event_${it.id}" }) { e ->
                    EventRow(e) { navController.navigate("calendar") }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    // 周期弹跳复测对话框
    if (showQuickTest) {
        com.workhub.app.ui.body.QuickTestDialog(
            rim = rim,
            onDismiss = { showQuickTest = false },
            onSave = { sr, mr, w, vert, gap ->
                showQuickTest = false
                scope.launch {
                    repo.saveBodyRecord(
                        com.workhub.app.data.BodyRecordEntity(
                            date = LocalDate.now().toString(),
                            standingReachCm = sr,
                            maxReachCm = mr,
                            weightKg = w
                        )
                    )
                    val msg = buildString {
                        append("⚡ 已记录")
                        vert?.let { append(" · 垂直弹跳 ${String.format("%.0f", it)}cm") }
                        gap?.let {
                            if (it > 0) append(" · 距摸框 ${String.format("%.0f", it)}cm") else append(" · 已达到篮筐高度")
                        }
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 首次使用建档
    if (showWelcome) {
        OnboardingFlow(
            settings = settings,
            onSaveProfile = { height, weight, goal ->
                    settingsRepository.save(
                        heightCm = height,
                        weightKg = weight,
                        goal = goal,
                        rimHeightCm = settings.rimHeightCm,
                        weeklyTarget = settings.weeklyTarget,
                        checkinRemindEnabled = settings.checkinRemindEnabled,
                        checkinRemindHour = settings.checkinRemindHour
                    )
            },
            onFinish = {
                showWelcome = false
                scope.launch { settingsRepository.setOnboarded() }
            },
            onRecordBaseline = {
                showQuickTest = true
            }
        )
    }
}

@Composable
private fun TrainingHero(status: Int?, progress: String?, vertical: String?, onClick: () -> Unit) {
    val title = when (status) {
        1 -> "今天的训练，完成了"
        2 -> "继续今天的训练"
        3 -> "给身体一点恢复时间"
        else -> "每一次起跳，都算数"
    }
    val action = when (status) {
        1 -> "查看训练记录"
        2 -> "继续训练"
        3 -> "查看与调整计划"
        else -> "打开今日训练"
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = GradientStart)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(GradientStart, GradientEnd))).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TODAY / 今日训练", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFB08A), letterSpacing = 1.sp)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(progress ?: "从你的第一份计划开始，逐步积累弹跳与力量。",
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE0DEDA))
            if (vertical != null) Text(vertical, style = MaterialTheme.typography.labelLarge, color = Color(0xFFFFB08A))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB08A), contentColor = Color(0xFF391304))) {
                Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(action, modifier = Modifier.padding(horizontal = 12.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HomeAction(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 124.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    count: Int?,
    onSeeAll: (() -> Unit)?,
    seeAllLabel: String = "全部",
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .padding(end = 8.dp)
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (count != null && count > 0) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(seeAllLabel, color = tint)
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TaskRow(
    task: TaskEntity,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val isDone = task.status == 2
    val isOverdue = !isDone && task.dueDate != null && task.dueDate!! < System.currentTimeMillis()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isDone, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface
                )
                task.dueDate?.let {
                    Text(
                        Format.dueText(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) PriorityHigh
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: EventEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    Format.dateTimeText(event.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
