package com.workhub.app.ui.body

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.BodyRecordEntity
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyDataScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by repo.allBodyRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())

    var showAddDialog by remember { mutableStateOf(false) }
    var showQuickTest by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BodyRecordEntity?>(null) }
    var selectedSection by remember { mutableStateOf(BodySection.OVERVIEW) }

    val rim = settings.rimHeightCm.toDoubleOrNull() ?: 305.0
    val latest = records.firstOrNull()
    fun verticalJump(r: BodyRecordEntity): Double? =
        r.maxReachCm?.let { mr -> r.standingReachCm?.let { mr - it } }

    // 趋势（按日期升序）
    val ascending = records.asReversed()
    val reachSeries = ascending.mapNotNull { it.maxReachCm?.toFloat() }.takeLast(30)
    val vertSeries = ascending.mapNotNull { verticalJump(it)?.toFloat() }.takeLast(30)
    val weightSeries = ascending.mapNotNull { it.weightKg?.toFloat() }.takeLast(30)
    val squatSeries = ascending.mapNotNull { it.squatKg?.toFloat() }.takeLast(30)
    val deadliftSeries = ascending.mapNotNull { it.deadliftKg?.toFloat() }.takeLast(30)
    val benchSeries = ascending.mapNotNull { it.benchKg?.toFloat() }.takeLast(30)
    val standingJumpSeries = ascending.mapNotNull { it.standingJumpCm?.toFloat() }.takeLast(30)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("弹跳数据") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showQuickTest = true }) {
                        Icon(Icons.Filled.Bolt, contentDescription = "快速测试")
                    }
                    IconButton(
                        onClick = { latest?.let { shareBodySummary(context, it, rim) } },
                        enabled = latest != null
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "记录弹跳数据")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BodySection.entries.forEach { section ->
                        FilterChip(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            label = { Text(section.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            when (selectedSection) {
                BodySection.OVERVIEW -> {
                    item { DunkLatestCard(records = records, rim = rim) }
                    item {
                        Text(
                            "先记录站立摸高与助跑摸高建立基线，再按周期补充力量数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                BodySection.TRENDS -> {
                    val hasTrend = listOf(
                        reachSeries, vertSeries, weightSeries, squatSeries,
                        deadliftSeries, benchSeries, standingJumpSeries
                    ).any { it.size >= 2 }
                    if (!hasTrend) {
                        item { BodyEmptyState("至少记录两次数据后，才能生成有效趋势") }
                    }
                    if (reachSeries.size >= 2) item { ChartCard("助跑摸高趋势 (cm)", reachSeries, MaterialTheme.colorScheme.primary) }
                    if (vertSeries.size >= 2) item { ChartCard("垂直弹跳趋势 (cm)", vertSeries, MaterialTheme.colorScheme.tertiary) }
                    if (weightSeries.size >= 2) item { ChartCard("体重趋势 (kg)", weightSeries, MaterialTheme.colorScheme.secondary) }
                    if (squatSeries.size >= 2) item { ChartCard("深蹲 1RM 趋势 (kg)", squatSeries, MaterialTheme.colorScheme.primary) }
                    if (deadliftSeries.size >= 2) item { ChartCard("硬拉 1RM 趋势 (kg)", deadliftSeries, MaterialTheme.colorScheme.tertiary) }
                    if (benchSeries.size >= 2) item { ChartCard("卧推 1RM 趋势 (kg)", benchSeries, MaterialTheme.colorScheme.secondary) }
                    if (standingJumpSeries.size >= 2) item { ChartCard("立定跳远趋势 (cm)", standingJumpSeries, MaterialTheme.colorScheme.tertiary) }
                }

                BodySection.HISTORY -> {
                    item {
                        Text(
                            "历史记录（${records.size} 条）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (records.isEmpty()) item { BodyEmptyState("还没有记录，点右下角 + 建立第一条数据") }
                    items(records, key = { it.id }) { record ->
                        RecordRow(record, rim = rim) { pendingDelete = record }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        BodyRecordDialog(
            onDismiss = { showAddDialog = false },
            onSave = { record ->
                scope.launch { repo.saveBodyRecord(record) }
                showAddDialog = false
                Toast.makeText(context, "已保存 ${record.date} 的身体数据", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showQuickTest) {
        QuickTestDialog(
            rim = rim,
            onDismiss = { showQuickTest = false },
            onSave = { sr, mr, w, vert, gap ->
                showQuickTest = false
                scope.launch {
                    repo.saveBodyRecord(
                        BodyRecordEntity(
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
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    pendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记录") },
            text = { Text("确定删除 ${r.date} 的弹跳记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { repo.deleteBodyRecord(r) }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

private enum class BodySection(val label: String) {
    OVERVIEW("数据总览"),
    TRENDS("历史趋势"),
    HISTORY("记录明细")
}

@Composable
private fun BodyEmptyState(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/** 取各指标最近一次的有效记录（任一记录里有该数据就用最新的） */
private fun latestKnown(
    records: List<BodyRecordEntity>,
    pick: (BodyRecordEntity) -> Double?
): Double? = records.firstOrNull { pick(it) != null }?.let { pick(it) }

@Composable
private fun DunkLatestCard(
    records: List<BodyRecordEntity>,
    rim: Double
) {
    val dunkReference = rim + 15.0
    val latest = records.firstOrNull()
    val previous = records.getOrNull(1)
    // 合并各指标的最新值
    val weightKg = latestKnown(records) { it.weightKg }
    val heightCm = latestKnown(records) { it.heightCm }
    val bodyFatPct = latestKnown(records) { it.bodyFatPct }
    val wingspanCm = latestKnown(records) { it.wingspanCm }
    val standingReachCm = latestKnown(records) { it.standingReachCm }
    val maxReachCm = latestKnown(records) { it.maxReachCm }
    val jumpReachCm = latestKnown(records) { it.standingJumpReachCm }
    val standingJumpCm = latestKnown(records) { it.standingJumpCm }
    val squatKg = latestKnown(records) { it.squatKg }
    val deadliftKg = latestKnown(records) { it.deadliftKg }
    val benchKg = latestKnown(records) { it.benchKg }
    val pullupCount = latestKnown(records) { it.pullupCount }
    val vert = maxReachCm?.let { mr -> standingReachCm?.let { sr -> mr - sr } }
    val rimGap = maxReachCm?.let { rim - it }
    val dunkGap = maxReachCm?.let { dunkReference - it }
    val ape = wingspanCm?.let { w -> heightCm?.let { h -> w - h } }
    val jumpVert = jumpReachCm?.let { jr -> standingReachCm?.let { sr -> jr - sr } }
    val runGain = maxReachCm?.let { mr -> jumpReachCm?.let { jr -> mr - jr } }

    if (latest == null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("建立你的扣篮数据基线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "先记录站立摸高和助跑摸高建立基线，再逐步补充体重与力量数据。App 会区分摸框差距和建议扣篮摸高线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最新 ${latest.date}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                weightKg?.let {
                    Text(
                        "${String.format("%.1f", it)} kg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 核心指标：垂直弹跳 + 距扣篮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBox(
                    label = "垂直弹跳",
                    value = vert?.let { String.format("%.0f", it) } ?: "--",
                    unit = "cm",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "助跑摸高",
                    value = maxReachCm?.let { String.format("%.0f", it) } ?: "--",
                    unit = "cm",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "站立摸高",
                    value = standingReachCm?.let { String.format("%.0f", it) } ?: "--",
                    unit = "cm",
                    modifier = Modifier.weight(1f)
                )
            }
            // 第二行动态指标（原地纵跳 / 助跑增益 / 立定跳远）
            if (jumpVert != null || runGain != null || standingJumpCm != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricBox(
                        label = "原地纵跳",
                        value = jumpVert?.let { String.format("%.0f", it) } ?: "--",
                        unit = "cm",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "助跑增益",
                        value = runGain?.let { "+${String.format("%.0f", it)}" } ?: "--",
                        unit = "cm",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "立定跳远",
                        value = standingJumpCm?.let { String.format("%.0f", it) } ?: "--",
                        unit = "cm",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 摸框与扣篮参考线
            when {
                rimGap == null -> {}
                dunkGap != null && dunkGap <= 0 -> Text(
                    "已达到建议扣篮摸高线，下一步提升持球与完成稳定性",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A)
                )
                rimGap <= 0 -> Text(
                    "已达到篮筐高度 · 距建议扣篮摸高线 ${String.format("%.0f", dunkGap)}cm",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                else -> Text(
                    "距摸框 ${String.format("%.0f", rimGap)}cm · 距建议扣篮摸高线 ${String.format("%.0f", dunkGap)}cm",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            // 辅助指标（力量 + 相对力量）
            Text(
                buildString {
                    ape?.let { append("臂展优势 ${String.format("%+.0f", it)}cm") }
                    squatKg?.let { append(if (isNotEmpty()) " · " else ""); append("深蹲 ${String.format("%.0f", it)}kg") }
                    deadliftKg?.let { append(if (isNotEmpty()) " · " else ""); append("硬拉 ${String.format("%.0f", it)}kg") }
                    benchKg?.let { append(if (isNotEmpty()) " · " else ""); append("卧推 ${String.format("%.0f", it)}kg") }
                    pullupCount?.let { append(if (isNotEmpty()) " · " else ""); append("引体 ${String.format("%.0f", it)}次") }
                    squatKg?.let { sq ->
                        weightKg?.let { w ->
                            if (w > 0) append(if (isNotEmpty()) " · " else ""); append("深蹲/体重 ${String.format("%.2f", sq / w)}x")
                        }
                    }
                    records.firstOrNull { it.maxReachCm != null }?.maxReachCm?.let { c ->
                        records.firstOrNull { r -> r.maxReachCm != null && r.maxReachCm != c }?.maxReachCm?.let { p ->
                            val d = c - p
                            if (d != 0.0) {
                                append(if (isNotEmpty()) " · " else "")
                                append("摸高${if (d > 0) "↑" else "↓"}${String.format("%.1f", kotlin.math.abs(d))}cm")
                            }
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ChartCard(title: String, values: List<Float>, color: Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "最新 ${String.format("%.1f", values.last())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TrendChart(values, color, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun TrendChart(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        if (values.size < 2) return@Canvas
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).coerceAtLeast(0.01f)
        val pad = 10.dp.toPx()
        val stepX = (size.width - pad * 2) / (values.size - 1)
        fun y(v: Float) = pad + (1f - (v - minV) / range) * (size.height - pad * 2)

        val gridColor = color.copy(alpha = 0.15f)
        for (i in 0..3) {
            val gy = pad + (size.height - pad * 2) * i / 3f
            drawLine(gridColor, Offset(pad, gy), Offset(size.width - pad, gy), 1.dp.toPx())
        }

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = pad + i * stepX
            val py = y(v)
            if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
        }
        drawPath(
            path,
            color,
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        values.forEachIndexed { i, v ->
            drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(pad + i * stepX, y(v)))
        }
    }
}

@Composable
private fun RecordRow(record: BodyRecordEntity, rim: Double, onDelete: () -> Unit) {
    val vert = record.maxReachCm?.let { mr -> record.standingReachCm?.let { mr - it } }
    val rimGap = record.maxReachCm?.let { rim - it }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.date,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    buildString {
                        record.weightKg?.let { append("体重 ${String.format("%.1f", it)}kg") }
                        record.maxReachCm?.let { append(if (isNotEmpty()) " · " else ""); append("摸高 ${String.format("%.0f", it)}cm") }
                        vert?.let { append(if (isNotEmpty()) " · " else ""); append("垂直 ${String.format("%.0f", it)}cm") }
                        rimGap?.let {
                            append(if (isNotEmpty()) " · " else "")
                            append(if (it > 0) "距摸框 ${String.format("%.0f", it)}cm" else "已摸框")
                        }
                        record.squatKg?.let { append(if (isNotEmpty()) " · " else ""); append("深蹲 ${String.format("%.0f", it)}kg") }
                        record.deadliftKg?.let { append(if (isNotEmpty()) " · " else ""); append("硬拉 ${String.format("%.0f", it)}kg") }
                        if (record.note.isNotBlank()) { append(if (isNotEmpty()) " · " else ""); append(record.note) }
                    }.ifEmpty { "（无数据）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyRecordDialog(
    onDismiss: () -> Unit,
    onSave: (BodyRecordEntity) -> Unit
) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var wingspan by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var standingReach by remember { mutableStateOf("") }
    var maxReach by remember { mutableStateOf("") }
    var jumpReach by remember { mutableStateOf("") }
    var standingJump by remember { mutableStateOf("") }
    var squat by remember { mutableStateOf("") }
    var deadlift by remember { mutableStateOf("") }
    var bench by remember { mutableStateOf("") }
    var pullup by remember { mutableStateOf("") }
    var plank by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // 实时计算
    val sr = standingReach.trim().toDoubleOrNull()
    val mr = maxReach.trim().toDoubleOrNull()
    val jr = jumpReach.trim().toDoubleOrNull()
    val vert = mr?.let { sr?.let { it - sr } }
    val jumpVert = jr?.let { sr?.let { it - sr } }
    val runGain = mr?.let { jr?.let { it - jr } }

    val save = {
        val values = listOf(
            weight, height, wingspan, bodyFat, standingReach, maxReach,
            jumpReach, standingJump, squat, deadlift, bench, pullup, plank
        )
        if (values.none { it.trim().toDoubleOrNull() != null }) {
            Toast.makeText(context, "请至少填写一项数据", Toast.LENGTH_SHORT).show()
        } else {
            onSave(
                BodyRecordEntity(
                    date = date.toString(),
                    heightCm = height.trim().toDoubleOrNull(),
                    weightKg = weight.trim().toDoubleOrNull(),
                    bodyFatPct = bodyFat.trim().toDoubleOrNull(),
                    wingspanCm = wingspan.trim().toDoubleOrNull(),
                    standingReachCm = standingReach.trim().toDoubleOrNull(),
                    maxReachCm = maxReach.trim().toDoubleOrNull(),
                    standingJumpReachCm = jumpReach.trim().toDoubleOrNull(),
                    standingJumpCm = standingJump.trim().toDoubleOrNull(),
                    squatKg = squat.trim().toDoubleOrNull(),
                    deadliftKg = deadlift.trim().toDoubleOrNull(),
                    benchKg = bench.trim().toDoubleOrNull(),
                    pullupCount = pullup.trim().toDoubleOrNull(),
                    plankSec = plank.trim().toDoubleOrNull(),
                    note = note.trim()
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录弹跳与力量数据") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("日期：$date")
                }

                GroupLabel("📏 静态数据")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(weight, { weight = it }, "体重 kg", Modifier.weight(1f))
                    NumField(height, { height = it }, "身高 cm", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(wingspan, { wingspan = it }, "臂展 cm", Modifier.weight(1f))
                    NumField(bodyFat, { bodyFat = it }, "体脂 %", Modifier.weight(1f))
                }
                NumField(standingReach, { standingReach = it }, "站立摸高 cm（原地举手最高点）", Modifier.fillMaxWidth())

                GroupLabel("🏃 动态数据")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(maxReach, { maxReach = it }, "助跑摸高 cm ★", Modifier.weight(1f))
                    NumField(jumpReach, { jumpReach = it }, "原地纵跳摸高 cm", Modifier.weight(1f))
                }
                NumField(
                    standingJump,
                    { standingJump = it },
                    "立定跳远 cm",
                    Modifier.fillMaxWidth()
                )
                // 实时计算摘要
                if (vert != null || jumpVert != null || runGain != null) {
                    Text(
                        buildString {
                            vert?.let { append("垂直弹跳 ${String.format("%.0f", it)}cm") }
                            jumpVert?.let { append(if (isNotEmpty()) " · " else ""); append("原地纵跳 ${String.format("%.0f", it)}cm") }
                            runGain?.let { append(if (isNotEmpty()) " · " else ""); append("助跑增益 +${String.format("%.0f", it)}cm") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                GroupLabel("力量数据")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(squat, { squat = it }, "深蹲 1RM kg", Modifier.weight(1f))
                    NumField(deadlift, { deadlift = it }, "硬拉 1RM kg", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(bench, { bench = it }, "卧推 1RM kg", Modifier.weight(1f))
                    NumField(pullup, { pullup = it }, "引体向上 次", Modifier.weight(1f))
                }
                NumField(plank, { plank = it }, "平板支撑 秒", Modifier.fillMaxWidth())

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun NumField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/** 分享弹跳数据摘要 */
private fun shareBodySummary(
    context: android.content.Context,
    r: BodyRecordEntity,
    rim: Double
) {
    val vert = r.maxReachCm?.let { mr -> r.standingReachCm?.let { mr - it } }
    val rimGap = r.maxReachCm?.let { rim - it }
    val dunkGap = r.maxReachCm?.let { rim + 15.0 - it }
    val text = buildString {
        appendLine("🏀 DEFY · 跃过引力 · 弹跳数据（${r.date}）")
        r.heightCm?.let { appendLine("身高：${String.format("%.0f", it)} cm") }
        r.weightKg?.let { appendLine("体重：${String.format("%.1f", it)} kg") }
        r.wingspanCm?.let { appendLine("臂展：${String.format("%.0f", it)} cm") }
        r.standingReachCm?.let { appendLine("站立摸高：${String.format("%.0f", it)} cm") }
        r.maxReachCm?.let { appendLine("助跑摸高：${String.format("%.0f", it)} cm") }
        vert?.let { appendLine("垂直弹跳：${String.format("%.0f", it)} cm") }
        rimGap?.let { appendLine(if (it > 0) "距摸框：${String.format("%.0f", it)} cm" else "已达到篮筐高度") }
        dunkGap?.let { appendLine(if (it > 0) "距建议扣篮摸高线：${String.format("%.0f", it)} cm" else "已达到建议扣篮摸高线") }
        r.standingJumpCm?.let { appendLine("立定跳远：${String.format("%.0f", it)} cm") }
        r.standingJumpReachCm?.let { jr ->
            r.standingReachCm?.let { appendLine("原地纵跳：${String.format("%.0f", jr - it)} cm") }
            r.maxReachCm?.let { appendLine("助跑增益：+${String.format("%.0f", it - jr)} cm") }
        }
        r.squatKg?.let { appendLine("深蹲 1RM：${String.format("%.0f", it)} kg") }
        r.deadliftKg?.let { appendLine("硬拉 1RM：${String.format("%.0f", it)} kg") }
        r.benchKg?.let { appendLine("卧推 1RM：${String.format("%.0f", it)} kg") }
        r.pullupCount?.let { appendLine("引体向上：${String.format("%.0f", it)} 次") }
        r.squatKg?.let { sq ->
            r.weightKg?.let { w ->
                if (w > 0) appendLine("深蹲/体重比：${String.format("%.2f", sq / w)}")
            }
        }
    }
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text.trim())
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享弹跳数据"))
    }.onFailure {
        Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
    }
}
