package com.workhub.app.ui.settings

import com.workhub.app.ai.GlmSpeechInput
import com.workhub.app.ai.GlmCompatibility
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.workhub.app.ai.ConnectionTester
import com.workhub.app.ai.AiServiceKind
import com.workhub.app.ai.ConnectionTestResult
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Job
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.workhub.app.ai.AiConfig
import com.workhub.app.ai.MediaAiClient
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.ui.ball.BallPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaSettingsScreen(repository: SettingsRepository, navController: NavHostController, initialMode: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val vision = initialMode == "vision"
    val textMode = initialMode == "text"
    val title = if (textMode) "文字 AI" else if (vision) "图片分析" else "语音转文字"
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val kind = if (textMode) AiServiceKind.TEXT else if (vision) AiServiceKind.VISION else AiServiceKind.SPEECH
    val tester = remember { ConnectionTester() }
    var testing by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<List<String>?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val dirty = !loading && original != null && original != listOf(baseUrl, apiKey, model)
    val back: () -> Unit = { if (!saving) { if (dirty) leaving = true else navController.popBackStack() } }
    BackHandler(enabled = dirty || saving) { back() }
    DisposableEffect(context, lifecycle) {
        val window = (context as? Activity)?.window
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        lifecycle.addObserver(observer)
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { lifecycle.removeObserver(observer); window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(initialMode) {
        testJob?.cancel()
        testResult = null
        testError = null
        loading = true
        visible = false
        status = null
        val current = repository.settings.first()
        baseUrl = if (textMode) current.apiBaseUrl else if (vision) current.visionBaseUrl else current.speechBaseUrl
        apiKey = if (textMode) current.apiKey else if (vision) current.visionApiKey else current.speechApiKey
        model = if (textMode) current.model else if (vision) current.visionModel else current.speechModel
        original = listOf(baseUrl, apiKey, model)
        loading = false
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("配置尚未保存") },
        text = { Text("返回会放弃本次填写，之前保存的配置不受影响。") },
        confirmButton = { TextButton(onClick = { testJob?.cancel(); leaving = false; navController.popBackStack() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("继续编辑") } })
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text("清除$title 配置？") },
        text = { Text("将删除已保存的地址、密钥和模型。再次使用需要重新填写。") },
        confirmButton = { TextButton(onClick = {
            clearing = false; saving = true
            scope.launch {
                try {
                    if (textMode) repository.saveAiConfig("", "", "") else repository.saveMediaConfig(vision, "", "", "")
                    baseUrl = ""; apiKey = ""; model = ""; original = listOf("", "", "")
                    testResult = null; testError = null; failed = false; status = "已清除该服务配置"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true; status = "清除失败，请重试" }
                finally { saving = false }
            }
        }) { Text("确认清除") } },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text("保留配置") } })
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text(title) }, windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BallPanel {
                Text("$title · 服务配置", style = MaterialTheme.typography.titleLarge)
                Text(when {
                    textMode -> "用于训练计划、饮食计划和 AI 对话。"
                    vision -> "用于动作与餐食照片分析；未单独设置密钥时沿用现有文字服务密钥。"
                    else -> "接口地址、API Key、模型名分别独立保存。录音转写只使用这里的配置，不会调用文字或图片服务。"
                }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (!textMode && !vision) "支持 GLM-ASR-2512 录音转文字及兼容转写服务。按住录音，松开自动转写并追加到输入框，发送前可编辑。" else "填写兼容 chat/completions 的接口地址和模型名。", style = MaterialTheme.typography.bodySmall)
                if (!textMode && !vision) {
                    FilterChip(selected = GlmSpeechInput.matches(model), enabled = !loading && !saving && !testing, onClick = {
                        if (baseUrl.trimEnd('/') != GlmCompatibility.BASE_URL) apiKey = ""
                        baseUrl = GlmCompatibility.BASE_URL; model = GlmSpeechInput.MODEL
                        status = "已填写语音服务，请输入对应密钥并测试"; failed = false; testResult = null; testError = null
                    }, label = { Text("智谱 GLM-ASR-2512") })
                    if (GlmSpeechInput.matches(model)) Text("只用于录音转文字，单段最长 30 秒。松开后自动转写，文字追加到输入框；训练与对话继续使用各自的 AI 配置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (textMode) {
                    Text("快捷填写", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("智谱 GLM", GlmCompatibility.BASE_URL, GlmCompatibility.DEFAULT_MODEL), Triple("DeepSeek", AiConfig.BASE_URL, AiConfig.MODEL)).forEach { (label, url, defaultModel) ->
                            FilterChip(selected = baseUrl.trimEnd('/') == url, enabled = !loading && !saving && !testing, onClick = {
                                if (baseUrl.trimEnd('/') != url) apiKey = ""
                                baseUrl = url; model = defaultModel; status = "已填写服务地址和模型，请使用该服务的 API Key"; failed = false; testResult = null; testError = null
                            }, label = { Text(label) })
                        }
                    }
                    Text("也可手动填写其他兼容服务。切换服务时清空当前表单密钥，保存后生效。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (baseUrl.contains("bigmodel.cn", ignoreCase = true)) {
                        Text("使用智谱标准 API 密钥与账户额度；Coding Plan 订阅额度不适用于本 App。默认 glm-4.7 使用快速回答，模型名可按账户权限修改。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(baseUrl, { baseUrl = it; status = null; testResult = null; testError = null }, label = { Text("HTTPS 接口地址") }, placeholder = { Text("https://服务域名/v1") }, enabled = !loading && !saving && !testing, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiKey, { apiKey = it; status = null; testResult = null; testError = null }, label = { Text("API Key（加密保存在本机）") }, enabled = !loading && !saving && !testing, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "隐藏密钥" else "显示密钥") } }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it; status = null; testResult = null; testError = null }, label = { Text(if (textMode) "文字模型名" else if (vision) "视觉模型名" else "语音转文字模型名") }, enabled = !loading && !saving && !testing, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("密钥加密保存在本机，不进入导出备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("测试当前填写的配置，不会自动保存；使用内置测试内容，可能产生少量调用费用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    testing = true; testResult = null; testError = null; status = null
                    testJob = scope.launch {
                        try { testResult = tester.test(kind, baseUrl, apiKey, model) }
                        catch (cancelled: CancellationException) { status = "测试已取消"; throw cancelled }
                        catch (error: Exception) { testError = error.message ?: "连接失败，请检查配置" }
                        finally { testing = false }
                    }
                }, enabled = !loading && !saving && !testing && baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (testing) "正在测试连接…" else "测试连接")
                }
                if (testing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { testJob?.cancel() }) { Text("取消测试") }
                }
                testResult?.let { result ->
                    Text("连接成功 · ${result.elapsedMs} ms", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text(result.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                testError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    saving = true
                    status = null
                    scope.launch {
                        try {
                            MediaAiClient.endpoint(baseUrl, if (vision || textMode) "chat/completions" else "audio/transcriptions")
                            if (textMode) repository.saveAiConfig(baseUrl, apiKey, model) else repository.saveMediaConfig(vision, baseUrl, apiKey, model)
                            original = listOf(baseUrl, apiKey, model)
                            failed = false
                            status = "配置已保存，可点击“测试连接”验证当前服务"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true; status = "保存失败，请检查 HTTPS 地址格式后重试" }
                        finally { saving = false }
                    }
                }, enabled = !loading && !saving && !testing && baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (saving) "保存中…" else "保存服务配置") }
                OutlinedButton(onClick = { clearing = true }, enabled = !loading && !saving && !testing,
                    modifier = Modifier.fillMaxWidth()) { Text("清除这个服务的配置") }
                status?.let { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
