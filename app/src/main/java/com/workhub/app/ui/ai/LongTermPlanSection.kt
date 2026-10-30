package com.workhub.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.ai.AiClient
import com.workhub.app.ai.AiPrompts
import com.workhub.app.data.AppRepository
import com.workhub.app.data.BodyRecordEntity
import com.workhub.app.data.LongTermPlanEntity
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.ui.markdown.Markdown
import com.workhub.app.ui.theme.CourtOrange
import com.workhub.app.ui.theme.RecoveryGreen
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/** 从长期计划内容中提取某一天的安排（"## 第X天" 分节） */
internal fun extractDay(content: String, day: Int): String? {
    val pattern = Regex("""(?m)^\s*#{0,6}\s*第\s*(\d+)\s*[天日]\s*:?.*$""")
    val matches = pattern.findAll(content).toList()
    if (matches.isEmpty()) return null
    val idx = matches.indexOfFirst { it.groupValues[1].toInt() == day }
        .takeIf { it >= 0 }
        ?: matches.getOrNull(day - 1)?.let { matches.indexOf(it) }
        ?: return null
    val start = matches[idx].range.first
    val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else content.length
    return content.substring(start, end).trim()
}

private fun missingPlanDays(content: String, durationDays: Int): List<Int> {
    val pattern = Regex("""(?m)^\s*#{0,6}\s*第\s*(\d+)\s*[天日]\s*:?.*$""")
    val generatedDays = pattern.findAll(content)
        .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
        .toSet()
    return (1..durationDays).filterNot(generatedDays::contains)
}

private fun longPlanMaxTokens(durationDays: Int): Int = when {
    durationDays <= 7 -> 4_096
    durationDays <= 14 -> 6_144
    else -> 8_192
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LongTermPlanContent(
    modifier: Modifier = Modifier,
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController,
    type: String,          // "fitness" / "diet"
    title: String,
    gradient: List<Color>,
    buildPrompt: (UserSettings, String, Int, BodyRecordEntity?, BodyRecordEntity?) -> String
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val plans by repo.observeLongTermPlans(type).collectAsStateWithLifecycle(initialValue = emptyList())
    val bodyRecords by repo.allBodyRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())

    var selectedPlanId by rememberSaveable(type) { mutableStateOf<Long?>(null) }
    var showGenDialog by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var genError by remember { mutableStateOf<String?>(null) }
    var showFull by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val plan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()

    val latest = bodyRecords.firstOrNull()
    val previous = bodyRecords.getOrNull(1)

    // 当前进度
    val today = LocalDate.now()
    var currentDay = 1
    var statusText = ""
    if (plan != null) {
        val start = runCatching { LocalDate.parse(plan!!.startDate) }.getOrDefault(today)
        val pausedAt = plan!!.pausedAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val effectiveToday = pausedAt ?: today
        val dayIndex = ChronoUnit.DAYS.between(start, effectiveToday).toInt() + 1 - plan!!.pausedDays
        currentDay = dayIndex.coerceIn(1, plan!!.durationDays)
        statusText = when {
            pausedAt != null -> "计划已暂停于 ${plan!!.pausedAt} · 当前停在第 $currentDay 天"
            dayIndex < 1 -> "计划将于 ${plan!!.startDate} 开始"
            dayIndex > plan!!.durationDays -> "计划已全部完成，可以重新生成新计划"
            else -> "第 $dayIndex / ${plan!!.durationDays} 天"
        }
    }
    val dayContent = plan?.let { extractDay(it.content, currentDay) }
    val accentColor = if (type == "fitness") CourtOrange else RecoveryGreen
    val scrollState = remember(type) { ScrollState(initial = 0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (plan == null) {
            // 空状态
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "还没有长期$title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        "生成一份 7/14/30 天的计划，每天自动显示当天的安排",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    Button(onClick = { showGenDialog = true }) { Text("生成长期计划") }
                }
            }
        } else {
                // 进度卡
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        Modifier
                            .background(Brush.linearGradient(gradient))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (type == "fitness") "训练周期" else "饮食恢复周期",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.68f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    plan!!.name.replace(Regex("（\\d+天）$"), ""),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "$currentDay / ${plan!!.durationDays} 天",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除长期计划",
                                    tint = Color.White.copy(alpha = 0.74f)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { currentDay.toFloat() / plan!!.durationDays },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = accentColor,
                            trackColor = Color.White.copy(alpha = 0.16f)
                        )
                        Text(
                            statusText + " · 开始于 ${plan!!.startDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                // 今天的安排
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.EventAvailable,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        "今天 · 第 $currentDay 天安排",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(accentColor)
                        )
                        Column(Modifier.padding(16.dp)) {
                        if (dayContent != null) {
                            Markdown.Render(dayContent)
                        } else {
                            Markdown.Render(plan!!.content)
                            Text(
                                "（未找到按天分节，已显示完整计划）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showFull = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("查看完整", maxLines = 1) }
                    OutlinedButton(
                        onClick = { showGenDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("重新生成", maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = {
                        val current = plan ?: return@OutlinedButton
                        scope.launch {
                            if (current.pausedAt == null) {
                                repo.updateLongTermPlan(current.copy(pausedAt = LocalDate.now().toString()))
                            } else {
                                val pausedDate = runCatching { LocalDate.parse(current.pausedAt) }
                                    .getOrDefault(LocalDate.now())
                                val addedDays = ChronoUnit.DAYS.between(pausedDate, LocalDate.now())
                                    .toInt().coerceAtLeast(0)
                                repo.updateLongTermPlan(
                                    current.copy(
                                        pausedAt = null,
                                        pausedDays = current.pausedDays + addedDays
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (plan!!.pausedAt == null) "暂停当前周期" else "恢复当前周期")
                }

                Text(
                    "已保存计划（${plans.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                plans.forEach { savedPlan ->
                    val selected = savedPlan.id == plan?.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedPlanId = savedPlan.id
                            scope.launch { scrollState.animateScrollTo(0) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                accentColor.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    savedPlan.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (selected) "正在查看" else "查看",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "${savedPlan.startDate} 开始 · ${savedPlan.durationDays} 天" +
                                    savedPlan.goal.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

    // 生成对话框
    if (showGenDialog) {
        GenLongPlanDialog(
            title = title,
            generating = generating,
            error = genError,
            onDismiss = { if (!generating) showGenDialog = false },
            onGenerate = { durationDays, goal ->
                scope.launch {
                    generating = true
                    genError = null
                    try {
                        if (settings.apiKey.isBlank()) {
                            throw IllegalStateException("尚未配置文字 AI，请到“我的 → 文字 AI”填写并测试连接")
                        }
                        val prompt = buildPrompt(settings, goal, durationDays, latest, previous)
                        var content = AiClient.chat(
                            baseUrl = settings.apiBaseUrl,
                            apiKey = settings.apiKey,
                            model = settings.model,
                            messages = listOf(com.workhub.app.ai.ChatMessage("user", prompt)),
                            maxTokens = longPlanMaxTokens(durationDays),
                            temperature = 0.55
                        )
                        var missingDays = missingPlanDays(content, durationDays)
                        if (missingDays.isNotEmpty()) {
                            content = AiClient.chat(
                                baseUrl = settings.apiBaseUrl,
                                apiKey = settings.apiKey,
                                model = settings.model,
                                messages = listOf(
                                    com.workhub.app.ai.ChatMessage(
                                        "user",
                                        prompt + "\n\n上一次生成缺少第${missingDays.joinToString("、")}天。请重新输出完整的1-${durationDays}天计划，不要解释、不要省略任何一天。"
                                    )
                                ),
                                maxTokens = longPlanMaxTokens(durationDays),
                                temperature = 0.35
                            )
                            missingDays = missingPlanDays(content, durationDays)
                        }
                        if (missingDays.isNotEmpty()) {
                            throw IllegalStateException(
                                "AI 计划仍缺少第${missingDays.joinToString("、")}天，已停止保存，请重新生成"
                            )
                        }
                        repo.saveLongTermPlan(
                            LongTermPlanEntity(
                                type = type,
                                name = "${title}（${durationDays}天）",
                                startDate = LocalDate.now().toString(),
                                durationDays = durationDays,
                                goal = goal,
                                content = content
                            )
                        )
                        selectedPlanId = null
                        showGenDialog = false
                    } catch (e: Exception) {
                        genError = e.message ?: "生成失败，请重试"
                    } finally {
                        generating = false
                    }
                }
            }
        )
    }

    // 完整计划查看
    if (showFull && plan != null) {
        Dialog(
            onDismissRequest = { showFull = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "📖 完整计划",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                    )
                    Text(
                        plan!!.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
                    )
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Markdown.Render(plan!!.content)
                        Spacer(Modifier.height(24.dp))
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(plan!!.content))
                        }) { Text("复制") }
                        TextButton(onClick = { showFull = false }) { Text("关闭") }
                    }
                }
            }
        }
    }

    // 删除确认
    if (showDeleteConfirm && plan != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除长期计划") },
            text = { Text("确定删除「${plan!!.name}」吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        repo.deleteLongTermPlan(plan!!)
                        selectedPlanId = null
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenLongPlanDialog(
    title: String,
    generating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onGenerate: (durationDays: Int, goal: String) -> Unit
) {
    var duration by remember { mutableIntStateOf(14) }
    var goal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成长期$title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择周期", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(7 to "7 天", 14 to "14 天", 30 to "30 天").forEach { (d, label) ->
                        FilterChip(
                            selected = duration == d,
                            onClick = { duration = d },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("目标说明（可选）") },
                    placeholder = { Text("如：减掉 3kg / 练出马甲线 / 增肌 2kg") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (generating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            " AI 正在生成 ${duration} 天计划，可能需要 30-60 秒…",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGenerate(duration, goal.trim()) },
                enabled = !generating
            ) { Text("生成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !generating) { Text("取消") }
        }
    )
}
