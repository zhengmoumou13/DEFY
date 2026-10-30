package com.workhub.app.ui.ball

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.BallTrainingRecordEntity
import com.workhub.app.training.BallDrill
import com.workhub.app.training.BallMoves
import com.workhub.app.training.BallPlanCodec
import com.workhub.app.training.BallPlanExercise
import com.workhub.app.training.ballPlanProgress
import com.workhub.app.training.prescription
import com.workhub.app.ui.markdown.Markdown
import com.workhub.app.ui.media.VoiceInputButton
import com.workhub.app.training.summarizeBallTraining
import com.workhub.app.training.validateBallRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BallRecordScreen(
    repo: AppRepository,
    navController: NavHostController,
    category: String,
    recordId: Long,
    movementKey: String = "",
    planId: Long = -1,
    planItemIndex: Int = -1
) {
    var initialRecord by remember(recordId, category, movementKey, planId, planItemIndex) { mutableStateOf<BallTrainingRecordEntity?>(null) }
    var planExercise by remember { mutableStateOf<BallPlanExercise?>(null) }
    var loadError by remember { mutableStateOf(false) }
    LaunchedEffect(recordId, category, movementKey, planId, planItemIndex) {
        loadError = false
        try {
            val existing = if (recordId > 0) requireNotNull(repo.getBallTrainingRecord(recordId)) else null
            val linkedPlanId = existing?.planId ?: planId.takeIf { it > 0 }
            val itemIndex = existing?.planItemId?.toIntOrNull() ?: planItemIndex
            val savedPlan = linkedPlanId?.let { repo.getPlan(it) }
            planExercise = savedPlan?.takeIf { it.type == BALL_PLAN_TYPE }?.let {
                runCatching { BallPlanCodec.decode(it.content).exercises.getOrNull(itemIndex) }.getOrNull()
            }
            if (existing != null) {
                initialRecord = existing
            } else {
                require(linkedPlanId == null || planExercise != null)
                val move = if (planExercise != null) requireNotNull(BallMoves.find(planExercise!!.movementKey))
                    else if (movementKey.isNotBlank()) requireNotNull(BallMoves.find(movementKey))
                    else BallMoves.forCategory(category).first()
                require(linkedPlanId != null || move.category == category)
                val drill = requireNotNull(BallDrill.fromKey(move.category))
                initialRecord = BallTrainingRecordEntity(
                    date = LocalDate.now().toString(),
                    category = drill.key,
                    position = planExercise?.position ?: drill.positions.first(),
                    movementKey = move.key,
                    planId = linkedPlanId,
                    planItemId = if (linkedPlanId == null) "" else itemIndex.toString()
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadError = true
        }
    }
    val record = initialRecord
    if (record == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (loadError) {
                Text("记录或计划动作不存在，或读取失败，请返回重试")
                TextButton(onClick = { navController.popBackStack() }) { Text("返回训练列表") }
            } else CircularProgressIndicator()
        }
    } else {
        BallRecordEditor(repo, record, planExercise, onBack = { navController.popBackStack() })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BallRecordEditor(repo: AppRepository, initial: BallTrainingRecordEntity, planExercise: BallPlanExercise?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drill = BallDrill.fromKey(initial.category) ?: BallDrill.MID_RANGE
    var date by rememberSaveable(initial.id) { mutableStateOf(initial.date) }
    var position by rememberSaveable(initial.id) { mutableStateOf(initial.position) }
    var attempts by rememberSaveable(initial.id) { mutableStateOf(initial.attempts.toString()) }
    var made by rememberSaveable(initial.id) { mutableStateOf(initial.made.toString()) }
    var minutes by rememberSaveable(initial.id) { mutableStateOf(initial.durationMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var seconds by rememberSaveable(initial.id) { mutableStateOf(initial.durationSeconds.takeIf { it > 0 }?.toString().orEmpty()) }
    var movementKey by rememberSaveable(initial.id) { mutableStateOf(initial.movementKey) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var previousCounts by rememberSaveable(initial.id) { mutableStateOf("") }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val records by repo.allBallTrainingRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val move = BallMoves.find(movementKey)

    val edited = initial.copy(
        date = date, position = position,
        attempts = if (drill.tracksShots) attempts.toIntOrNull() ?: -1 else 0,
        made = if (drill.tracksShots) made.toIntOrNull() ?: -1 else 0,
        durationMinutes = if (minutes.isBlank()) 0 else minutes.toIntOrNull() ?: -1,
        durationSeconds = if (seconds.isBlank()) 0 else seconds.toIntOrNull() ?: -1,
        movementKey = movementKey,
        note = note
    )
    val dirty = edited != initial
    val validationError = validateBallRecord(edited)
    val countsValid = edited.attempts in 0..9998 && edited.made in 0..edited.attempts
    val back: () -> Unit = { if (!saving) { if (dirty) showDiscard = true else onBack() } }
    BackHandler(onBack = back)

    fun countShot(hit: Boolean) {
        if (!countsValid) return
        previousCounts = "$attempts:$made"
        attempts = (edited.attempts + 1).toString()
        made = (edited.made + if (hit) 1 else 0).toString()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("${if (initial.id > 0) "编辑" else "记录"}${drill.label}") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    saveError ?: validationError ?: "数据保存在本机，可在历史中修改",
                    color = if (saveError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        saving = true
                        saveError = null
                        scope.launch {
                            try {
                                repo.saveBallTrainingRecord(edited)
                                Toast.makeText(context, "已保存${drill.label}记录", Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                saveError = "保存失败，输入已保留，请重试"
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && validationError == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(if (saving) "保存中…" else if (initial.id > 0) "保存修改" else "保存这组训练") }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BallPanel {
                Text(move?.label ?: "${drill.label} · 未标注动作", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (planExercise != null && initial.planId != null) {
                    Text("AI 计划 · 主训练 ${(initial.planItemId.toIntOrNull() ?: 0) + 1}", style = MaterialTheme.typography.labelLarge)
                    Text(planExercise.prescription(), style = MaterialTheme.typography.titleSmall)
                    Markdown.Render(planExercise.steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n"))
                    Markdown.Render("**动作要领**\n\n${planExercise.cues}")
                    Text(ballPlanProgress(initial.planId, initial.planItemId, planExercise, records, date), style = MaterialTheme.typography.bodySmall)
                    Text("下方只填本组实际完成量；保存后回到计划，可继续记录下一组。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(move?.steps ?: drill.cues, style = MaterialTheme.typography.bodyMedium)
                    if (initial.planId != null) Text("关联计划暂不可读，仍可修改本组实际数据。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BallPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("训练日期", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(enabled = !saving, onClick = { showDate = true }) { Text(date) }
                }
                if (initial.planId == null) {
                    Text("训练动作", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BallMoves.forCategory(drill.key).forEach { option ->
                            FilterChip(selected = movementKey == option.key, enabled = !saving, onClick = { movementKey = option.key }, label = { Text(option.label) })
                        }
                    }
                }
                Text(if (drill.tracksShots) "点位 / 终结方式" else "练习方式", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    drill.positions.forEach { option ->
                        FilterChip(
                            selected = option == position,
                            onClick = { position = option },
                            enabled = !saving && initial.planId == null,
                            label = { Text(option) }
                        )
                    }
                }
                if (drill.tracksShots && (edited.attempts > 0 || edited.made > 0)) {
                    Text("换点位前建议先保存这组；不同点位分别记录，统计更准确。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (drill.tracksShots) {
                BallPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("本组命中率", style = MaterialTheme.typography.labelLarge)
                        Text("${edited.made.coerceAtLeast(0)} 命中 / ${edited.attempts.coerceAtLeast(0)} 出手", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        if (edited.attempts > 0 && edited.made in 0..edited.attempts) summarizeBallTraining(listOf(edited)).accuracy else "—",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(enabled = !saving && countsValid, onClick = { countShot(true) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("命中 +1") }
                        OutlinedButton(enabled = !saving && countsValid, onClick = { countShot(false) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("未中 +1") }
                    }
                    TextButton(
                        enabled = !saving && previousCounts.isNotEmpty(),
                        onClick = {
                            val counts = previousCounts.split(':')
                            attempts = counts.first()
                            made = counts.last()
                            previousCounts = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("撤销上一球") }
                    Text("也可在训练后直接填写整组数据。命中已包含在出手数内。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CountField("出手次数", attempts, !saving, Modifier.weight(1f)) {
                            attempts = it
                            previousCounts = ""
                        }
                        CountField("命中次数", made, !saving, Modifier.weight(1f)) {
                            made = it
                            previousCounts = ""
                        }
                    }
                }
            }
            BallPanel {
                Text(if (drill.tracksShots) "实际练习时长（可选）" else "实际运控时长（不含休息）", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountField("分钟", minutes, !saving, Modifier.weight(1f)) { minutes = it }
                    CountField("秒（0–59）", seconds, !saving, Modifier.weight(1f)) { seconds = it }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 500) note = it },
                    enabled = !saving,
                    label = { Text("训练备注（可选）") },
                    placeholder = { Text(if (drill.tracksShots) "例如：接球投篮、急停跳投、今天的手感" else "例如：左右手各 3 组，弱侧失误较多") },
                    minLines = 2,
                    trailingIcon = { VoiceInputButton(note, { note = it }, enabled = !saving, maxLength = 500) },
                    supportingText = { Text("${note.length}/500") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    if (showDate) {
        val latestDate = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latestDate
                override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                    picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }
        ) { DatePicker(state = picker) }
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("这组训练还未保存") },
            text = { Text("返回会丢弃当前修改。你可以继续记录并保存。") },
            confirmButton = { TextButton(onClick = { showDiscard = false; onBack() }) { Text("丢弃修改", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("继续记录") } }
        )
    }
}

@Composable
private fun CountField(label: String, value: String, enabled: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.length <= 4 && input.all { it in '0'..'9' }) onChange(input) },
        label = { Text(label) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}
