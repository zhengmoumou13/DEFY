package com.workhub.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.workhub.app.settings.UserSettings
import com.workhub.app.settings.ProfileValidation
import com.workhub.app.ui.theme.DefyBrand
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun OnboardingFlow(
    settings: UserSettings,
    onSaveProfile: suspend (height: String, weight: String, goal: String) -> Unit,
    onFinish: () -> Unit,
    onRecordBaseline: () -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var height by remember(settings.heightCm) { mutableStateOf(settings.heightCm) }
    var weight by remember(settings.weightKg) { mutableStateOf(settings.weightKg) }
    var goal by remember(settings.goal) { mutableStateOf(settings.goal) }

    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { if (!saving) onFinish() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    color = if (index <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                when (step) {
                    0 -> WelcomeStep()
                    1 -> ProfileStep(
                        height = height,
                        weight = weight,
                        goal = goal,
                        onHeightChange = { height = it },
                        onWeightChange = { weight = it },
                        onGoalChange = { goal = it }
                    )
                    else -> BaselineStep()
                }

                if (step == 2) {
                    Button(
                        onClick = {
                            onFinish()
                            onRecordBaseline()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("记录第一次基线") }
                    OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                        Text("进入 DEFY")
                    }
                } else {
                    Button(
                        onClick = {
                            if (step == 0) step = 1
                            else {
                                error = ProfileValidation.error(height, weight)
                                if (error == null) {
                                    saving = true
                                    scope.launch {
                                        try { onSaveProfile(height.trim(), weight.trim(), goal); step = 2 }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { error = "保存失败，请重试" }
                                        finally { saving = false }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                        , enabled = !saving
                    ) { Text(if (saving) "保存中…" else if (step == 0) "开始设置" else "保存并继续") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                if (step == 0) {
                    TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("稍后再说")
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DefyBrand()
        Text("为下一次突破做好准备", style = MaterialTheme.typography.headlineMedium)
        Text(
            "DEFY · 跃过引力，根据你的档案与执行记录，安排训练、饮食和复盘。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OnboardingFeature(Icons.Filled.FitnessCenter, "计划能执行", "每天只看该做的主训练动作")
        OnboardingFeature(Icons.Filled.Insights, "进步能追踪", "用同一标准记录摸高、力量和恢复")
    }
}

@Composable
private fun ProfileStep(
    height: String,
    weight: String,
    goal: String,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onGoalChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("建立训练档案", style = MaterialTheme.typography.headlineMedium)
        Text("这些信息用于调整训练量与饮食建议，之后可在设置中修改。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = height,
                onValueChange = onHeightChange,
                label = { Text("身高 cm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = weight,
                onValueChange = onWeightChange,
                label = { Text("体重 kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Text("当前目标", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("减脂", "增肌", "保持").forEach { option ->
                FilterChip(
                    selected = goal == option,
                    onClick = { onGoalChange(option) },
                    label = { Text(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BaselineStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("记录你的起点", style = MaterialTheme.typography.headlineMedium)
        Text(
            "充分热身后记录站立摸高和助跑摸高。以后每 7–14 天在相同条件下复测，趋势才有意义。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("建议顺序", style = MaterialTheme.typography.titleSmall)
                Text("01  动态热身与低强度起跳")
                Text("02  记录站立摸高")
                Text("03  三次助跑摸高，保留最好成绩")
            }
        }
        Text("摸到篮筐不等于稳定扣篮，工作台会同时显示摸框差距与建议扣篮线。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OnboardingFeature(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
