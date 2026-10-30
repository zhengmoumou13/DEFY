package com.workhub.app.ui.ball

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.BallTrainingRecordEntity
import com.workhub.app.training.BallDrill
import com.workhub.app.training.BallMoves
import com.workhub.app.training.formatBallDuration
import com.workhub.app.training.totalDurationSeconds
import com.workhub.app.training.summarizeBallTraining
import com.workhub.app.ui.theme.ArenaBlack
import com.workhub.app.ui.theme.CourtOrange
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallTrainingScreen(repo: AppRepository, navController: NavHostController) {
    val records by repo.allBallTrainingRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberSaveable(tab, saver = LazyListState.Saver) { LazyListState() }
    var category by rememberSaveable { mutableStateOf("") }
    var rangeDays by rememberSaveable { mutableIntStateOf(30) }
    var pendingDelete by remember { mutableStateOf<BallTrainingRecordEntity?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val filtered = remember(records, category, rangeDays, today) {
        records.filter { record ->
            val date = runCatching { LocalDate.parse(record.date) }.getOrNull()
            (category.isEmpty() || category == record.category) && date != null &&
                !date.isAfter(today) && (rangeDays == 0 || !date.isBefore(today.minusDays(rangeDays - 1L)))
        }
    }
    val todayRecords = records.filter { it.date == today.toString() }
    val todaySummary = summarizeBallTraining(todayRecords)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("有球训练") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf("训练", "历史", "统计").forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index, onClick = { tab = index }, text = { Text(title) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (tab == 0) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = ArenaBlack)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.SportsBasketball, contentDescription = null, tint = CourtOrange)
                                    Text("把手感练成稳定输出", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                }
                                Text("中投 · 三分 · 罚球 · 终结 · 运控", color = Color.White.copy(alpha = 0.75f))
                                Text(
                                    "今日 ${todaySummary.groups} 组 · ${todaySummary.made}/${todaySummary.attempts} 命中 · ${todaySummary.durationLabel}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    item {
                        BallPanel {
                            Text("AI 结合球训练计划", style = MaterialTheme.typography.titleMedium)
                            Text("不止定点投篮：把运球、变向、急停和跑位接球串成一堂训练课。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { navController.navigate("ball_plan?focus=balanced") }, modifier = Modifier.fillMaxWidth()) { Text("制定 / 查看有球计划") }
                            TextButton(onClick = { navController.navigate("photo_analysis?scene=training") }, modifier = Modifier.fillMaxWidth()) { Text("上传动作照片 · 辅助技术复盘") }
                        }
                    }
                    item {
                        Text("选择训练项目", style = MaterialTheme.typography.titleMedium)
                        Text("进入专项选择动作，或制定专项 AI 计划；动作目录与记录可离线使用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(BallDrill.entries, key = { it.key }) { drill ->
                        Card(
                            onClick = { navController.navigate("ball_category?category=${drill.key}") },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(drill.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text("${BallMoves.forCategory(drill.key).size} 个动作 →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                                Text(drill.subtitle, style = MaterialTheme.typography.bodyMedium)
                                Text(drill.suggestion, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        TextButton(onClick = { tab = 1 }, modifier = Modifier.fillMaxWidth()) { Text("查看全部训练记录") }
                    }
                } else {
                    item {
                        ChoiceRow(
                            options = listOf("" to "全部") + BallDrill.entries.map { it.key to it.label },
                            selected = category,
                            onSelect = { category = it }
                        )
                        ChoiceRow(
                            options = listOf("7" to "近7天", "30" to "近30天", "0" to "全部时间"),
                            selected = rangeDays.toString(),
                            onSelect = { rangeDays = it.toInt() }
                        )
                    }
                    item { TrainingSummary(filtered) }
                    if (filtered.isEmpty()) {
                        item {
                            BallPanel {
                                Text("还没有这个范围的训练记录", style = MaterialTheme.typography.titleMedium)
                                Text("选择项目完成第一组，或调整分类和时间范围查看历史。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { tab = 0 }) { Text("开始记录") }
                            }
                        }
                    } else if (tab == 1) {
                        filtered.groupBy { it.date }.forEach { (date, dayRecords) ->
                            item(key = "date_$date") {
                                Text("$date · ${dayRecords.size} 组", style = MaterialTheme.typography.titleSmall)
                            }
                            items(dayRecords, key = { it.id }) { record ->
                                RecordCard(
                                    record,
                                    onEdit = { navController.navigate("ball_record?recordId=${record.id}") },
                                    onDelete = { pendingDelete = record }
                                )
                            }
                        }
                    } else {
                        item {
                            BallPanel {
                                Text("分项表现", style = MaterialTheme.typography.titleMedium)
                                Text("命中率 = 总命中 ÷ 总出手；运控不计入投篮数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                BallDrill.entries.filter { category.isEmpty() || it.key == category }.forEach { drill ->
                                    val drillRecords = filtered.filter { it.category == drill.key }
                                    if (drillRecords.isNotEmpty()) {
                                        PerformanceRow(drill.label, drillRecords, drill.tracksShots)
                                    }
                                }
                            }
                        }
                        BallDrill.fromKey(category)?.let { drill ->
                            item {
                                BallPanel {
                                    Text("技术动作表现", style = MaterialTheme.typography.titleMedium)
                                    filtered.groupBy { it.movementKey }.forEach { (movementKey, moveRecords) ->
                                        PerformanceRow(BallMoves.label(movementKey), moveRecords, drill.tracksShots)
                                    }
                                }
                            }
                            item {
                                BallPanel {
                                    Text(if (drill.tracksShots) "点位表现" else "动作时长", style = MaterialTheme.typography.titleMedium)
                                    drill.positions.forEach { position ->
                                        val positionRecords = filtered.filter { it.position == position }
                                        if (positionRecords.isNotEmpty()) PerformanceRow(position, positionRecords, drill.tracksShots)
                                    }
                                }
                            }
                        }
                        item {
                            BallPanel {
                                Text("最近 7 天", style = MaterialTheme.typography.titleMedium)
                                Text("沿用当前分类筛选。空白日期表示未记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                (6 downTo 0).forEach { offset ->
                                    val date = today.minusDays(offset.toLong()).toString()
                                    val daily = summarizeBallTraining(filtered.filter { it.date == date })
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(date.takeLast(5), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (daily.groups == 0) "—" else if (category == BallDrill.HANDLING.key) daily.durationLabel
                                            else "${daily.made}/${daily.attempts} · ${daily.accuracy}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text("删除这组训练？") },
            text = { Text("${record.date} · ${BallDrill.fromKey(record.category)?.label.orEmpty()} · ${record.position}\n删除后会同步更新命中率统计。") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    scope.launch {
                        try {
                            repo.deleteBallTrainingRecord(record)
                            pendingDelete = null
                            snackbar.showSnackbar("已删除训练记录")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            snackbar.showSnackbar("删除失败，请重试")
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text(if (deleting) "删除中" else "删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
internal fun BallPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.first }) { (key, label) ->
            FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(label) })
        }
    }
}

@Composable
private fun TrainingSummary(records: List<BallTrainingRecordEntity>) {
    val summary = summarizeBallTraining(records)
    BallPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryMetric("命中 / 出手", "${summary.made}/${summary.attempts}", Modifier.weight(1f))
            SummaryMetric("综合命中率", summary.accuracy, Modifier.weight(1f))
        }
        Text("${summary.days} 个训练日 · ${summary.groups} 组 · 已记录 ${summary.durationLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PerformanceRow(label: String, records: List<BallTrainingRecordEntity>, tracksShots: Boolean) {
    val summary = summarizeBallTraining(records)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(if (tracksShots) "${summary.made}/${summary.attempts} · ${summary.accuracy}" else summary.durationLabel, style = MaterialTheme.typography.bodyMedium)
        }
        if (tracksShots) {
            LinearProgressIndicator(
                progress = { if (summary.attempts == 0L) 0f else (summary.made.toFloat() / summary.attempts).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun RecordCard(record: BallTrainingRecordEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val drill = BallDrill.fromKey(record.category)
    BallPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${drill?.label ?: record.category} · ${record.position}", style = MaterialTheme.typography.titleSmall)
                Text(BallMoves.label(record.movementKey), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (drill?.tracksShots == true) "${record.made}/${record.attempts} 命中 · ${summarizeBallTraining(listOf(record)).accuracy}"
                    else "运控 ${formatBallDuration(record.totalDurationSeconds())}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑这组训练") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "删除这组训练") }
        }
        if (record.planId != null) Text("来自 AI 有球计划 · 主训练 ${(record.planItemId.toIntOrNull() ?: 0) + 1}", style = MaterialTheme.typography.bodySmall)
        if (drill?.tracksShots == true && record.totalDurationSeconds() > 0) Text("用时 ${formatBallDuration(record.totalDurationSeconds())}", style = MaterialTheme.typography.bodySmall)
        if (record.note.isNotBlank()) Text(record.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
