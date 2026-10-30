package com.workhub.app.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.workhub.app.ui.markdown.Markdown

@Composable
internal fun DietPlanOverview(
    content: String,
    items: List<PlanCheckItem>,
    checkedMap: Map<String, Boolean>,
    enabled: Boolean,
    onToggle: (PlanCheckItem, Boolean) -> Unit,
    onTasks: () -> Unit,
    tasksEnabled: Boolean,
    onConvert: (String) -> Unit,
    onCopy: () -> Unit
) {
    val plan = remember(content, items) { dietPlanPresentation(content, items) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("营养参考", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (plan.targets.isNotEmpty()) {
                plan.targets.filter { it.first != "饮水" }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { (label, value) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                plan.targets.firstOrNull { it.first == "饮水" }?.let { Text("饮水 · ${it.second}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("计划目标 · 实际成分按食材换算", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onConvert("") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)) { Text("食物成分换算") }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("餐次安排", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (items.isNotEmpty()) Text("已完成 ${items.count { checkedMap[it.key] == true }} / ${items.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onTasks, enabled = tasksEnabled && items.isNotEmpty()) { Text("转为任务") }
    }
    if (plan.meals.isEmpty()) Markdown.Render(content)
    plan.meals.forEach { meal ->
        var expanded by rememberSaveable(content, meal.label) { mutableStateOf(false) }
        Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meal.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    meal.item?.let { item ->
                        Checkbox(checked = checkedMap[item.key] == true, enabled = enabled, onCheckedChange = { onToggle(item, it) })
                    }
                    if (meal.details.isNotEmpty()) IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (expanded) "收起${meal.label}说明" else "展开${meal.label}说明")
                    }
                }
                meal.foods.forEach { food -> Text(food, style = MaterialTheme.typography.bodyLarge) }
                if (expanded) {
                    HorizontalDivider()
                    meal.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (meal.foods.isNotEmpty() && meal.label !in setOf("饮水", "补水")) {
                    TextButton(onClick = { onConvert("${meal.label}：${meal.foods.joinToString("；")}") }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("换算这餐的食材 →") }
                }
            }
        }
    }
    if (plan.meals.isNotEmpty()) PlanDisclosure("饮食说明与完整安排", content) { Markdown.Render(content) }
    TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text("复制完整计划") }
}
