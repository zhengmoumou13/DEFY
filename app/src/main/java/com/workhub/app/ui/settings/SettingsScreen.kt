package com.workhub.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.ai.AiConfig
import com.workhub.app.ai.AiClient
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.imePadding
import com.workhub.app.settings.ProfileValidation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsRepository: SettingsRepository, navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val current by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val scope = rememberCoroutineScope()

    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("减脂") }
    var rimHeight by remember { mutableStateOf("305") }
    var weeklyTarget by remember { mutableStateOf("4") }
    var remindEnabled by remember { mutableStateOf(false) }
    var remindHour by remember { mutableStateOf(20) }
    var saved by remember { mutableStateOf(false) }
    var profileSaving by remember { mutableStateOf(false) }
    var profileLoading by remember { mutableStateOf(true) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var aiBaseUrl by remember { mutableStateOf(AiConfig.BASE_URL) }
    var aiApiKey by remember { mutableStateOf("") }
    var aiModel by remember { mutableStateOf(AiConfig.MODEL) }
    var showKey by remember { mutableStateOf(false) }
    var aiSaved by remember { mutableStateOf(false) }
    var aiTesting by remember { mutableStateOf(false) }
    var aiTestResult by remember { mutableStateOf<String?>(null) }
    var aiTestSuccess by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        remindEnabled = granted
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        val window = (context as? Activity)?.window
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Lifecycle.Event.ON_PAUSE -> window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(Unit) {
        val current = settingsRepository.settings.first()
        height = current.heightCm
        weight = current.weightKg
        goal = current.goal
        rimHeight = current.rimHeightCm
        weeklyTarget = current.weeklyTarget
        remindEnabled = current.checkinRemindEnabled
        remindHour = current.checkinRemindHour
        aiBaseUrl = current.apiBaseUrl
        aiApiKey = current.apiKey
        aiModel = current.model
        profileLoading = false
    }
    LaunchedEffect(height, weight, goal, rimHeight, weeklyTarget, remindEnabled, remindHour) { saved = false; profileError = null }

    val save: () -> Unit = save@{
        profileError = ProfileValidation.error(height, weight, rimHeight, weeklyTarget)
        if (profileError != null || profileSaving || profileLoading) return@save
        profileSaving = true
        scope.launch {
          try {
            settingsRepository.save(
                heightCm = height.trim(),
                weightKg = weight.trim(),
                goal = goal,
                rimHeightCm = rimHeight.trim().ifBlank { "305" },
                weeklyTarget = weeklyTarget.trim().ifBlank { "4" },
                checkinRemindEnabled = remindEnabled,
                checkinRemindHour = remindHour
            )
            if (remindEnabled) {
                com.workhub.app.notify.ReminderScheduler.scheduleCheckinReminder(context, remindHour)
            } else {
                com.workhub.app.notify.ReminderScheduler.cancelCheckinReminder(context)
            }
            saved = true
          } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { profileError = "保存失败，请重试" }
          finally { profileSaving = false }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("训练设置") },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsSectionCard(
                title = "训练档案",
                subtitle = "用于生成更贴合你的弹跳训练、力量安排和饮食恢复建议。"
            ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("身高 (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("体重 (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Text("目标", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("减脂", "增肌", "保持").forEach { option ->
                    FilterChip(
                        selected = goal == option,
                        onClick = { goal = option },
                        label = { Text(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            OutlinedTextField(
                value = rimHeight,
                onValueChange = { rimHeight = it },
                label = { Text("篮筐高度 (cm)") },
                supportingText = { Text("标准篮筐 305cm；建议扣篮摸高线约为篮筐上方 15cm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = weeklyTarget,
                onValueChange = { weeklyTarget = it },
                label = { Text("每周训练目标（天）") },
                supportingText = { Text("用于统计训练打卡进度，建议 3–5 天") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            }

            SettingsSectionCard(
                title = "打卡提醒",
                subtitle = "在固定时间提醒你完成当天训练与恢复记录。"
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "每天提醒训练打卡",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = remindEnabled,
                    onCheckedChange = { enabled ->
                        if (
                            enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            remindEnabled = enabled
                        }
                    }
                )
            }
            if (remindEnabled) {
                Text("提醒时间", style = MaterialTheme.typography.titleSmall)
                listOf(
                    listOf(19 to "19:00", 20 to "20:00"),
                    listOf(21 to "21:00", 22 to "22:00")
                ).forEach { options ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (hour, label) ->
                            FilterChip(
                                selected = remindHour == hour,
                                onClick = { remindHour = hour },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            }

            profileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = save, enabled = !profileSaving && !profileLoading, modifier = Modifier.fillMaxWidth()) {
                Text(if (profileSaving) "保存中…" else if (saved) "训练设置已保存" else "保存训练设置")
            }

            SettingsSectionCard(title = "图片与语音", subtitle = "语音转文字的地址、API Key 和模型独立保存。") {
                OutlinedButton(onClick = { navController.navigate("media_settings?mode=vision") }, modifier = Modifier.fillMaxWidth()) { Text("配置图片分析") }
                OutlinedButton(onClick = { navController.navigate("media_settings?mode=speech") }, modifier = Modifier.fillMaxWidth()) { Text("配置语音转文字") }
            }

            SettingsSectionCard(
                title = "AI 服务",
                subtitle = "选择常用服务或填写 OpenAI 兼容接口。密钥使用 Android Keystore 加密，且不会进入备份。"
            ) {
            Text("服务商", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AI_SERVICE_PRESETS.forEach { preset ->
                    val selected = aiBaseUrl.trimEnd('/') == preset.baseUrl
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (aiBaseUrl.trimEnd('/') != preset.baseUrl) aiApiKey = ""
                            aiBaseUrl = preset.baseUrl
                            aiModel = preset.model
                            aiSaved = false; aiTestResult = null
                        },
                        label = { Text(preset.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = { aiBaseUrl = it; aiSaved = false; aiTestResult = null },
                label = { Text("接口地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = { aiApiKey = it; aiSaved = false; aiTestResult = null },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "隐藏" else "显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = aiModel,
                onValueChange = { aiModel = it; aiSaved = false; aiTestResult = null },
                label = { Text("模型名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            aiTesting = true
                            aiTestResult = null
                            val tested = Triple(aiBaseUrl, aiApiKey, aiModel)
                            try {
                                val result = AiClient.testConnection(tested.first, tested.second, tested.third)
                                if (tested == Triple(aiBaseUrl, aiApiKey, aiModel)) {
                                    aiTestSuccess = true
                                    aiTestResult = "连接成功 · ${result.elapsedMs} ms"
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                if (tested == Triple(aiBaseUrl, aiApiKey, aiModel)) {
                                    aiTestSuccess = false
                                    aiTestResult = failure.message ?: "连接失败，请检查配置"
                                }
                            } finally { aiTesting = false }
                        }
                    },
                    enabled = !aiTesting && aiApiKey.isNotBlank() && aiBaseUrl.isNotBlank() && aiModel.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (aiTesting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null)
                    }
                    Text(if (aiTesting) "检测中" else "测试连接", modifier = Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = {
                        scope.launch {
                            settingsRepository.saveAiConfig(aiBaseUrl, aiApiKey, aiModel)
                            aiSaved = true
                        }
                    },
                    enabled = aiApiKey.isNotBlank() && aiBaseUrl.isNotBlank() && aiModel.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("保存配置") }
            }
            aiTestResult?.let { result ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (aiTestSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = if (aiTestSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (aiTestSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (aiSaved) {
                Text("AI 配置已保存", color = MaterialTheme.colorScheme.primary)
            }
            }

        }
    }
}

private data class AiServicePreset(
    val label: String,
    val baseUrl: String,
    val model: String
)

private val AI_SERVICE_PRESETS = listOf(
    AiServicePreset("智谱 GLM", com.workhub.app.ai.GlmCompatibility.BASE_URL, com.workhub.app.ai.GlmCompatibility.DEFAULT_MODEL),
    AiServicePreset("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    AiServicePreset("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini"),
    AiServicePreset("通义", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus")
)

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}
