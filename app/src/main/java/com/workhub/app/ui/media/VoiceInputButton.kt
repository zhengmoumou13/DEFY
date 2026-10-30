package com.workhub.app.ui.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workhub.app.ai.GlmSpeechInput
import com.workhub.app.ai.MediaAiClient
import com.workhub.app.media.VoiceRecorder
import com.workhub.app.media.appendVoiceInput
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Press records, release transcribes into the existing draft. No request is sent on cancellation. */
@Composable
fun VoiceInputButton(value: String, onValueChange: (String) -> Unit, enabled: Boolean = true, maxLength: Int = 1500) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val repository = remember(context) { SettingsRepository(context.applicationContext) }
    val settings by repository.settings.collectAsStateWithLifecycle(UserSettings())
    val recorder = remember(context) { VoiceRecorder(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val updateValue by rememberUpdatedState(onValueChange)
    var held by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var limit by remember { mutableIntStateOf(30) }
    var clip by remember { mutableStateOf<File?>(null) }
    var activeSettings by remember { mutableStateOf<UserSettings?>(null) }
    var transcript by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    fun discard() {
        generation++
        job?.cancel(); job = null
        held = false; recording = false; transcribing = false
        recorder.discard(); clip = null; activeSettings = null
    }
    fun close() { discard(); showResult = false; transcript = ""; error = null }
    fun stopRecording() {
        if (!recording) return
        recording = false
        try { clip = recorder.stop() }
        catch (_: Exception) { recorder.discard(); clip = null; error = "录音太短或没有录到声音，请按住麦克风再说一次" }
    }
    fun transcribe() {
        val audio = clip ?: return
        val config = activeSettings ?: return
        val token = generation
        transcribing = true; showResult = true; error = null
        job = scope.launch {
            try {
                val text = MediaAiClient.transcribe(config, audio)
                if (token == generation) {
                    transcript = text
                    recorder.discard(); clip = null
                    updateValue(appendVoiceInput(currentValue, text, maxLength))
                    showResult = false; transcript = ""; activeSettings = null
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (token == generation) error = failure.message ?: "转写失败，请重试" }
            finally { if (token == generation) transcribing = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Permission dialogs consume the original gesture. A fresh press is always required.
        Toast.makeText(context, if (granted) "麦克风已授权，请重新按住说话" else "麦克风权限未授权，可在系统设置中开启", Toast.LENGTH_SHORT).show()
    }
    fun begin(): Boolean {
        if (!enabled || held || transcribing || showResult || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        val ready = settings.speechBaseUrl.isNotBlank() && settings.speechApiKey.isNotBlank() && settings.speechModel.isNotBlank()
        if (!ready) { error = "请先在 我的 → AI 服务 → 语音转文字 中填写独立语音 API"; showResult = true; return false }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO); return false
        }
        discard(); error = null; transcript = ""; seconds = 0
        activeSettings = settings
        val glmAsr = GlmSpeechInput.matches(settings.speechModel)
        limit = if (glmAsr) GlmSpeechInput.MAX_SECONDS else 60
        try {
            recorder.start(wav = glmAsr, maxSeconds = limit)
            recording = true; held = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            return true
        } catch (_: Exception) { discard(); error = "无法启动麦克风，请检查权限或是否被其他应用占用"; showResult = true; return false }
    }
    fun finish(released: Boolean) {
        if (!held) return
        held = false
        if (!released || !enabled || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { discard(); return }
        stopRecording()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (clip != null) transcribe() else showResult = true
    }
    val beginLatest by rememberUpdatedState({ begin() })
    val finishLatest by rememberUpdatedState({ released: Boolean -> finish(released) })
    val closeLatest by rememberUpdatedState({ close() })
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) closeLatest() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); closeLatest() }
    }
    LaunchedEffect(recording) {
        if (recording) {
            val started = SystemClock.elapsedRealtime()
            while (recording && seconds < limit) {
                delay(100)
                seconds = ((SystemClock.elapsedRealtime() - started) / 1000).toInt().coerceAtMost(limit)
            }
            if (recording) stopRecording() // At the limit, wait for release rather than uploading unexpectedly.
        }
    }
    LaunchedEffect(enabled) { if (!enabled) close() }

    Box(Modifier.size(48.dp)
        .clip(CircleShape)
        .background(if (held) MaterialTheme.colorScheme.errorContainer else androidx.compose.ui.graphics.Color.Transparent)
        .pointerInput(enabled) {
            detectTapGestures(onPress = {
                if (beginLatest()) {
                    var released = false
                    try { released = tryAwaitRelease() }
                    finally { finishLatest(released) }
                }
            })
        }
        .onKeyEvent {
            if (it.key == Key.Enter || it.key == Key.Spacebar || it.key == Key.DirectionCenter) {
                if (it.type == KeyEventType.KeyDown) beginLatest() else if (it.type == KeyEventType.KeyUp) finishLatest(true)
                true
            } else false
        }
        .semantics {
            role = Role.Button
            contentDescription = "语音输入，按住说话，松开转文字"
            stateDescription = if (held) "录音 $seconds 秒" else if (transcribing) "正在转文字" else "仅使用独立语音 API"
            if (!enabled) disabled()
            // TalkBack can start and stop with its standard activation gesture.
            onClick(label = if (held) "结束录音并转文字" else "开始录音") {
                if (held) finishLatest(true) else beginLatest()
                enabled
            }
        }.focusable(enabled), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Mic, null, tint = if (held) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f))
        if (held) {
            val offset = with(LocalDensity.current) { (-90).dp.roundToPx() }
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, offset), properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Surface(shape = MaterialTheme.shapes.large, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (recording) "正在录音 · $seconds / $limit 秒" else "已达 $limit 秒，松开转文字", style = MaterialTheme.typography.titleSmall)
                        Text("松开转文字 · 移出按钮取消", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (showResult) {
        AlertDialog(onDismissRequest = { close() }, title = { Text(if (transcribing) "正在转文字" else "语音输入") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (transcribing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("通过独立语音 API 转写，完成后追加到输入框，不会自动发送给 AI。", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (transcript.isNotBlank()) OutlinedTextField(transcript, { transcript = it }, label = { Text("识别结果（可精简后追加）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (!transcribing && clip != null) TextButton(onClick = { transcribe() }) { Text("重试转写") }
            }
        }, confirmButton = {
            if (!transcribing && transcript.isNotBlank()) TextButton(onClick = {
                try { updateValue(appendVoiceInput(currentValue, transcript, maxLength)); close() }
                catch (failure: IllegalArgumentException) { error = failure.message }
            }) { Text("追加到输入框") }
        }, dismissButton = { TextButton(onClick = { close() }) { Text(if (transcribing) "取消" else "关闭") } })
    }
}
