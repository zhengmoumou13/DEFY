package com.workhub.app.ui.gym.media

import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workhub.app.data.AppRepository
import com.workhub.app.gym.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExerciseMedia(exercise: GymExercise, repo: AppRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val media by repo.db.gymDao().observeMedia(exercise.id).collectAsStateWithLifecycle(null)
    val files by repo.allFiles.collectAsStateWithLifecycle(emptyList())
    val file = files.firstOrNull { it.id == media?.fileId }
    val original = OriginalDemo.forExercise(exercise)
    var showOriginal by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attribution by rememberSaveable(exercise.id) { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            var copied: com.workhub.app.data.FileRecordEntity? = null
            var saved = false
            try {
                // Finish the bounded import and association even if its dialog is dismissed.
                withContext(NonCancellable) {
                    copied = GymMediaImport.copy(context, uri, exercise.name)
                    repo.saveGymMedia(exercise.id, copied!!, attribution)
                    saved = true
                }
                showOriginal = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message ?: "导入失败，请换一个本机可播放的视频。"
            } finally {
                if (!saved) copied?.let { File(it.path).delete() }
                busy = false
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            file != null && !showOriginal -> {
                Text("本地演示 · ${media?.attribution.orEmpty()}", style = MaterialTheme.typography.labelLarge)
                key(file.path) { LocalVideoPlayer(file.path) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (original != null) TextButton(onClick = { showOriginal = true }) { Text("看原创示意") }
                    TextButton(onClick = { scope.launch { repo.db.gymDao().unlinkMedia(exercise.id) } }) { Text("解除关联") }
                }
            }
            original != null -> {
                key(exercise.id) { OriginalDemoPlayer(original) }
                if (file != null) TextButton(onClick = { showOriginal = false }) { Text("看本地视频") }
            }
            else -> Text("此动作暂无内置示意，可添加自己的演示视频。", style = MaterialTheme.typography.bodySmall)
        }
        if (media != null && file == null) Text("关联视频已被移除，可重新导入。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !busy, onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "正在保存视频…" else if (file == null) "添加自己的演示视频" else "更换演示视频")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (showImport) AlertDialog(onDismissRequest = { showImport = false }, title = { Text("导入动作演示") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("选择你拍摄或已获授权的视频（不超过 100 MB）。视频保存在本机，并随数据备份导出。")
            OutlinedTextField(attribution, { attribution = it }, label = { Text("作者 / 来源 / 许可备注（可选）") }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = { showImport = false; picker.launch(arrayOf("video/*")) }) { Text("选择视频") } },
        dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } })
}

@Composable
private fun LocalVideoPlayer(path: String) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { view?.pause(); playing = false }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); view?.stopPlayback() }
    }
    Column {
        AndroidView(factory = { context -> VideoView(context).apply {
            view = this
            setOnPreparedListener { ready = true; seekTo(1) }
            setOnCompletionListener { playing = false }
            setOnErrorListener { _, _, _ -> error = "该视频无法播放，请更换为设备支持的 MP4 视频。"; ready = false; playing = false; true }
            setVideoPath(path)
        } }, modifier = Modifier.fillMaxWidth().height(220.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = ready, onClick = { if (playing) view?.pause() else view?.start(); playing = !playing }) { Text(if (playing) "暂停视频" else "播放视频") }
            TextButton(enabled = ready, onClick = { view?.seekTo(0); view?.pause(); playing = false }) { Text("回到开头") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
