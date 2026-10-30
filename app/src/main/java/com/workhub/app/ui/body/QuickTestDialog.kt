package com.workhub.app.ui.body

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 周期弹跳复测：只填站立摸高 + 助跑摸高（可选体重），
 * 同时显示摸框差距与建议扣篮摸高线。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QuickTestDialog(
    rim: Double,
    onDismiss: () -> Unit,
    onSave: (
        standingReachCm: Double?,
        maxReachCm: Double?,
        weightKg: Double?,
        verticalJump: Double?,
        dunkGap: Double?
    ) -> Unit
) {
    val context = LocalContext.current
    var standingReach by remember { mutableStateOf("") }
    var maxReach by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }

    val sr = standingReach.trim().toDoubleOrNull()
    val mr = maxReach.trim().toDoubleOrNull()
    val vert = mr?.let { m -> sr?.let { m - it } }
    val rimGap = mr?.let { rim - it }
    val dunkReference = rim + 15.0
    val dunkGap = mr?.let { dunkReference - it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚡ 本周弹跳复测") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "先充分热身，再用与上次相同的场地、鞋子和测量方式完成 3–5 次高质量尝试。建议每 7–14 天复测一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = standingReach,
                    onValueChange = { standingReach = it },
                    label = { Text("站立摸高 cm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxReach,
                    onValueChange = { maxReach = it },
                    label = { Text("助跑摸高 cm ★") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("体重 kg（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (vert != null) {
                    Text(
                        "📏 垂直弹跳：${String.format("%.0f", vert)} cm",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (rimGap != null) {
                    Text(
                        if (rimGap <= 0) "已达到 ${String.format("%.0f", rim)}cm 篮筐高度"
                        else "距摸框 ${String.format("%.0f", rimGap)} cm",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (rimGap <= 0) androidx.compose.ui.graphics.Color(0xFF16A34A)
                        else MaterialTheme.colorScheme.primary
                    )
                }
                if (dunkGap != null) {
                    Text(
                        if (dunkGap <= 0) "已达到建议扣篮摸高线（篮筐上方约 15cm）"
                        else "距建议扣篮摸高线 ${String.format("%.0f", dunkGap)} cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (sr == null && mr == null && weight.trim().toDoubleOrNull() == null) {
                    Toast.makeText(context, "请至少填写一项", Toast.LENGTH_SHORT).show()
                } else {
                    onSave(sr, mr, weight.trim().toDoubleOrNull(), vert, rimGap)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
