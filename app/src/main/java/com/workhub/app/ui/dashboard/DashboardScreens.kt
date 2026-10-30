package com.workhub.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.workhub.app.ui.theme.DefyBrand
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.workhub.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.*
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.ui.body.QuickTestDialog
import com.workhub.app.ui.today.OnboardingFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeDashboard(repo: AppRepository, settingsRepo: SettingsRepository, nav: NavHostController) {
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(UserSettings())
    val logs by repo.observePlanLogs("fitness").collectAsStateWithLifecycle(emptyList())
    val sets by repo.observeAllWorkoutSets().collectAsStateWithLifecycle(emptyList())
    val ball by repo.db.ballTrainingDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val bodies by repo.allBodyRecords.collectAsStateWithLifecycle(emptyList())
    val sessions by repo.db.gymDao().observeSessions().collectAsStateWithLifecycle(emptyList())
    val activeSession = sessions.firstOrNull { it.completedAt == null }
    val scope = rememberCoroutineScope()
    var onboarding by remember { mutableStateOf(false) }
    var test by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val trained = (logs.filter { it.status == 1 || it.status == 2 }.map { it.date } + sets.map { it.date } + ball.map { it.date }).toSet()
    val weekCount = (0L..6).map { monday.plusDays(it) }.count { !it.isAfter(today) && it.toString() in trained }
    val latest = bodies.firstOrNull { it.maxReachCm != null }
    val todayLog = logs.firstOrNull { it.date == today.toString() }
    val todayPlan by repo.observePlan("fitness", today.toString()).collectAsStateWithLifecycle(null)
    val completedSession = sessions.firstOrNull { it.date == today.toString() && it.completedAt != null }
    val completedToday = todayLog?.status == 1 || completedSession != null
    val trainingRoute = when {
        activeSession != null -> "gym_session/${activeSession.id}"
        completedSession != null -> "gym_session/${completedSession.id}"
        todayPlan != null || todayLog?.status == 1 -> "fitness"
        else -> "training"
    }
    LaunchedEffect(Unit) { onboarding = !settingsRepo.settings.first().onboarded; repo.refreshRepeatingTasks() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BrandHeader(today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINESE))) }
        item { FeatureCard(if (activeSession != null) "正在训练" else "今日训练", when {
            activeSession != null -> runCatching { com.workhub.app.gym.GymCodec.decode(activeSession.content).name }.getOrDefault("继续本次训练")
            completedToday -> "今日训练已完成"
            todayPlan != null -> "训练计划已就绪"
            else -> "安排今天的训练"
        }, when {
            activeSession != null -> "已记录的组次会保留，从上次的位置继续。"
            completedToday -> "查看训练记录，安排接下来的恢复。"
            todayPlan != null -> "查看动作与组次，按计划开始。"
            else -> "从力量模板或专项计划开始。"
        }, when {
            activeSession != null -> "继续未结束的训练"
            completedToday -> "查看今日记录"
            else -> "开始今日训练"
        }, Icons.Default.Bolt) { nav.navigate(trainingRoute) } }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrbitProgressCard(weekCount, settings.weeklyTarget.toIntOrNull() ?: 4, Modifier.weight(1f).fillMaxHeight())
                MetricCard(latest?.maxReachCm?.let { "${it.toInt()} cm" } ?: "待记录", "最近助跑摸高", Modifier.weight(1f).fillMaxHeight(), onClick = { test = true })
            }
        }
        item { SectionHeading("本周记录", "查看进展") { nav.navigate("progress") } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    (0..6).forEach { index ->
                        val day = monday.plusDays(index.toLong())
                        val done = !day.isAfter(today) && day.toString() in trained
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(listOf("一","二","三","四","五","六","日")[index], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (done) MaterialTheme.colorScheme.primary else if (day == today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
                                Text(if (done) "✓" else "${day.dayOfMonth}", color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeading("快捷入口") }
        item { ActionGroup(
            DashboardAction(Icons.Outlined.Restaurant, "今日饮食", "餐次安排、食物换算与饮食记录") { nav.navigate("nutrition") },
            DashboardAction(Icons.Outlined.FitnessCenter, "力量训练", "动作库与可编辑训练模板") { nav.navigate("gym") },
            DashboardAction(Icons.Outlined.Straighten, "记录摸高", "更新身体数据与训练基线") { test = true }
        ) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
    if (onboarding) OnboardingFlow(settings, { height, weight, goal -> settingsRepo.save(height, weight, goal, settings.rimHeightCm, settings.weeklyTarget, settings.checkinRemindEnabled, settings.checkinRemindHour) },
        { onboarding = false; scope.launch { settingsRepo.setOnboarded() } }, { test = true })
    if (test) QuickTestDialog(rim = settings.rimHeightCm.toDoubleOrNull() ?: 305.0, onDismiss = { test = false }, onSave = { sr,mr,w,_,_ ->
        scope.launch { try { repo.saveBodyRecord(BodyRecordEntity(date = today.toString(), standingReachCm = sr, maxReachCm = mr, weightKg = w)); test = false } catch (_: Exception) { error = "记录失败，请重试" } }
    })
}

@Composable
fun TrainingDashboard(repo: AppRepository, nav: NavHostController) {
    val templates by repo.db.gymDao().observeTemplates().collectAsStateWithLifecycle(emptyList())
    val sessions by repo.db.gymDao().observeSessions().collectAsStateWithLifecycle(emptyList())
    val active = sessions.firstOrNull { it.completedAt == null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("TRAINING", "训练", "选择训练方式，记录每一组表现。") }
        if (active != null) item { ActionRow(Icons.Outlined.PlayArrow, "继续未结束的训练", runCatching { com.workhub.app.gym.GymCodec.decode(active.content).name }.getOrDefault("本次训练")) { nav.navigate("gym_session/${active.id}") } }
        item { FeatureCard("力量训练", "从模板开始", "${templates.size} 个个人模板 · 动作库 · 逐组记录", "打开力量训练", Icons.Outlined.FitnessCenter) { nav.navigate("gym") } }
        item { SectionHeading("专项计划") }
        item { ActionGroup(
            DashboardAction(Icons.Outlined.Bolt, "弹跳与扣篮", "今日安排、长期计划和执行反馈") { nav.navigate("fitness") },
            DashboardAction(Icons.Outlined.SportsBasketball, "投篮与有球", "中投、三分、终结和运控") { nav.navigate("ball_training") }
        ) }
        item { SectionHeading("训练工具") }
        item { ActionGroup(
            DashboardAction(Icons.Outlined.PhotoCamera, "动作分析", "用照片检查动作细节") { nav.navigate("photo_analysis?scene=training") },
            DashboardAction(Icons.Outlined.ChatBubbleOutline, "AI 教练", "讨论计划、动作和恢复") { nav.navigate("chat") },
            DashboardAction(Icons.Outlined.Timer, "训练计时", "正计时、倒计时和历史记录") { nav.navigate("pomodoro") }
        ) }
    }
}

@Composable
fun NutritionDashboard(repo: AppRepository, settingsRepo: SettingsRepository, nav: NavHostController) {
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(UserSettings())
    val today = LocalDate.now().toString()
    val log by repo.observePlanLog("diet", today).collectAsStateWithLifecycle(null)
    val plan by repo.observePlan("diet", today).collectAsStateWithLifecycle(null)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("NUTRITION", "饮食", "安排餐食，掌握份量与恢复。") }
        item { FeatureCard("今日饮食", when {
            log?.status == 1 -> "今日饮食已记录"
            plan != null -> "餐食计划已就绪"
            else -> "安排今天怎么吃"
        }, if (plan == null) "根据身体数据与训练安排制定餐食。" else "按餐查看食材与份量，记录实际执行。",
            if (plan == null) "制定今日饮食" else "查看今日饮食", Icons.Outlined.Restaurant) { nav.navigate("diet") } }
        item { SectionHeading("饮食工具") }
        item { ActionGroup(
            DashboardAction(Icons.Outlined.SwapHoriz, "食物成分换算", "重量换营养、目标份量与食材替换") { nav.navigate("food_converter") },
            DashboardAction(Icons.Outlined.MenuBook, "谭成义饮食方案", if (settings.dietPlan.enabled) "已启用 · 查看目标与复盘" else "按体重和运动量设置参考目标") { nav.navigate("diet_reference") },
            DashboardAction(Icons.Outlined.PhotoCamera, "餐食照片分析", "查看份量与营养估算") { nav.navigate("photo_analysis?scene=meal") }
        ) }
        item { SectionHeading("实际饮食", "去记录") { nav.navigate("diet") } }
        item { ActionGroup(*listOf("早餐" to log?.breakfast, "午餐" to log?.lunch, "晚餐" to log?.dinner, "加餐" to log?.snack).map { (name, value) ->
            DashboardAction(Icons.Outlined.Restaurant, name, value?.takeIf { it.isNotBlank() } ?: "未记录") { nav.navigate("diet") }
        }.toTypedArray()) }
    }
}

@Composable
fun ProgressDashboard(repo: AppRepository, nav: NavHostController) {
    val sets by repo.observeAllWorkoutSets().collectAsStateWithLifecycle(emptyList())
    val logs by repo.observePlanLogs("fitness").collectAsStateWithLifecycle(emptyList())
    val bodies by repo.allBodyRecords.collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now()
    val days = (6L downTo 0L).map { today.minusDays(it).toString() }
    val ball by repo.db.ballTrainingDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val weekSets = sets.filter { it.date in days }
    val trained = (weekSets.map { it.date } + logs.filter { it.date in days && it.status in 1..2 }.map { it.date } + ball.filter { it.date in days }.map { it.date }).toSet()
    val maxSets = days.maxOf { d -> weekSets.count { it.date == d } }.coerceAtLeast(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("PROGRESS", "进展", "过去 7 天的训练与身体记录。") }
        item { Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricCard("${trained.size} 天", "有训练记录", Modifier.weight(1f).fillMaxHeight()); MetricCard("${weekSets.size} 组", "已记录组数", Modifier.weight(1f).fillMaxHeight()) } }
        item { SectionHeading("每日组数") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    days.forEach { date ->
                        val count = weekSets.count { it.date == date }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$count", style = MaterialTheme.typography.labelMedium)
                            Box(Modifier.fillMaxWidth().height((6 + 90 * count / maxSets).dp).clip(MaterialTheme.shapes.small).background(if(count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh))
                            Text(LocalDate.parse(date).dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item { SectionHeading("身体档案", "更新") { nav.navigate("body") } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(bodies.firstOrNull { it.weightKg != null }?.weightKg?.let { "$it kg" } ?: "待记录", "最近体重", Modifier.weight(1f))
            MetricCard(bodies.firstOrNull { it.maxReachCm != null }?.maxReachCm?.let { "${it.toInt()} cm" } ?: "待记录", "最近摸高", Modifier.weight(1f))
        } }
        item { SectionHeading("复盘与历史") }
        item { ActionGroup(
            DashboardAction(Icons.Outlined.Insights, "训练复盘与统计", "训练量、完成率和执行情况") { nav.navigate("stats") },
            DashboardAction(Icons.Outlined.AutoAwesome, "AI 周期复盘", "根据实际记录调整下一步计划") { nav.navigate("checkin_report") },
            DashboardAction(Icons.Outlined.History, "力量训练历史", "查看模板训练与逐组记录") { nav.navigate("gym") }
        ) }
    }
}

@Composable
fun ProfileDashboard(repo: AppRepository, settingsRepo: SettingsRepository, nav: NavHostController) {
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(UserSettings())
    val tasks by repo.openTasks.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var themeError by remember { mutableStateOf<String?>(null) }
    var tools by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("PROFILE", "我的", "管理训练档案与个人偏好。") }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("外观", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            listOf("light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                FilterChip(selected = settings.themeMode == mode, onClick = { scope.launch {
                    try { settingsRepo.saveTheme(mode); themeError = null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { themeError = "主题保存失败，请重试" }
                } }, label = { Text(label) }, leadingIcon = { Icon(if (mode == "light") Icons.Default.LightMode else Icons.Default.DarkMode, null, Modifier.size(16.dp)) })
            }
        } }
        themeError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.PersonOutline, null, modifier = Modifier.padding(16.dp).size(30.dp), tint = MaterialTheme.colorScheme.primary) }
                    Column { Text(settings.goal, style = MaterialTheme.typography.titleLarge); Text("每周目标 ${settings.weeklyTarget} 天", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider()
                Text("身高 ${settings.heightCm.ifBlank { "—" }} cm    体重 ${settings.weightKg.ifBlank { "—" }} kg", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { nav.navigate("settings") }) { Text("编辑档案与训练偏好 →") }
            }
        } }
        item { SectionHeading("AI 服务") }
        item { ActionGroup(
            DashboardAction(Icons.Outlined.ChatBubbleOutline, "文字 AI", if (settings.apiKey.isBlank()) "未配置" else settings.model) { nav.navigate("media_settings?mode=text") },
            DashboardAction(Icons.Outlined.PhotoCamera, "图片分析", "独立配置图片服务") { nav.navigate("media_settings?mode=vision") },
            DashboardAction(Icons.Outlined.Mic, "语音转文字", if (settings.speechApiKey.isBlank()) "未配置" else settings.speechModel) { nav.navigate("media_settings?mode=speech") }
        ) }
        item { SectionHeading("数据与工具") }
        item { ActionGroup(
            DashboardAction(Icons.Outlined.SaveAlt, "备份与恢复", "训练、模板、记录和演示素材") { nav.navigate("backup") },
            DashboardAction(Icons.Outlined.FolderOpen, "文件资料", "导入的文档与演示视频") { nav.navigate("files") },
            DashboardAction(Icons.Outlined.EventNote, "日常工具", "${tasks.size} 项待办 · ${if (tools) "收起工具" else "待办、笔记与日程"}") { tools = !tools }
        ) }
        if (tools) item { ActionGroup(
            DashboardAction(Icons.Outlined.CheckCircleOutline, "待办清单") { nav.navigate("tasks") },
            DashboardAction(Icons.Outlined.Notes, "笔记") { nav.navigate("notes") },
            DashboardAction(Icons.Outlined.CalendarMonth, "日程") { nav.navigate("calendar") },
            DashboardAction(Icons.Outlined.Dashboard, "任务看板") { nav.navigate("board") },
            DashboardAction(Icons.Outlined.AutoAwesome, "更多 AI 工具", "学习计划、周报与任务拆解") { nav.navigate("ai_hub") }
        ) }
        item {
            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DefyBrand()
                Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("版本 $version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    runCatching { uriHandler.openUri("https://github.com/zhengmoumou13/DEFY") }.onFailure {
                        android.widget.Toast.makeText(context, "无法打开浏览器，请访问 github.com/zhengmoumou13/DEFY", android.widget.Toast.LENGTH_LONG).show()
                    }
                }) {
                    Icon(Icons.Outlined.Code, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("开源项目 · AGPL-3.0", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
