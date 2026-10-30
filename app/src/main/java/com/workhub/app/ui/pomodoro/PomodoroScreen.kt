package com.workhub.app.ui.pomodoro

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.PomodoroEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COUNT_UP = 0
private const val COUNT_DOWN = 1
private val COUNTDOWN_PRESETS = listOf(5, 15, 25, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(repo: AppRepository, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pomodoros by repo.allPomodoros.collectAsStateWithLifecycle(initialValue = emptyList())
    val persisted = remember { FocusTimerStore.load(context) }

    var timerType by rememberSaveable { mutableIntStateOf(persisted.type) }
    var selectedMinutes by rememberSaveable { mutableIntStateOf(persisted.selectedMinutes) }
    var customMinutes by rememberSaveable { mutableStateOf("") }
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(persisted.baseElapsedSeconds) }
    var remainingSeconds by rememberSaveable { mutableIntStateOf(persisted.pausedRemainingSeconds) }
    var running by rememberSaveable { mutableStateOf(persisted.running) }
    var startedAt by rememberSaveable { mutableStateOf(persisted.startedAt) }
    var endAt by rememberSaveable { mutableStateOf(persisted.endAt) }
    var baseElapsedAtStart by rememberSaveable { mutableIntStateOf(persisted.baseElapsedSeconds) }
    var completedAndSaved by rememberSaveable { mutableStateOf(persisted.completed) }

    val totalSeconds = selectedMinutes * 60
    val recordedSeconds = if (timerType == COUNT_UP) elapsedSeconds
    else (totalSeconds - remainingSeconds).coerceAtLeast(0)

    fun resetTimer() {
        val state = FocusTimerStore.reset(context, timerType, selectedMinutes)
        running = state.running
        startedAt = state.startedAt
        endAt = state.endAt
        completedAndSaved = state.completed
        elapsedSeconds = 0
        remainingSeconds = selectedMinutes * 60
    }

    fun switchTimer(type: Int) {
        running = false
        completedAndSaved = false
        timerType = type
        FocusTimerStore.reset(context, type, selectedMinutes)
        elapsedSeconds = 0
        remainingSeconds = selectedMinutes * 60
    }

    fun chooseMinutes(minutes: Int) {
        running = false
        completedAndSaved = false
        selectedMinutes = minutes
        FocusTimerStore.reset(context, timerType, minutes)
        customMinutes = ""
        elapsedSeconds = 0
        remainingSeconds = minutes * 60
    }

    fun saveSession(seconds: Int) {
        if (seconds < 1) return
        running = false
        scope.launch {
            val minutes = ((seconds + 59) / 60).coerceAtLeast(1)
            repo.savePomodoro(PomodoroEntity(durationMinutes = minutes))
            Toast.makeText(context, "已记录 $minutes 分钟专注", Toast.LENGTH_SHORT).show()
            resetTimer()
        }
    }

    fun toggleRunning() {
        if (running) {
            val state = FocusTimerStore.savePausedProgress(
                context = context,
                type = timerType,
                selectedMinutes = selectedMinutes,
                elapsed = elapsedSeconds,
                remaining = remainingSeconds
            )
            FocusTimerStore.pause(context, state)
            running = false
            startedAt = 0L
            endAt = 0L
        } else {
            val state = FocusTimerStore.start(
                context = context,
                type = timerType,
                selectedMinutes = selectedMinutes,
                elapsed = elapsedSeconds,
                remaining = remainingSeconds
            )
            running = true
            startedAt = state.startedAt
            endAt = state.endAt
            baseElapsedAtStart = elapsedSeconds
            completedAndSaved = false
        }
    }

    LaunchedEffect(running, timerType, startedAt, endAt) {
        if (!running) return@LaunchedEffect
        while (running) {
            if (timerType == COUNT_UP) {
                elapsedSeconds = baseElapsedAtStart +
                    ((System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 1_000L).toInt()
            } else {
                remainingSeconds = (((endAt - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1_000L).toInt()
                if (remainingSeconds <= 0) {
                    val completedMinutes = FocusTimerStore.claimCompletion(context, endAt)
                    if (completedMinutes != null) {
                        repo.savePomodoro(
                            PomodoroEntity(durationMinutes = completedMinutes, completedAt = endAt)
                        )
                        notifyFocusComplete(context, completedMinutes)
                    }
                    running = false
                    completedAndSaved = true
                    startedAt = 0L
                    endAt = 0L
                    break
                }
            }
            delay(250)
        }
    }

    val today = LocalDate.now()
    val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val todaySessions = pomodoros.filter { it.completedAt in todayStart until todayEnd }
    val todayMinutes = todaySessions.sumOf { it.durationMinutes }

    val shownSeconds = if (timerType == COUNT_UP) elapsedSeconds else remainingSeconds
    val progress = if (timerType == COUNT_UP) {
        (elapsedSeconds % 3600) / 3600f
    } else if (totalSeconds > 0) {
        remainingSeconds.toFloat() / totalSeconds
    } else 0f
    val activeColor = if (timerType == COUNT_UP) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("专注计时器") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = timerType == COUNT_UP,
                    onClick = { switchTimer(COUNT_UP) },
                    label = { Text("正计时", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f).height(44.dp)
                )
                FilterChip(
                    selected = timerType == COUNT_DOWN,
                    onClick = { switchTimer(COUNT_DOWN) },
                    label = { Text("倒计时", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f).height(44.dp)
                )
            }

            if (timerType == COUNT_DOWN) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    COUNTDOWN_PRESETS.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { minutes ->
                                FilterChip(
                                    selected = selectedMinutes == minutes && customMinutes.isBlank(),
                                    onClick = { chooseMinutes(minutes) },
                                    label = {
                                        Text(
                                            "$minutes 分钟",
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { value ->
                            val digits = value.filter(Char::isDigit).take(3)
                            customMinutes = digits
                            val minutes = digits.toIntOrNull()
                            if (minutes != null && minutes in 1..999) {
                                running = false
                                completedAndSaved = false
                                selectedMinutes = minutes
                                elapsedSeconds = 0
                                remainingSeconds = minutes * 60
                                FocusTimerStore.reset(context, timerType, minutes)
                            } else if (digits.isNotBlank()) {
                                running = false
                                remainingSeconds = 0
                            }
                        },
                        label = { Text("自定义分钟") },
                        placeholder = { Text("输入 1–999 分钟") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        "适合自由训练、拉伸、学习或不确定结束时间的任务；结束后可直接记录本次时长。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.size(220.dp)) {
                    val stroke = 14.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatTime(shownSeconds),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            running && timerType == COUNT_UP -> "正在正计时"
                            running -> "正在倒计时"
                            completedAndSaved -> "已完成并记录"
                            recordedSeconds > 0 -> "已暂停"
                            else -> "准备开始"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { toggleRunning() },
                    enabled = timerType == COUNT_UP || remainingSeconds > 0,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                    Text(if (running) "暂停" else "开始", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(
                    onClick = { resetTimer() },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("重置", modifier = Modifier.padding(start = 6.dp))
                }
            }

            if (recordedSeconds > 0 && !completedAndSaved) {
                OutlinedButton(
                    onClick = { saveSession(recordedSeconds) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("结束并记录", modifier = Modifier.padding(start = 6.dp))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimerStat("$todayMinutes", "今日专注（分）")
                    TimerStat("${todaySessions.size}", "记录次数")
                }
            }
        }
    }
}

@Composable
private fun TimerStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
