package com.workhub.app.ui.ai

import com.workhub.app.ui.media.VoiceInputButton

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.ai.AiClient
import com.workhub.app.ai.AiPrompts
import com.workhub.app.ai.ChatMessage
import com.workhub.app.data.AiPlanEntity
import com.workhub.app.data.AppRepository
import com.workhub.app.data.BodyRecordEntity
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.ui.Format
import com.workhub.app.ui.markdown.Markdown
import com.workhub.app.ui.theme.ArenaBlack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


// ---------- 今日执行记录表单 ----------

/** 今日执行记录表单状态（训练 + 饮食共用） */
data class LogFormState(
    val status: Int = 0,
    val duration: String = "",
    val feeling: Int = 0,
    val sleepQuality: Int = 0,
    val energyLevel: Int = 0,
    val sorenessLevel: Int = 0,
    val painLevel: Int = 0,
    val painArea: String = "",
    val detail: String = "",
    val breakfast: String = "",
    val lunch: String = "",
    val dinner: String = "",
    val snack: String = "",
    val water: String = ""
) {
    fun fromEntity(e: com.workhub.app.data.PlanLogEntity?): LogFormState =
        if (e == null) this else LogFormState(
            status = e.status,
            duration = e.durationMinutes?.toString() ?: "",
            feeling = e.feeling,
            sleepQuality = e.sleepQuality,
            energyLevel = e.energyLevel,
            sorenessLevel = e.sorenessLevel,
            painLevel = e.painLevel,
            painArea = e.painArea,
            detail = e.detail,
            breakfast = e.breakfast,
            lunch = e.lunch,
            dinner = e.dinner,
            snack = e.snack,
            water = e.waterCups?.toString() ?: ""
        )
}

// ---------- 入口 ----------

@Composable
fun DietPlanScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val today = LocalDate.now().toString()
    val fitnessPlan by repo.observePlan("fitness", today).collectAsStateWithLifecycle(initialValue = null)
    val fitnessLog by repo.observePlanLog("fitness", today).collectAsStateWithLifecycle(initialValue = null)
    val crossInfo = buildCrossInfo("训练", fitnessPlan?.content, fitnessLog?.status, fitnessLog?.detail)
    AiSceneScreen(
        repo = repo, settingsRepository = settingsRepository, navController = navController,
        type = "diet", title = "饮食计划", icon = Icons.Filled.Restaurant,
        gradient = listOf(Color(0xFF174D47), Color(0xFF267965)),
        topicEnabled = true, topicPlaceholder = "特别要求（可选）：如 忌口、素食、少油…",
        supportLongTerm = true,
        crossContext = crossInfo,
        buildPrompt = { s, t, latest, prev -> AiPrompts.diet(s, t, AiPrompts.bodyDataLine(latest, prev, s), crossInfo) }
    )
}

@Composable
fun FitnessPlanScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val today = LocalDate.now().toString()
    val dietPlan by repo.observePlan("diet", today).collectAsStateWithLifecycle(initialValue = null)
    val dietLog by repo.observePlanLog("diet", today).collectAsStateWithLifecycle(initialValue = null)
    val crossInfo = buildCrossInfo("饮食", dietPlan?.content, dietLog?.status, dietLog?.detail)
    AiSceneScreen(
        repo = repo, settingsRepository = settingsRepository, navController = navController,
        type = "fitness", title = "弹跳训练计划", icon = Icons.Filled.FitnessCenter,
        gradient = listOf(com.workhub.app.ui.theme.GradientStart, com.workhub.app.ui.theme.GradientEnd),
        topicEnabled = true, topicPlaceholder = "特别要求（可选）：如 今天练腿、脚踝不适、只有30分钟…",
        supportLongTerm = true,
        crossContext = crossInfo,
        buildPrompt = { s, t, latest, prev -> AiPrompts.fitness(s, t, AiPrompts.bodyDataLine(latest, prev, s), crossInfo) }
    )
}

@Composable
fun StudyPlanScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    AiSceneScreen(
        repo = repo, settingsRepository = settingsRepository, navController = navController,
        type = "study", title = "学习计划", icon = Icons.Filled.MenuBook,
        gradient = listOf(Color(0xFF182126), Color(0xFF315D6B)),
        topicEnabled = true, topicPlaceholder = "学习主题（可选）：如 英语、算法、产品设计…",
        buildPrompt = { s, t, _, _ -> AiPrompts.study(s, t) }
    )
}

// ---------- 核心界面 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSceneScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController,
    type: String,
    title: String,
    icon: ImageVector,
    gradient: List<Color>,
    topicEnabled: Boolean,
    topicPlaceholder: String,
    buildPrompt: (UserSettings, String, BodyRecordEntity?, BodyRecordEntity?) -> String,
    supportLongTerm: Boolean = false,
    initialDateKey: String = LocalDate.now().toString(),
    contextCard: (@Composable () -> Unit)? = null,
    crossContext: String = ""
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedDate by rememberSaveable(type) {
        mutableStateOf(runCatching { LocalDate.parse(initialDateKey) }.getOrDefault(LocalDate.now()))
    }
    val dateKey = selectedDate.toString()
    val isFutureDate = selectedDate.isAfter(LocalDate.now())
    val plan by repo.observePlan(type, dateKey).collectAsStateWithLifecycle(initialValue = null)
    val planLog by repo.observePlanLog(type, dateKey).collectAsStateWithLifecycle(initialValue = null)
    val planChecks by repo.observePlanChecks(type, dateKey).collectAsStateWithLifecycle(initialValue = emptyList())
    val typeLogs by repo.observePlanLogs(type).collectAsStateWithLifecycle(initialValue = emptyList())
    val workoutSets by repo.observeWorkoutSets(dateKey).collectAsStateWithLifecycle(initialValue = emptyList())
    val allWorkoutSets by repo.observeAllWorkoutSets().collectAsStateWithLifecycle(initialValue = emptyList())
    val history by repo.observePlansByType(type).collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val bodyRecords by repo.allBodyRecords.collectAsStateWithLifecycle(initialValue = emptyList())

    var topic by remember { mutableStateOf("") }
    var selectedSection by rememberSaveable(type) { mutableStateOf(0) }
    val latestBody = bodyRecords.firstOrNull()
    val previousBody = bodyRecords.getOrNull(1)

    // 长期计划 → 今日映射
    val longTermPlan by repo.observeLatestPlan(type).collectAsStateWithLifecycle(initialValue = null)
    val todayDate = LocalDate.now()
    val dayOfPlan = remember(longTermPlan, selectedDate) {
        longTermPlan?.let { p ->
            val start = runCatching { LocalDate.parse(p.startDate) }.getOrDefault(todayDate)
            val pausedAt = p.pausedAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val effectiveDate = if (pausedAt != null && selectedDate.isAfter(pausedAt)) pausedAt else selectedDate
            (java.time.temporal.ChronoUnit.DAYS.between(start, effectiveDate).toInt() + 1 - p.pausedDays)
        } ?: 0
    }
    val mappedContent = remember(longTermPlan, dayOfPlan) {
        longTermPlan?.let { p ->
            if (dayOfPlan in 1..p.durationDays) extractDay(p.content, dayOfPlan) else null
        }
    }
    // 长期计划映射优先；可用"独立今日计划"临时切换
    var dailyOverride by rememberSaveable { mutableStateOf(false) }
    val usingMappedView = !dailyOverride && mappedContent != null
    val effectiveContent = if (usingMappedView) mappedContent ?: "" else (plan?.content ?: "")
    val compactPlan = type in setOf("fitness", "diet") && effectiveContent.isNotBlank()

    // 最近 7 天完成率（用于按完成度调整后续计划）
    val recentStats = remember(typeLogs) {
        com.workhub.app.ui.checkin.computeCheckinStats(typeLogs, LocalDate.now(), 7)
    }
    val adjustmentLine = remember(recentStats, typeLogs) {
        if (recentStats.loggedDays == 0) "" else buildString {
            append("最近7天")
            append(if (type == "fitness") "训练" else "饮食")
            append("打卡完成率 ${recentStats.rate}%（${recentStats.executedDays}/${recentStats.loggedDays}天执行）")
            if (type == "fitness") {
                val recent = typeLogs.take(7)
                val painDays = recent.count { it.painLevel >= 3 }
                val tiredDays = recent.count { it.sorenessLevel >= 4 || it.energyLevel in 1..2 }
                if (painDays > 0) append("；其中${painDays}天疼痛等级≥3")
                if (tiredDays > 0) append("；${tiredDays}天恢复状态偏低")
            }
        }
    }

    // 今日执行记录（详细表单）
    var logForm by remember { mutableStateOf(LogFormState().fromEntity(planLog)) }
    var logSaved by remember { mutableStateOf(false) }
    LaunchedEffect(planLog?.id) {
        logForm = LogFormState().fromEntity(planLog)
        logSaved = false
    }
    // 组装打卡实体
    fun buildLogEntity(advice: String = ""): com.workhub.app.data.PlanLogEntity =
        com.workhub.app.data.PlanLogEntity(
            type = type, date = dateKey,
            status = logForm.status,
            detail = logForm.detail.trim(),
            durationMinutes = logForm.duration.trim().toIntOrNull(),
            feeling = logForm.feeling,
            sleepQuality = logForm.sleepQuality,
            energyLevel = logForm.energyLevel,
            sorenessLevel = logForm.sorenessLevel,
            painLevel = logForm.painLevel,
            painArea = logForm.painArea.trim(),
            breakfast = logForm.breakfast.trim(),
            lunch = logForm.lunch.trim(),
            dinner = logForm.dinner.trim(),
            snack = logForm.snack.trim(),
            waterCups = logForm.water.trim().toIntOrNull(),
            advice = advice
        )

    // AI 明日建议（打卡后自动生成，也可手动重新生成）
    var adviceLoading by remember { mutableStateOf(false) }
    var adviceText by remember { mutableStateOf<String?>(planLog?.advice ?: "") }
    LaunchedEffect(planLog?.id) {
        if (planLog?.advice?.isNotBlank() == true) adviceText = planLog?.advice
    }
    val generateAdvice = {
        scope.launch {
            adviceLoading = true
            adviceText = null
            try {
                if (settings.apiKey.isBlank()) {
                    throw IllegalStateException("尚未配置文字 AI，请到“我的 → 文字 AI”填写并测试连接")
                }
                val todayPlanLine = effectiveContent.lineSequence().firstOrNull { it.isNotBlank() }?.take(60) ?: title
                val kind = if (type == "fitness") "训练" else "饮食恢复"
                val effectiveStatus = if (logForm.status == 0) 1 else logForm.status
                val setSummary = if (workoutSets.isNotEmpty()) {
                    workoutSets.take(10).joinToString("；") { s ->
                        "${s.exercise} ${workoutMetricText(s)}"
                    }
                } else "未记录"
                val executionDetails = if (type == "fitness") {
                    "- 训练时长：${logForm.duration.ifBlank { "未记录" }} 分钟\n" +
                        "- 主观感受等级：${logForm.feeling}\n" +
                        "- 训练前状态：睡眠${logForm.sleepQuality}/5，精力${logForm.energyLevel}/5，酸痛${logForm.sorenessLevel}/5，疼痛${logForm.painLevel}/5 ${logForm.painArea}\n" +
                        "- 训练组记录：$setSummary"
                } else {
                    "- 早餐：${logForm.breakfast.ifBlank { "未记录" }}\n" +
                        "- 午餐：${logForm.lunch.ifBlank { "未记录" }}\n" +
                        "- 晚餐：${logForm.dinner.ifBlank { "未记录" }}\n" +
                        "- 加餐：${logForm.snack.ifBlank { "未记录" }}\n" +
                        "- 饮水：${logForm.water.ifBlank { "未记录" }} 杯"
                }
                val prompt = """
你是「DEFY · 跃过引力」的训练复盘教练。请只依据真实记录，为用户生成明日的${kind}建议。

今日原计划摘要：$todayPlanLine
今日执行状态：${logStatusText(effectiveStatus)}
用户补充：${logForm.detail.ifBlank { "未填写" }}
$executionDetails
最近 7 天执行率：${recentStats.rate}%（${recentStats.executedDays}/${recentStats.loggedDays} 天）

要求：
1. 不默认表扬，也不把一次未完成解释为意志力问题；先客观总结数据。
2. 若信息不足，明确说明无法判断疲劳、疼痛或能量状态。
3. 不要求用户把今天漏掉的高强度内容全部补到明天。
4. 训练建议写清主目标、强度、时长和是否需要恢复；饮食建议写清最优先的一餐或补给调整。
5. 出现疼痛、肿胀、跛行或明显动作失控时，建议停止高冲击训练并咨询专业人员。

请按以下结构输出：
## 今日观察
- 2-3 条有记录依据的结论
## 明日调整
- 最重要的一项调整
- 明日${kind}安排
## 需要留意
- 一个恢复或风险提醒
""".trimIndent()
                val reply = AiClient.chatSingle(
                    settings.apiBaseUrl, settings.apiKey, settings.model, prompt
                )
                adviceText = reply
                // 持久化到当天的打卡记录
                repo.savePlanLog(buildLogEntity(reply))
            } catch (e: Exception) {
                adviceText = e.message ?: "生成失败"
            } finally {
                adviceLoading = false
            }
        }
    }

    val saveLog = {
        scope.launch {
            repo.savePlanLog(buildLogEntity(adviceText ?: ""))
            logSaved = true
            // 打卡后自动生成明日建议
            generateAdvice()
        }
    }

    // 计划逐项勾选
    val parsedPlanItems = remember(effectiveContent, type) { parsePlanItems(effectiveContent, type) }
    val planItems = remember(parsedPlanItems, planChecks) {
        val savedItems = planChecks.associateBy { it.itemKey }
        parsedPlanItems.map { item ->
            savedItems[item.key]?.label
                ?.takeIf { it.isNotBlank() }
                ?.let { item.copy(text = it) }
                ?: item
        }
    }
    val checkedMap = remember(planChecks) { planChecks.associate { it.itemKey to it.checked } }
    val toggleItem: (PlanCheckItem, Boolean) -> Unit = { item, checked ->
        val newMap = checkedMap.toMutableMap().apply { this[item.key] = checked }
        val done = planItems.count { newMap[it.key] == true }
        val newStatus = when {
            planItems.isEmpty() -> logForm.status
            done >= planItems.size -> 1
            done > 0 -> 2
            else -> 3
        }
        logForm = logForm.copy(status = newStatus)
        scope.launch { repo.togglePlanCheck(type, dateKey, item.key, item.text, checked) }
    }

    // 训练过程记录（每组重量）
    var setExercise by remember { mutableStateOf("") }
    var setWeight by remember { mutableStateOf("") }
    var setReps by remember { mutableStateOf("") }
    var setMetricType by remember { mutableStateOf("reps") }
    var setRpe by remember { mutableStateOf("") }
    var setSide by remember { mutableStateOf("") }
    LaunchedEffect(dateKey) {
        dailyOverride = false
        setExercise = ""
        setWeight = ""
        setReps = ""
        setMetricType = "reps"
        setRpe = ""
        setSide = ""
    }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showTrainingMode by rememberSaveable(type, dateKey) { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<AiPlanEntity?>(null) }
    var planSavedAsTask by remember(type, dateKey, effectiveContent) { mutableStateOf(false) }
    var showTaskPreview by remember(type, dateKey, effectiveContent) { mutableStateOf(false) }
    var selectedTaskKeys by remember(type, dateKey, effectiveContent) { mutableStateOf(emptySet<String>()) }
    var savingTasks by remember { mutableStateOf(false) }
    var taskSaveError by remember { mutableStateOf<String?>(null) }

    val savePlanAsTask = {
        val selected = planItems.filter { it.key in selectedTaskKeys }
        val taskDate = dateKey
        val dueDate = selectedDate.atTime(23, 59)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        savingTasks = true
        taskSaveError = null
        scope.launch {
            try {
                repo.savePlanTasks(selected.map { item ->
                    com.workhub.app.data.TaskEntity(
                        title = item.text,
                        detail = listOf("来源：${title.removePrefix("今日")} · $taskDate")
                            .plus(item.notes.map { "动作说明：$it" }).joinToString("\n"),
                        priority = 2,
                        dueDate = dueDate
                    )
                })
                planSavedAsTask = true
                showTaskPreview = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                taskSaveError = "保存失败，请重试"
            } finally {
                savingTasks = false
            }
        }
        Unit
    }

    val generate = {
        if (settings.apiKey.isBlank()) {
            error = "尚未配置文字 AI，请到“我的 → 文字 AI”填写并测试连接"
        } else {
            scope.launch {
                loading = true
                error = null
                try {
                    val content = AiClient.chat(
                        baseUrl = settings.apiBaseUrl,
                        apiKey = settings.apiKey,
                        model = settings.model,
                        messages = listOf(
                            ChatMessage(
                                "user",
                                buildPrompt(settings, topic.trim(), latestBody, previousBody) +
                                    adjustmentLine.takeIf { it.isNotBlank() }
                                        ?.let { "\n\n近期执行与恢复状态：$it。请据此调整今日强度。" }
                                        .orEmpty()
                            )
                        )
                    )
                    repo.savePlan(AiPlanEntity(type = type, date = dateKey, content = content))
                } catch (e: Exception) {
                    error = e.message ?: "生成失败，请重试"
                } finally {
                    loading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Filled.History, contentDescription = "历史记录")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (type == "diet" && !compactPlan) {
                TextButton(onClick = { navController.navigate("diet_reference") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (settings.dietPlan.enabled) "饮食方案：谭成义减脂 · 查看或调整 →" else "选择饮食方案 · 通用 / 谭成义减脂 →")
                }
            }
            if (supportLongTerm) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CommercialSectionTab(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        label = "今日安排",
                        modifier = Modifier.weight(1f)
                    )
                    CommercialSectionTab(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        label = "长期计划",
                        modifier = Modifier.weight(1f)
                    )
                    CommercialSectionTab(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        label = if (type == "fitness") "训练记录" else "执行记录",
                        modifier = Modifier.weight(1f)
                    )
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "前一天")
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = selectedDate != LocalDate.now()) {
                                    selectedDate = LocalDate.now()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                when (selectedDate) {
                                    LocalDate.now() -> "今天"
                                    LocalDate.now().minusDays(1) -> "昨天"
                                    LocalDate.now().plusDays(1) -> "明天"
                                    else -> selectedDate.format(
                                        DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
                                    )
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    isFutureDate -> "计划预览 · 执行记录暂不可用"
                                    selectedDate != LocalDate.now() -> "$dateKey · 点击返回今天"
                                    else -> dateKey
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFutureDate) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "后一天")
                        }
                    }
                }
            }
            if (selectedSection == 1 && supportLongTerm) {
                LongTermPlanContent(
                    modifier = Modifier.weight(1f),
                    repo = repo,
                    settingsRepository = settingsRepository,
                    navController = navController,
                    type = type,
                    title = title.removePrefix("今日"),
                    gradient = gradient,
                    buildPrompt = { u, goal, days, latest, prev ->
                        val ctx = buildString {
                            append(crossContext)
                            if (adjustmentLine.isNotBlank()) {
                                append(if (isNotEmpty()) "；" else "")
                                append(adjustmentLine)
                            }
                        }
                        if (type == "diet") {
                            AiPrompts.longTermDiet(u, days, goal, AiPrompts.bodyDataLine(latest, prev, u), ctx)
                        } else {
                            AiPrompts.longTermFitness(u, days, goal, AiPrompts.bodyDataLine(latest, prev, u), ctx)
                        }
                    }
                )
            } else if (selectedSection == 2 && supportLongTerm) {
                PlanHistoryContent(
                    modifier = Modifier.weight(1f),
                    type = type,
                    logs = typeLogs,
                    workoutSets = if (type == "fitness") allWorkoutSets else emptyList()
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
            val generationControls: @Composable () -> Unit = {
            if (type == "diet" && compactPlan) {
                TextButton(onClick = { navController.navigate("diet_reference") }) {
                    Text(if (settings.dietPlan.enabled) "谭成义减脂方案 · 查看或调整 →" else "选择饮食参考方案 →")
                }
            }
            if (!compactPlan) {
            // 渐变头部
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(gradient))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            "TODAY'S SESSION",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                        Text(
                            if (usingMappedView) "第 $dayOfPlan 天 · ${if (type == "fitness") "训练安排" else "饮食恢复"}"
                            else if (type == "fitness") "当日训练安排" else "当日饮食安排",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        Text(
                            dateKey,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            }
            // 个人档案摘要
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileItem("身高", settings.heightCm.ifBlank { "未填" } + " cm")
                    ProfileItem("体重", settings.weightKg.ifBlank { "未填" } + " kg")
                    ProfileItem("目标", settings.goal)
                }
            }

            contextCard?.invoke()

            if (type == "fitness" || type == "diet") {
                OutlinedButton(
                    onClick = { navController.navigate("photo_analysis?scene=${if (type == "fitness") "training" else "meal"}") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (type == "fitness") "上传照片 · 分析训练动作" else "上传照片 · 分析当餐摄入") }
            }

            if (crossContext.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "关联状态  ·  $crossContext",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (topicEnabled) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    enabled = !isFutureDate,
                    label = { Text("补充说明（可选）") },
                    placeholder = { Text(topicPlaceholder) },
                    trailingIcon = { VoiceInputButton(topic, { topic = it }, enabled = !isFutureDate && !loading, maxLength = 1500) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            }
            if (!compactPlan) generationControls()

            when {
                loading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                "AI 正在生成…通常需要 10-30 秒",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }

                error != null && plan == null -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                error!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row {
                                if (settings.apiKey.isBlank()) TextButton(onClick = { navController.navigate("media_settings?mode=text") }) { Text("配置文字 AI") }
                                TextButton(onClick = { error = null }) { Text("关闭") }
                            }
                        }
                    }
                }

                plan == null && !usingMappedView -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "还没有生成$title",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "生成长期计划后，每天会自动显示当天的安排",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )
                            Button(onClick = { generate() }, enabled = !isFutureDate) {
                                Text(if (isFutureDate) "未来日期仅支持预览" else "生成当日计划")
                            }
                        }
                    }
                }

                else -> {
                    error?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row {
                                    TextButton(onClick = { error = null }) { Text("关闭") }
                                }
                            }
                        }
                    }
                    if (type == "fitness") {
                        if (usingMappedView && longTermPlan != null) {
                            Text("跟随长期计划 · 第 $dayOfPlan / ${longTermPlan!!.durationDays} 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (dailyOverride && longTermPlan != null) {
                            TextButton(onClick = { dailyOverride = false }) { Text("独立计划 · 恢复跟随长期计划") }
                        }
                        FitnessPlanOverview(
                            content = effectiveContent,
                            items = planItems,
                            checkedMap = checkedMap,
                            canTrain = !isFutureDate,
                            onStart = { showTrainingMode = true },
                            onTasks = {
                                selectedTaskKeys = planItems.filter { checkedMap[it.key] != true }.map { it.key }.toSet()
                                taskSaveError = null
                                showTaskPreview = true
                            },
                            tasksEnabled = !savingTasks,
                            onCopy = { clipboard.setText(AnnotatedString(effectiveContent)) }
                        )
                    } else if (type == "diet") {
                        if (usingMappedView && longTermPlan != null) {
                            Text("跟随长期计划 · 第 $dayOfPlan / ${longTermPlan!!.durationDays} 天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (dailyOverride && longTermPlan != null) {
                            TextButton(onClick = { dailyOverride = false }) { Text("独立计划 · 恢复跟随长期计划") }
                        }
                        DietPlanOverview(
                            content = effectiveContent, items = planItems, checkedMap = checkedMap,
                            enabled = !isFutureDate, onToggle = toggleItem,
                            onTasks = {
                                selectedTaskKeys = planItems.filter { checkedMap[it.key] != true }.map { it.key }.toSet()
                                taskSaveError = null
                                showTaskPreview = true
                            },
                            tasksEnabled = !savingTasks,
                            onConvert = { meal -> navController.navigate("food_converter?meal=${android.net.Uri.encode(meal)}") },
                            onCopy = { clipboard.setText(AnnotatedString(effectiveContent)) }
                        )
                    } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "计划内容",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(effectiveContent))
                                }) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(" 复制")
                                }
                                if (effectiveContent.isNotBlank()) {
                                    TextButton(onClick = {
                                        selectedTaskKeys = planItems.filter { checkedMap[it.key] != true }.map { it.key }.toSet()
                                        taskSaveError = null
                                        showTaskPreview = true
                                    }, enabled = !savingTasks) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = if (planSavedAsTask) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            if (planSavedAsTask) " 查看任务清单" else " 转为任务",
                                            color = if (planSavedAsTask) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (!usingMappedView && dailyOverride && longTermPlan != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "已使用独立今日计划",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { dailyOverride = false }) {
                                            Text("恢复跟随长期计划")
                                        }
                                    }
                                }
                            }
                            if (usingMappedView && longTermPlan != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "正在跟随长期计划",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                "第 $dayOfPlan / ${longTermPlan!!.durationDays} 天",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { dayOfPlan.toFloat() / longTermPlan!!.durationDays },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                            Markdown.Render(effectiveContent)
                        }
                    }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (usingMappedView) {
                            OutlinedButton(
                                onClick = { selectedSection = 1 },
                                modifier = Modifier.weight(1f)
                            ) { Text("查看完整周期", maxLines = 1) }
                            OutlinedButton(
                                onClick = {
                                    dailyOverride = true
                                    generate()
                                },
                                enabled = !isFutureDate,
                                modifier = Modifier.weight(1f)
                            ) { Text("生成独立计划", maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { generate() },
                                enabled = !isFutureDate,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" 重新生成", modifier = Modifier.padding(start = 2.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    topic = ""
                                    plan?.let { viewing = it }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("查看原文")
                            }
                        }
                    }

                    if (type != "fitness") {
                        if (type != "diet") PlanCheckCard(
                            items = planItems,
                            checkedMap = checkedMap,
                            onToggle = toggleItem,
                            enabled = !isFutureDate
                        )
                        val executionLog: @Composable () -> Unit = { PlanLogCard(
                            type = type,
                            dateLabel = if (selectedDate == LocalDate.now()) "今日" else dateKey,
                            enabled = !isFutureDate,
                            logForm = logForm,
                            logSaved = logSaved,
                            adviceLoading = adviceLoading,
                            adviceText = adviceText,
                            onLogChange = { logForm = it },
                            onSave = { saveLog() },
                            onAdvice = { generateAdvice() }
                        ) }
                        if (type == "diet") PlanDisclosure("记录实际饮食与反馈", dateKey) { executionLog() }
                        else executionLog()
                    }
                }
            }
            if (compactPlan) {
                PlanDisclosure("调整本次计划", dateKey) { generationControls() }
            }
            Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showTaskPreview) {
        AlertDialog(
            onDismissRequest = { if (!savingTasks) showTaskPreview = false },
            title = { Text("选择要添加的任务") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("仅添加勾选的执行项，同一天已添加的相同任务会自动跳过。")
                    if (planItems.isEmpty()) {
                        Text("未识别到明确的执行项。请补充具体动作与组次，或餐食与份量后再试。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    planItems.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.key in selectedTaskKeys, enabled = !savingTasks,
                                onCheckedChange = { checked ->
                                    selectedTaskKeys = if (checked) selectedTaskKeys + item.key else selectedTaskKeys - item.key
                                })
                            Text(item.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    taskSaveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { savePlanAsTask() }, enabled = !savingTasks && selectedTaskKeys.isNotEmpty()) {
                    Text(if (savingTasks) "正在添加…" else "添加 ${selectedTaskKeys.size} 项任务")
                }
            },
            dismissButton = { TextButton(onClick = { showTaskPreview = false }, enabled = !savingTasks) { Text("取消") } }
        )
    }

    if (showTrainingMode && type == "fitness") {
        TrainingModeDialog(
            dateLabel = if (selectedDate == LocalDate.now()) "今天" else dateKey,
            items = planItems,
            checkedMap = checkedMap,
            sets = workoutSets,
            exercise = setExercise,
            weight = setWeight,
            reps = setReps,
            metricType = setMetricType,
            rpe = setRpe,
            side = setSide,
            logForm = logForm,
            logSaved = logSaved,
            adviceLoading = adviceLoading,
            adviceText = adviceText,
            onDismiss = { showTrainingMode = false },
            onToggle = toggleItem,
            onEdit = { item, newText ->
                scope.launch { repo.renamePlanCheck(type, dateKey, item.key, newText) }
            },
            onExerciseChange = { setExercise = it },
            onWeightChange = { setWeight = it },
            onRepsChange = { setReps = it },
            onMetricTypeChange = {
                setMetricType = it
                setReps = ""
            },
            onRpeChange = { setRpe = it },
            onSideChange = { setSide = it },
            onAddSet = {
                scope.launch {
                    repo.addWorkoutSet(
                        com.workhub.app.data.WorkoutSetEntity(
                            date = dateKey,
                            exercise = setExercise.trim(),
                            weightKg = setWeight.trim().toDoubleOrNull(),
                            reps = setReps.trim().toIntOrNull().takeIf { setMetricType == "reps" },
                            metricType = setMetricType,
                            durationSeconds = setReps.trim().toIntOrNull().takeIf { setMetricType == "duration" },
                            distanceMeters = setReps.trim().toDoubleOrNull().takeIf { setMetricType == "distance" },
                            heightCm = setReps.trim().toDoubleOrNull().takeIf { setMetricType == "height" },
                            rpe = setRpe.trim().toIntOrNull()?.coerceIn(1, 10),
                            side = setSide
                        )
                    )
                    setExercise = ""
                    setWeight = ""
                    setReps = ""
                    setRpe = ""
                }
            },
            onDeleteSet = { set -> scope.launch { repo.deleteWorkoutSet(set) } },
            onLogChange = { logForm = it },
            onSave = { saveLog() },
            onAdvice = { generateAdvice() }
        )
    }

    // 历史记录对话框
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("历史记录") },
            text = {
                if (history.isEmpty()) {
                    Text("还没有历史记录，先生成一份吧")
                } else {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(history.size) { i ->
                            val p = history[i]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                onClick = {
                                    showHistory = false
                                    viewing = p
                                }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(p.date, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        Markdown.preview(p.content),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("关闭") }
            }
        )
    }

    // 查看历史计划内容
    viewing?.let { p ->
        Dialog(
            onDismissRequest = { viewing = null },
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
                        "${p.date} 的$title",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                    )
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Markdown.Render(p.content)
                        Spacer(Modifier.height(24.dp))
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(p.content))
                        }) { Text("复制") }
                        TextButton(onClick = { viewing = null }) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingModeDialog(
    dateLabel: String,
    items: List<PlanCheckItem>,
    checkedMap: Map<String, Boolean>,
    sets: List<com.workhub.app.data.WorkoutSetEntity>,
    exercise: String,
    weight: String,
    reps: String,
    metricType: String,
    rpe: String,
    side: String,
    logForm: LogFormState,
    logSaved: Boolean,
    adviceLoading: Boolean,
    adviceText: String?,
    onDismiss: () -> Unit,
    onToggle: (PlanCheckItem, Boolean) -> Unit,
    onEdit: (PlanCheckItem, String) -> Unit,
    onExerciseChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onMetricTypeChange: (String) -> Unit,
    onRpeChange: (String) -> Unit,
    onSideChange: (String) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (com.workhub.app.data.WorkoutSetEntity) -> Unit,
    onLogChange: (LogFormState) -> Unit,
    onSave: () -> Unit,
    onAdvice: () -> Unit
) {
    val completedCount = items.count { checkedMap[it.key] == true }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ArenaBlack)
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "退出训练模式", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$dateLabel · 训练模式",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "$completedCount / ${items.size} 个动作完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                    Text(
                        "专注执行",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                LinearProgressIndicator(
                    progress = { if (items.isEmpty()) 0f else completedCount.toFloat() / items.size },
                    modifier = Modifier.fillMaxWidth().height(5.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlanCheckCard(
                        items = items,
                        checkedMap = checkedMap,
                        onToggle = onToggle,
                        enabled = true,
                        onEdit = onEdit
                    )
                    WorkoutLogCard(
                        planItems = items,
                        sets = sets,
                        exercise = exercise,
                        weight = weight,
                        reps = reps,
                        metricType = metricType,
                        rpe = rpe,
                        side = side,
                        enabled = true,
                        onExerciseChange = onExerciseChange,
                        onWeightChange = onWeightChange,
                        onRepsChange = onRepsChange,
                        onMetricTypeChange = onMetricTypeChange,
                        onRpeChange = onRpeChange,
                        onSideChange = onSideChange,
                        onAdd = onAddSet,
                        onDelete = onDeleteSet
                    )
                    PlanLogCard(
                        type = "fitness",
                        dateLabel = dateLabel,
                        enabled = true,
                        logForm = logForm,
                        logSaved = logSaved,
                        adviceLoading = adviceLoading,
                        adviceText = adviceText,
                        onLogChange = onLogChange,
                        onSave = onSave,
                        onAdvice = onAdvice
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PlanHistoryContent(
    modifier: Modifier = Modifier,
    type: String,
    logs: List<com.workhub.app.data.PlanLogEntity>,
    workoutSets: List<com.workhub.app.data.WorkoutSetEntity>
) {
    if (logs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📒", style = MaterialTheme.typography.displayMedium)
                Text(
                    if (type == "fitness") "还没有训练记录" else "还没有饮食记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "完成当天计划后保存打卡，记录会自动保留在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        return
    }

    val executed = logs.count {
        it.status == 1 || it.status == 2 || it.durationMinutes != null || it.detail.isNotBlank()
    }
    val totalMinutes = logs.sumOf { it.durationMinutes ?: 0 }
    val totalVolume = workoutSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ProfileItem("累计记录", "${logs.size} 天")
                    ProfileItem("执行天数", "$executed 天")
                    ProfileItem(
                        "执行率",
                        "${if (logs.isEmpty()) 0 else executed * 100 / logs.size}%"
                    )
                }
            }
        }
        if (type == "fitness") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ProfileItem("累计时长", "$totalMinutes 分钟")
                        ProfileItem("累计组数", "${workoutSets.size} 组")
                        ProfileItem("累计容量", "${String.format("%.0f", totalVolume)} kg")
                    }
                }
            }
        }
        items(logs.size) { index ->
            val log = logs[index]
            val dateSets = workoutSets.filter { it.date == log.date }
            val hasExecution = log.durationMinutes != null || log.detail.isNotBlank() || dateSets.isNotEmpty()
            val displayStatus = if (log.status == 0 && hasExecution) "已记录" else logStatusText(log.status)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            log.date,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            displayStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = when (log.status) {
                                1 -> MaterialTheme.colorScheme.primary
                                2 -> MaterialTheme.colorScheme.tertiary
                                3 -> MaterialTheme.colorScheme.error
                                else -> if (hasExecution) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (type == "fitness") {
                        val feeling = when (log.feeling) {
                            1 -> "轻松"
                            2 -> "正好"
                            3 -> "很累"
                            4 -> "力竭"
                            else -> "未记录"
                        }
                        Text(
                            "训练时长：${log.durationMinutes?.let { "$it 分钟" } ?: "未记录"} · 感受：$feeling",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val meals = listOf(log.breakfast, log.lunch, log.dinner, log.snack)
                            .count { it.isNotBlank() }
                        Text(
                            "已记录 $meals 餐 · 饮水 ${log.waterCups?.let { "$it 杯" } ?: "未记录"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (log.detail.isNotBlank()) {
                        Text(log.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (dateSets.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            "逐组记录（${dateSets.size} 组）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        dateSets.forEach { set ->
                            Text(
                                "• ${set.exercise} · ${workoutMetricText(set)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (log.advice.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            "当日生成的明日建议",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            Markdown.preview(log.advice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommercialSectionTab(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanLogCard(
    type: String,
    dateLabel: String,
    enabled: Boolean,
    logForm: LogFormState,
    logSaved: Boolean,
    adviceLoading: Boolean,
    adviceText: String?,
    onLogChange: (LogFormState) -> Unit,
    onSave: () -> Unit,
    onAdvice: () -> Unit
) {
    var readinessExpanded by rememberSaveable(type, dateLabel) { mutableStateOf(false) }
    val readinessCompleted = listOf(
        logForm.sleepQuality,
        logForm.energyLevel,
        logForm.sorenessLevel,
        logForm.painLevel
    ).all { it > 0 }
    val readinessMessage = when {
        logForm.painLevel >= 3 -> "建议停止高冲击训练，优先恢复并评估疼痛原因"
        logForm.sorenessLevel >= 4 || logForm.energyLevel in 1..2 || logForm.sleepQuality in 1..2 ->
            "今日建议减量 20–40%，取消极限摸高与高疲劳组"
        readinessCompleted -> "当前状态可训练，以动作质量作为停止条件"
        else -> "完成 4 项快速评估，获得今日强度建议"
    }
    val readinessColor = when {
        logForm.painLevel >= 3 -> MaterialTheme.colorScheme.error
        logForm.sorenessLevel >= 4 || logForm.energyLevel in 1..2 || logForm.sleepQuality in 1..2 ->
            MaterialTheme.colorScheme.primary
        readinessCompleted -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${dateLabel}执行记录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (logSaved) {
                    Text(
                        "✓ 已保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "已完成", 2 to "部分完成", 3 to "未执行").forEach { (v, label) ->
                    CommercialChoice(
                        selected = logForm.status == v,
                        enabled = enabled,
                        onClick = { onLogChange(logForm.copy(status = v)) },
                        label = label,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (type == "fitness") {
                Surface(
                    onClick = { readinessExpanded = !readinessExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 38.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(readinessColor)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "训练准备",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (readinessCompleted) "  已评估" else "  待评估",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = readinessColor
                                )
                            }
                            Text(
                                readinessMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Icon(
                            if (readinessExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (readinessExpanded) "收起训练准备" else "展开训练准备",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (readinessExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "1 表示状态较差或症状较轻，5 表示状态最佳或症状最重。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ReadinessScale("睡眠", logForm.sleepQuality, enabled) {
                            onLogChange(logForm.copy(sleepQuality = it))
                        }
                        ReadinessScale("精力", logForm.energyLevel, enabled) {
                            onLogChange(logForm.copy(energyLevel = it))
                        }
                        ReadinessScale("酸痛", logForm.sorenessLevel, enabled) {
                            onLogChange(logForm.copy(sorenessLevel = it))
                        }
                        ReadinessScale("疼痛", logForm.painLevel, enabled) {
                            onLogChange(logForm.copy(painLevel = it))
                        }
                        if (logForm.painLevel > 0) {
                            OutlinedTextField(
                                value = logForm.painArea,
                                onValueChange = { onLogChange(logForm.copy(painArea = it)) },
                                label = { Text("疼痛部位") },
                                placeholder = { Text("如：左膝、右脚踝、下背") },
                                enabled = enabled,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                // 训练记录：时长 + 感受
                OutlinedTextField(
                    value = logForm.duration,
                    onValueChange = { onLogChange(logForm.copy(duration = it)) },
                    label = { Text("训练时长（分钟）") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("训练感受", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "轻松", 2 to "正好", 3 to "很累", 4 to "力竭").forEach { (v, label) ->
                        CommercialChoice(
                            selected = logForm.feeling == v,
                            enabled = enabled,
                            onClick = { onLogChange(logForm.copy(feeling = v)) },
                            label = label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = logForm.detail,
                    onValueChange = { onLogChange(logForm.copy(detail = it)) },
                    label = { Text("实际完成情况") },
                    placeholder = { Text("如：深蹲4组×8完成 85kg，跳箱5×5，摸高6次") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    minLines = 3
                )
            } else {
                // 饮食记录：逐餐 + 喝水
                Text("实际吃了什么", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealField(logForm.breakfast, { onLogChange(logForm.copy(breakfast = it)) }, "早餐", enabled, Modifier.weight(1f))
                    MealField(logForm.lunch, { onLogChange(logForm.copy(lunch = it)) }, "午餐", enabled, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealField(logForm.dinner, { onLogChange(logForm.copy(dinner = it)) }, "晚餐", enabled, Modifier.weight(1f))
                    MealField(logForm.snack, { onLogChange(logForm.copy(snack = it)) }, "加餐", enabled, Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = logForm.water,
                    onValueChange = { onLogChange(logForm.copy(water = it)) },
                    label = { Text("喝水（杯，约 250ml/杯）") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = logForm.detail,
                    onValueChange = { onLogChange(logForm.copy(detail = it)) },
                    label = { Text("补充说明（可选）") },
                    placeholder = { Text("如：中午聚餐吃了火锅，碳水偏多") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    enabled = enabled
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("保存打卡")
                }
                OutlinedButton(
                    onClick = onAdvice,
                    enabled = enabled && !adviceLoading,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (adviceLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("AI 明日建议")
                    }
                }
            }
            adviceText?.takeIf { it.isNotBlank() }?.let { advice ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "AI 明日建议",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Markdown.Render(
                            advice,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MealField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = { VoiceInputButton(value, onValueChange, enabled = enabled, maxLength = 1500) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier
    )
}

@Composable
private fun CommercialChoice(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReadinessScale(
    label: String,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.72f)
        )
        (1..5).forEach { level ->
            CommercialChoice(
                selected = value == level,
                onClick = { onChange(level) },
                enabled = enabled,
                label = level.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}


/** 汇总另一类计划的今日安排与执行情况 */
private fun buildCrossInfo(
    otherName: String,
    otherContent: String?,
    otherStatus: Int?,
    otherDetail: String?
): String = buildString {
    if (otherContent != null) {
        val firstLine = otherContent.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.let(::stripMarkdown)
            ?.take(36)
            ?: ""
        append("今日${otherName}安排：$firstLine")
    }
    if (otherStatus != null && otherStatus > 0) {
        append(if (isNotEmpty()) "；" else "")
        append("${otherName}执行：${logStatusText(otherStatus)}")
        if (!otherDetail.isNullOrBlank()) append("（${otherDetail.take(30)}）")
    }
}

internal fun logStatusText(status: Int): String = when (status) {
    1 -> "已完成"
    2 -> "部分完成"
    3 -> "未执行"
    else -> "未记录"
}

// ---------- 计划逐项勾选 ----------

private fun stripMarkdown(text: String): String = Markdown.plainText(text)

private fun workoutExerciseName(text: String): String = stripMarkdown(text)
    .substringBefore('|')
    .substringBefore('｜')
    .trim()

private fun workoutMetricText(set: com.workhub.app.data.WorkoutSetEntity): String = buildString {
    set.weightKg?.let { append(String.format("%.1f", it).removeSuffix(".0")); append("kg") }
    val metric = when (set.metricType) {
        "duration" -> set.durationSeconds?.let { "${it}秒" }
        "distance" -> set.distanceMeters?.let { "${String.format("%.1f", it).removeSuffix(".0")}m" }
        "height" -> set.heightCm?.let { "${String.format("%.1f", it).removeSuffix(".0")}cm" }
        else -> set.reps?.let { "${it}次" }
    }
    metric?.let {
        if (isNotEmpty()) append(" × ")
        append(it)
    }
    if (isEmpty()) append("徒手")
    if (set.side.isNotBlank()) append(" · ").append(
        when (set.side) { "left" -> "左侧"; "right" -> "右侧"; else -> "双侧" }
    )
    set.rpe?.let { append(" · RPE ").append(it) }
}

@Composable
private fun PlanCheckCard(
    items: List<PlanCheckItem>,
    checkedMap: Map<String, Boolean>,
    onToggle: (PlanCheckItem, Boolean) -> Unit,
    enabled: Boolean = true,
    onEdit: ((PlanCheckItem, String) -> Unit)? = null
) {
    if (items.isEmpty()) return
    val done = items.count { checkedMap[it.key] == true }
    var editingItem by remember { mutableStateOf<PlanCheckItem?>(null) }
    var editingText by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (onEdit != null) "动作清单" else "计划逐项完成",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (onEdit == null) Text(
                    "$done / ${items.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (onEdit == null) LinearProgressIndicator(
                progress = { if (items.isEmpty()) 0f else done.toFloat() / items.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            if (onEdit != null) items.forEachIndexed { index, item ->
                FitnessExerciseCard(
                    number = index + 1, item = item, done = checkedMap[item.key] == true,
                    onToggle = { onToggle(item, it) }, enabled = enabled, framed = false,
                    onEdit = { editingItem = item; editingText = stripMarkdown(item.text) }
                )
                if (index < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            } else items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clickable(enabled = enabled) { onToggle(item, checkedMap[item.key] != true) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checkedMap[item.key] == true,
                        enabled = enabled,
                        onCheckedChange = { onToggle(item, it) },
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        stripMarkdown(item.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (checkedMap[item.key] == true) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        } else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (checkedMap[item.key] == true) TextDecoration.LineThrough
                        else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                    if (onEdit != null) {
                        IconButton(
                            onClick = {
                                editingItem = item
                                editingText = stripMarkdown(item.text)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "修改训练动作",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                if (item.notes.isNotEmpty()) {
                    Text(
                        "动作说明 · ${item.notes.joinToString("；")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 36.dp, end = 40.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
    if (editingItem != null && onEdit != null) {
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("修改今日训练动作") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    label = { Text("动作与组次说明") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editingText.isNotBlank(),
                    onClick = {
                        editingItem?.let { onEdit(it, editingText.trim()) }
                        editingItem = null
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) { Text("取消") }
            }
        )
    }
}


// ---------- 训练过程记录 ----------

@Composable
private fun WorkoutLogCard(
    planItems: List<PlanCheckItem>,
    sets: List<com.workhub.app.data.WorkoutSetEntity>,
    exercise: String,
    weight: String,
    reps: String,
    metricType: String,
    rpe: String,
    side: String,
    enabled: Boolean,
    onExerciseChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onMetricTypeChange: (String) -> Unit,
    onRpeChange: (String) -> Unit,
    onSideChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (com.workhub.app.data.WorkoutSetEntity) -> Unit
) {
    val context = LocalContext.current
    val volume = sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
    val totalReps = sets.sumOf { it.reps ?: 0 }
    val totalDuration = sets.sumOf { it.durationSeconds ?: 0 }
    val totalDistance = sets.sumOf { it.distanceMeters ?: 0.0 }
    val weightedSets = sets.count { it.weightKg != null }
    var restPreset by rememberSaveable { mutableStateOf(90) }
    var restRemaining by rememberSaveable { mutableStateOf(0) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }
    var restEndAt by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(timerRunning, restEndAt) {
        while (timerRunning) {
            restRemaining = (((restEndAt - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1_000L).toInt()
            if (restRemaining <= 0) {
                timerRunning = false
                com.workhub.app.ui.pomodoro.notifyRestComplete(context)
                break
            }
            delay(250)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "训练过程记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                buildString {
                    append("已记录 ${sets.size} 组 · 总次数 $totalReps")
                    if (totalDuration > 0) append(" · ${totalDuration}秒")
                    if (totalDistance > 0) append(" · ${String.format("%.1f", totalDistance)}m")
                    if (weightedSets > 0) append(" · 负重容量 ${String.format("%.0f", volume)} kg")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("组间休息", style = MaterialTheme.typography.labelMedium)
                            Text(
                                String.format("%02d:%02d", restRemaining / 60, restRemaining % 60),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = if (timerRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                if (restRemaining == 0) restRemaining = restPreset
                                if (timerRunning) {
                                    timerRunning = false
                                } else {
                                    restEndAt = System.currentTimeMillis() + restRemaining * 1_000L
                                    timerRunning = true
                                }
                            },
                            enabled = enabled
                        ) {
                            Icon(
                                if (timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (timerRunning) "暂停休息计时" else "开始休息计时"
                            )
                        }
                        IconButton(
                            onClick = {
                                timerRunning = false
                                restRemaining = restPreset
                                restEndAt = 0L
                            },
                            enabled = enabled
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = "重置休息计时")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60, 90, 120, 180).forEach { seconds ->
                            FilterChip(
                                selected = restPreset == seconds,
                                onClick = {
                                    restPreset = seconds
                                    restRemaining = seconds
                                    timerRunning = false
                                    restEndAt = 0L
                                },
                                enabled = enabled,
                                label = {
                                    Text(
                                        if (seconds < 120) "${seconds}s" else "${seconds / 60}min",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        "记录一组后自动开始计时，保持动作质量再进入下一组",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("记录指标", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "reps" to "次数",
                    "duration" to "时长",
                    "distance" to "距离",
                    "height" to "高度"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = metricType == value,
                        onClick = { onMetricTypeChange(value) },
                        enabled = enabled,
                        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 从计划要点快速选动作
            if (planItems.isNotEmpty()) {
                Text(
                    "从今日计划快速选择",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                planItems.chunked(2).forEach { items ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items.forEach { item ->
                            FilterChip(
                                selected = exercise == workoutExerciseName(item.text),
                                enabled = enabled,
                                onClick = { onExerciseChange(workoutExerciseName(item.text)) },
                                label = {
                                    Text(
                                        workoutExerciseName(item.text).take(12),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (items.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            OutlinedTextField(
                value = exercise,
                onValueChange = onExerciseChange,
                label = { Text("动作名称") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = onWeightChange,
                    label = { Text("重量 kg（可选）") },
                    placeholder = { Text("摸高等动作留空") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = onRepsChange,
                    label = {
                        Text(
                            when (metricType) {
                                "duration" -> "时长（秒）"
                                "distance" -> "距离（米）"
                                "height" -> "高度（厘米）"
                                else -> "次数 / 跳数"
                            }
                        )
                    },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (metricType == "distance" || metricType == "height") {
                            KeyboardType.Decimal
                        } else KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = rpe,
                onValueChange = { onRpeChange(it.filter(Char::isDigit).take(2)) },
                label = { Text("主观强度 RPE 1-10（可选）") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("" to "不限", "left" to "左", "right" to "右", "both" to "双").forEach { (value, label) ->
                    FilterChip(
                        selected = side == value,
                        onClick = { onSideChange(value) },
                        enabled = enabled,
                        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Button(
                onClick = {
                    onAdd()
                    restRemaining = restPreset
                    restEndAt = System.currentTimeMillis() + restPreset * 1_000L
                    timerRunning = true
                },
                enabled = enabled && exercise.isNotBlank() && (weight.isNotBlank() || reps.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "记录一组",
                    modifier = Modifier.size(20.dp)
                )
                Text("记录这一组", modifier = Modifier.padding(start = 6.dp))
            }

            if (sets.isEmpty()) {
                Text(
                    "训练时每完成一组，记录动作、重量和次数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            sets.forEach { set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        set.exercise,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        workoutMetricText(set),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { onDelete(set) }, enabled = enabled) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
