package com.workhub.app.ui.ball

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.ai.BallPlanGenerator
import com.workhub.app.ai.BallPlanRequest
import com.workhub.app.data.AiPlanEntity
import com.workhub.app.data.AppRepository
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.training.BallDrill
import com.workhub.app.training.BallMoves
import com.workhub.app.training.BallPlanCodec
import com.workhub.app.training.ballPlanProgress
import com.workhub.app.training.prescription
import com.workhub.app.ui.markdown.Markdown
import com.workhub.app.ui.media.VoiceInputButton
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val BALL_PLAN_TYPE = "ball_skill"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallPlanScreen(repo: AppRepository, settingsRepository: SettingsRepository, navController: NavHostController, initialFocus: String) {
    val plansFlow = remember(repo) { repo.observePlansByType(BALL_PLAN_TYPE) }
    val loadedPlans by plansFlow.collectAsStateWithLifecycle(initialValue = null)
    val plans = loadedPlans.orEmpty()
    val records by repo.allBallTrainingRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    var focus by rememberSaveable { mutableStateOf(initialFocus.takeIf { BallDrill.fromKey(it) != null } ?: "balanced") }
    var level by rememberSaveable { mutableStateOf("有基础") }
    var partner by rememberSaveable { mutableStateOf("单人训练") }
    var minutes by rememberSaveable { mutableIntStateOf(45) }
    var requirements by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableLongStateOf(0) }
    var showOptions by rememberSaveable { mutableStateOf(true) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    val selected = plans.firstOrNull { it.id == selectedId } ?: plans.firstOrNull()
    val parsed = remember(selected?.content) { selected?.let { runCatching { BallPlanCodec.decode(it.content) } } }
    val plan = parsed?.getOrNull()
    val back: () -> Unit = { if (loading) showLeave = true else navController.popBackStack() }
    BackHandler(enabled = loading, onBack = back)

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("AI 有球训练计划") },
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
        )
    }) { padding ->
        if (loadedPlans == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                BallPanel {
                    Text("把运球、脚步和出手练在一起", style = MaterialTheme.typography.titleMedium)
                    Text("按你的条件生成 3–6 个主训练动作，包含热身、执行步骤、组次、休息和进阶建议。计划保存后可离线查看。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showOptions = !showOptions }, enabled = !loading) {
                        Text(if (plans.isEmpty()) "训练条件" else if (showOptions) "收起训练条件" else "调整训练条件 / 生成新计划")
                    }
                    if (showOptions || plans.isEmpty()) {
                        Text("训练方向", style = MaterialTheme.typography.titleSmall)
                        PlanOptions(listOf("balanced" to "综合有球") + BallDrill.entries.map { it.key to it.label }, focus, !loading) { focus = it }
                        Text("当前水平", style = MaterialTheme.typography.titleSmall)
                        PlanOptions(listOf("入门", "有基础", "进阶").map { it to it }, level, !loading) { level = it }
                        Text("可用时间（含热身与放松）", style = MaterialTheme.typography.titleSmall)
                        PlanOptions(listOf(30, 45, 60).map { it.toString() to "$it 分钟" }, minutes.toString(), !loading) { minutes = it.toInt() }
                        PlanOptions(listOf("单人训练", "有陪练").map { it to it }, partner, !loading) { partner = it }
                        OutlinedTextField(
                            value = requirements,
                            onValueChange = { if (it.length <= 600) requirements = it },
                            enabled = !loading,
                            label = { Text("目标、场地与身体状态") },
                            placeholder = { Text("例如：想练变向急停中投，只有半场和一个球，左脚踝有些疲劳") },
                            minLines = 3,
                            trailingIcon = { VoiceInputButton(requirements, { requirements = it }, enabled = !loading, maxLength = 600) },
                            supportingText = { Text("${requirements.length}/600") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("生成时会将档案、训练条件及近30天有球训练汇总发送给你配置的 AI 服务。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (settings.apiKey.isBlank()) {
                            OutlinedButton(onClick = { navController.navigate("settings") }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("配置 AI 服务") }
                        }
                        Button(
                            enabled = !loading && settings.apiKey.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val request = BallPlanRequest(focus, level, partner, minutes, requirements.trim())
                                val inputRecords = records.toList()
                                loading = true
                                error = null
                                scope.launch {
                                    var key = ""
                                    try {
                                        val currentSettings = settingsRepository.settings.first()
                                        key = currentSettings.apiKey
                                        val result = BallPlanGenerator.generate(currentSettings, request, inputRecords)
                                        selectedId = repo.savePlan(AiPlanEntity(type = BALL_PLAN_TYPE, date = LocalDate.now().toString(), content = BallPlanCodec.encode(result)))
                                        showOptions = false
                                        showHistory = false
                                        scroll.animateScrollToItem(0)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        val message = failure.message ?: "生成失败，请检查网络后重试"
                                        error = if (key.isNotEmpty()) message.replace(key, "[密钥已隐藏]") else message
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        ) { Text(if (loading) "正在制定计划…" else "生成并保存训练计划") }
                    }
                    if (loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在组织动作、控制训练量并校验计划，完成后自动保存。", style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (plans.isNotEmpty()) {
                item {
                    TextButton(onClick = { showHistory = !showHistory }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showHistory) "收起历史计划" else "历史计划（${plans.size}）· 点击切换")
                    }
                }
                if (showHistory) {
                    items(plans, key = { "history_${it.id}" }) { saved ->
                        val title = remember(saved.content) { runCatching { BallPlanCodec.decode(saved.content).title }.getOrDefault("有球训练计划") }
                        OutlinedButton(onClick = { selectedId = saved.id; showHistory = false }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text("${saved.date} · $title${if (saved.id == selected?.id) " · 当前查看" else ""}")
                        }
                    }
                }
            }
            if (plan != null && selected != null) {
                item {
                    BallPanel {
                        Text(plan.title, style = MaterialTheme.typography.titleLarge)
                        Text("${selected.date} · ${plan.minutes} 分钟 · ${plan.level} · ${plan.partner}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${if (plan.focus == "balanced") "综合有球" else BallDrill.fromKey(plan.focus)?.label} · ${plan.exercises.size} 个主训练 · ${plan.exercises.sumOf { it.sets * it.shotsPerSet }} 次计划出手", style = MaterialTheme.typography.bodyMedium)
                        Markdown.Render(plan.assessment)
                        Text("下方进度仅统计今天关联这份计划的已保存记录；往日训练可在有球训练的「历史」中查看。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    BallPanel {
                        Text("01 · 热身准备", style = MaterialTheme.typography.titleMedium)
                        plan.warmup.forEach { Markdown.Render("- $it") }
                    }
                }
                items(plan.exercises.withIndex().toList(), key = { "exercise_${selected.id}_${it.index}" }) { (index, exercise) ->
                    val move = requireNotNull(BallMoves.find(exercise.movementKey))
                    BallPanel {
                        Text("主训练 ${index + 1} · ${move.label}", style = MaterialTheme.typography.titleMedium)
                        Text(exercise.position, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(exercise.prescription(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                        Markdown.Render(exercise.steps.mapIndexed { stepIndex, text -> "${stepIndex + 1}. $text" }.joinToString("\n"))
                        Text("动作要领", style = MaterialTheme.typography.titleSmall)
                        Markdown.Render(exercise.cues)
                        Text("进阶与调整", style = MaterialTheme.typography.titleSmall)
                        Markdown.Render(exercise.progression)
                        Text(ballPlanProgress(selected.id, index.toString(), exercise, records), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { navController.navigate("ball_record?planId=${selected.id}&planItemIndex=$index") },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("记录这一动作") }
                    }
                }
                item {
                    BallPanel {
                        Text("03 · 放松与恢复", style = MaterialTheme.typography.titleMedium)
                        plan.cooldown.forEach { Markdown.Render("- $it") }
                        Text("停止条件", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Markdown.Render(plan.stopRules)
                    }
                }
            } else if (parsed?.isFailure == true) {
                item {
                    BallPanel {
                        Text("这份计划暂时无法完整展示", style = MaterialTheme.typography.titleMedium)
                        Text("原文仍保留在本机。请切换其他历史计划，或重新生成。")
                        OutlinedButton(onClick = { showOptions = true }, enabled = !loading) { Text("重新制定") }
                    }
                }
            }
        }
    }
    if (showLeave) {
        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text("正在生成计划") },
            text = { Text("离开后本次生成结果不会保存，已有计划不受影响。") },
            confirmButton = { TextButton(onClick = { showLeave = false; navController.popBackStack() }) { Text("离开") } },
            dismissButton = { TextButton(onClick = { showLeave = false }) { Text("继续等待") } }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlanOptions(options: List<Pair<String, String>>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            androidx.compose.material3.FilterChip(selected = key == selected, enabled = enabled, onClick = { onSelect(key) }, label = { Text(label) })
        }
    }
}
