package com.workhub.app.ui.calendar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.EventEntity
import com.workhub.app.ui.EmptyHint
import com.workhub.app.ui.Format
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(repo: AppRepository, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val events by repo.allEvents.collectAsStateWithLifecycle(initialValue = emptyList())

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<EventEntity?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    fun eventsOn(date: LocalDate): List<EventEntity> {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        return events.filter { it.startTime in start..end }
    }

    val newEvent = {
        EventEntity(
            title = "",
            startTime = selected.atTime(9, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            remindMinutes = 15
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("日程") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { editing = newEvent() }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建日程")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${month.year}年${month.monthValue}月",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    month = YearMonth.now()
                    selected = LocalDate.now()
                }) { Text("今天") }
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                }
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                    Text(
                        w,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val days = buildList<LocalDate?> {
                val first = month.atDay(1)
                repeat((first.dayOfWeek.value - 1) % 7) { add(null) }
                repeat(month.lengthOfMonth()) { add(month.atDay(it + 1)) }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(270.dp)
            ) {
                items(days.size) { i ->
                    val d = days[i]
                    if (d == null) {
                        Box(Modifier.height(44.dp))
                    } else {
                        DayCell(
                            date = d,
                            isToday = d == LocalDate.now(),
                            isSelected = d == selected,
                            hasEvents = eventsOn(d).isNotEmpty(),
                            onClick = { selected = d }
                        )
                    }
                }
            }

            Text(
                "${selected.monthValue}月${selected.dayOfMonth}日 日程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val dayEvents = eventsOn(selected)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dayEvents.isEmpty()) {
                    item { EmptyHint("这一天没有日程，点右上角 + 新建", emoji = "📅") }
                }
                items(dayEvents, key = { it.id }) { e ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { editing = e }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(e.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "时间：${Format.dateTimeText(e.startTime)}" +
                                        if (e.remindMinutes > 0) " · 提前${e.remindMinutes}分钟提醒" else " · 不提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { e ->
        EventEditDialog(
            initial = e,
            onDismiss = { editing = null },
            onSave = { ev ->
                scope.launch {
                    if (ev.id > 0) repo.updateEvent(ev) else repo.saveEvent(ev)
                }
                editing = null
            },
            onDelete = {
                if (e.id > 0) scope.launch { repo.deleteEvent(e) }
                editing = null
            }
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .padding(2.dp)
            .height(42.dp)
            .background(background, CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Box(
            Modifier
                .size(4.dp)
                .background(
                    if (hasEvents) MaterialTheme.colorScheme.tertiary else androidx.compose.ui.graphics.Color.Transparent,
                    CircleShape
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditDialog(
    initial: EventEntity,
    onDismiss: () -> Unit,
    onSave: (EventEntity) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var date by remember {
        mutableStateOf(Format.localDate(initial.startTime))
    }
    var time by remember {
        mutableStateOf(Format.localDateTime(initial.startTime).toLocalTime())
    }
    var remind by remember { mutableStateOf(initial.remindMinutes) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id > 0) "编辑日程" else "新建日程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("日程标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text("${date.monthValue}月${date.dayOfMonth}日")
                    }
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(String.format("%02d:%02d", time.hour, time.minute))
                    }
                }
                Text("提醒", style = MaterialTheme.typography.titleSmall)
                listOf(
                    listOf(0 to "不提醒", 5 to "5分钟"),
                    listOf(15 to "15分钟", 30 to "30分钟"),
                    listOf(60 to "1小时")
                ).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (min, label) ->
                            FilterChip(
                                selected = remind == min,
                                onClick = { remind = min },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (options.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = title.trim()
                if (t.isEmpty()) {
                    Toast.makeText(context, "请填写日程标题", Toast.LENGTH_SHORT).show()
                } else {
                    onSave(
                        initial.copy(
                            title = t,
                            startTime = date.atTime(time)
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            remindMinutes = remind
                        )
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial.id > 0) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timeState)
                }
            }
        )
    }
}
