package com.workhub.app.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.workhub.app.ui.markdown.Markdown

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FitnessPlanOverview(
    content: String,
    items: List<PlanCheckItem>,
    checkedMap: Map<String, Boolean>,
    canTrain: Boolean,
    onStart: () -> Unit,
    onTasks: () -> Unit,
    tasksEnabled: Boolean,
    onCopy: () -> Unit
) {
    val plan = remember(content) { fitnessPlanPresentation(content) }
    val done = items.count { checkedMap[it.key] == true }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("训练重点", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                plan.goal?.replace(Regex("\\s*[+＋]\\s*"), "\n+ ") ?: "本次训练安排",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.isNotEmpty()) Text("${items.size} 个动作", style = MaterialTheme.typography.labelLarge)
                plan.duration?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                plan.intensity?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            }
            if (items.isNotEmpty()) {
                Button(
                    onClick = onStart,
                    enabled = canTrain,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        when {
                            !canTrain -> "计划预览"
                            done == items.size -> "查看本次训练"
                            done > 0 -> "继续训练 · $done / ${items.size}"
                            else -> "开始训练"
                        }, modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }

    plan.sections.filter { it.title == "热身" }.forEach { section ->
        PlanDisclosure("训练前 · 热身", section.content) { Markdown.Render(section.content) }
    }

    if (items.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("主训练", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onTasks, enabled = tasksEnabled) { Text("转为任务") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, item ->
                FitnessExerciseCard(index + 1, item, checkedMap[item.key] == true)
            }
        }
    } else {
        // Recovery days and unfamiliar formats are readable without claiming they contain actions.
        Text("本日未列出可勾选的主训练动作", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val supporting = plan.sections.filter { it.title !in setOf("热身", "主训练", "停止条件", "今日判断", "计划依据") }
    if (supporting.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            supporting.forEach { section ->
                PlanDisclosure(section.title, section.content) { Markdown.Render(section.content) }
            }
        }
    }
    // Keep stop conditions visible, with the exact advice supplied by the plan.
    plan.sections.filter { it.title == "停止条件" }.forEach { section ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("何时停止训练", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Markdown.Render(section.content)
            }
        }
    }
    val background = plan.sections.filter { it.title in setOf("今日判断", "计划依据", "主训练") }
    if (background.isNotEmpty()) {
        PlanDisclosure("计划依据与补充说明", content) {
            background.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Markdown.Render(section.content)
            }
        }
    }
    TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text("复制完整计划") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FitnessExerciseCard(
    number: Int, item: PlanCheckItem, done: Boolean,
    onToggle: ((Boolean) -> Unit)? = null, onEdit: (() -> Unit)? = null,
    enabled: Boolean = true, framed: Boolean = true
) {
    val exercise = remember(item) { fitnessExercisePresentation(item) }
    var expanded by rememberSaveable(item.key, item.text, item.notes) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        border = if (framed) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().then(
                if (exercise.details.isNotEmpty()) Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起动作要点" else "展开动作要点"
                ) { expanded = !expanded }.semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                else Modifier
            ).padding(if (framed) 16.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onToggle != null) Checkbox(done, onToggle, enabled = enabled, modifier = Modifier.semantics { contentDescription = "${exercise.name}完成状态" })
                else Text(number.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (done && onToggle == null) Text("已完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onEdit != null) IconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Filled.Edit, "修改${exercise.name}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (exercise.details.isNotEmpty()) {
                    Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            FlowRow(
                modifier = Modifier.padding(start = if (onToggle == null) 28.dp else 58.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                exercise.dose?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically)) }
                listOfNotNull(exercise.intensity, exercise.rest).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                exercise.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
internal fun PlanDisclosure(title: String, contentKey: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title, contentKey) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                    .heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            if (expanded) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}
