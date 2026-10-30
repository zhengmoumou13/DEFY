package com.workhub.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.ui.theme.ArenaBlack
import com.workhub.app.ui.theme.IronGraphite
import com.workhub.app.ui.theme.CourtOrange
import com.workhub.app.ui.theme.RecoveryGreen
import com.workhub.app.ui.theme.SteelBlue
import java.time.DayOfWeek
import java.time.LocalDate

private data class MenuEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val route: String? = null,
    val tint: Color = CourtOrange,
    val action: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val bodyRecords by repo.allBodyRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val fitnessLogs by repo.observePlanLogs("fitness").collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by repo.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAbout by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val weekStart = today.with(DayOfWeek.MONDAY)
    val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
        .filter { !it.isAfter(today) }
        .map(LocalDate::toString)
        .toSet()
    val weekLogs = fitnessLogs.filter { it.date in weekDates }
    val completedTraining = weekLogs.count { it.status == 1 }
    val executedTraining = weekLogs.count { it.status == 1 || it.status == 2 }
    val weeklyTarget = settings.weeklyTarget.toIntOrNull()?.coerceIn(1, 7) ?: 4
    val trainingProgress = (completedTraining.toFloat() / weeklyTarget).coerceIn(0f, 1f)
    val openTasks = tasks.count { it.status != 2 }
    val latestReach = bodyRecords.firstOrNull { it.maxReachCm != null }?.maxReachCm
    val profileReady = settings.heightCm.isNotBlank() && settings.weightKg.isNotBlank()
    val profileProgress = listOf(
        settings.heightCm.isNotBlank(),
        settings.weightKg.isNotBlank(),
        bodyRecords.isNotEmpty(),
        settings.goal.isNotBlank()
    ).count { it }

    val trainingEntries = listOf(
        MenuEntry(Icons.Filled.FitnessCenter, "力量训练 · openGym", "动作库、模板与逐组记录", "gym", CourtOrange),
        MenuEntry(Icons.Filled.SportsBasketball, "有球训练", "中投、三分、罚球、上篮与运控", "ball_training", CourtOrange),
        MenuEntry(Icons.Filled.QueryStats, "训练复盘", "本周完成 $executedTraining 天 · 查看负荷和恢复", "stats", SteelBlue),
        MenuEntry(Icons.Filled.MonitorWeight, "弹跳数据", "${bodyRecords.size} 条记录 · 摸高与力量趋势", "body", CourtOrange),
        MenuEntry(Icons.Filled.DashboardCustomize, "任务看板", "$openTasks 项待办 · 管理训练之外的事项", "board", RecoveryGreen),
        MenuEntry(Icons.Filled.CalendarMonth, "日程提醒", "训练安排与到期提醒", "calendar", SteelBlue)
    )
    val intelligenceEntries = listOf(
        MenuEntry(Icons.Filled.AutoAwesome, "AI 教练中心", "训练计划、饮食恢复与周报", "ai_hub", CourtOrange),
        MenuEntry(Icons.Filled.ChatBubble, "问扣篮教练", "动作技术、负荷与恢复建议", "chat", SteelBlue),
        MenuEntry(Icons.Filled.Timer, "专注计时器", "正计时、倒计时与历史记录", "pomodoro", RecoveryGreen),
        MenuEntry(Icons.Filled.Folder, "文件资料", "本地训练资料与文档", "files", SteelBlue)
    )
    val systemEntries = listOf(
        MenuEntry(Icons.Filled.Settings, "训练设置", "档案、AI 服务、提醒与训练目标", "settings", CourtOrange),
        MenuEntry(Icons.Filled.SaveAlt, "数据备份", "导出 .dwb 文件，换机也能恢复", "backup", RecoveryGreen),
        MenuEntry(Icons.Filled.Info, "关于产品", "版本信息、使用建议与数据说明", tint = SteelBlue) { showAbout = true }
    )

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("我的") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProfileHero(settings, profileReady) { navController.navigate("settings") }
            }
            item {
                TrainingPulseCard(
                    completedTraining = completedTraining,
                    weeklyTarget = weeklyTarget,
                    progress = trainingProgress,
                    latestReach = latestReach,
                    onTraining = { navController.navigate("fitness") },
                    onStats = { navController.navigate("stats") }
                )
            }
            if (profileProgress < 4) {
                item { ProfileCompletenessCard(profileProgress) { navController.navigate("settings") } }
            }
            item { MenuSection("训练管理", trainingEntries, navController) }
            item { MenuSection("智能与资料", intelligenceEntries, navController) }
            item { MenuSection("系统与安全", systemEntries, navController) }
            item { PrivacyCard { navController.navigate("backup") } }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于 DEFY · 跃过引力") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("把训练计划、身体数据、执行记录和 AI 建议放进同一套工作流。")
                    Text("建议每 7–14 天，在相同热身和场地条件下复测摸高。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("数据保存在本机；API Key 使用 Android Keystore 加密保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("完成") } }
        )
    }
}

@Composable
private fun ProfileHero(settings: UserSettings, profileReady: Boolean, onSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = ArenaBlack)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(ArenaBlack, IronGraphite)))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SportsBasketball, contentDescription = null, tint = CourtOrange, modifier = Modifier.size(31.dp))
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("DEFY · 跃过引力", style = MaterialTheme.typography.labelLarge, color = CourtOrange)
                        Text(
                            if (profileReady) "训练档案已就绪" else "建立训练档案",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Text(
                            "${settings.goal} · 每周目标 ${settings.weeklyTarget} 天",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.68f),
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "打开训练设置", tint = Color.White)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroMetric("身高", settings.heightCm.ifBlank { "--" }, "cm", Modifier.weight(1f))
                    HeroMetric("体重", settings.weightKg.ifBlank { "--" }, "kg", Modifier.weight(1f))
                    HeroMetric("篮筐", settings.rimHeightCm.ifBlank { "305" }, "cm", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 3.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
            Text(" $unit", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
        }
    }
}

@Composable
private fun TrainingPulseCard(
    completedTraining: Int,
    weeklyTarget: Int,
    progress: Float,
    latestReach: Double?,
    onTraining: () -> Unit,
    onStats: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("本周训练", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (completedTraining >= weeklyTarget) "目标已完成，保持状态并关注恢复"
                        else "距本周目标还差 ${weeklyTarget - completedTraining} 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text("$completedTraining/$weeklyTarget 天", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PulseMetric("最近摸高", latestReach?.let { "${it.toInt()} cm" } ?: "待记录", Modifier.weight(1f))
                PulseMetric("训练节奏", if (completedTraining > 0) "稳定建立中" else "今天开始", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onTraining, modifier = Modifier.weight(1f)) { Text("今日训练") }
                OutlinedButton(onClick = onStats, modifier = Modifier.weight(1f)) { Text("查看复盘") }
            }
        }
    }
}

@Composable
private fun PulseMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(11.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ProfileCompletenessCard(readyCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text("完善训练档案", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("已完成 $readyCount/4 项，AI 建议会更贴合你的真实状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun MenuSection(title: String, entries: List<MenuEntry>, navController: NavHostController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            entries.forEachIndexed { index, entry ->
                MenuRow(entry) { entry.route?.let(navController::navigate) ?: entry.action?.invoke() }
                if (index != entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(entry.tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(entry.icon, contentDescription = null, tint = entry.tint, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyCard(onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text("本地数据保护", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("数据默认留在设备，建议定期导出 .dwb 备份", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) { Text("去备份") }
        }
    }
}
