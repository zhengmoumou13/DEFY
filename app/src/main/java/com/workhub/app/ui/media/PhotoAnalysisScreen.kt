package com.workhub.app.ui.media

import android.app.Application
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.workhub.app.ai.MediaAiClient
import com.workhub.app.ai.PhotoAnalysis
import com.workhub.app.ai.PhotoScene
import com.workhub.app.data.AiPlanEntity
import com.workhub.app.data.AppRepository
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.ui.ball.BallPanel
import com.workhub.app.ui.ball.ChoiceRow
import com.workhub.app.ui.markdown.Markdown
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoAnalysisScreen(repo: AppRepository, settingsRepository: SettingsRepository, navController: NavHostController, sceneKey: String) {
    val context = LocalContext.current
    val scene = PhotoScene.fromKey(sceneKey)
    val factory = remember(repo, scene) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PhotoAnalysisViewModel(context.applicationContext as Application, repo, settingsRepository, scene) as T
        }
    }
    val model: PhotoAnalysisViewModel = viewModel(key = scene.key, factory = factory)
    val state = model.state
    val recordsFlow = remember(repo, scene) { repo.observePlansByType(scene.recordType) }
    val loadedRecords by recordsFlow.collectAsStateWithLifecycle(initialValue = null)
    val records = loadedRecords.orEmpty()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val ready = settings.visionApiKey.isNotBlank() && settings.visionBaseUrl.isNotBlank() && settings.visionModel.isNotBlank()
    val host = remember(settings.visionBaseUrl) { runCatching { MediaAiClient.endpoint(settings.visionBaseUrl, "chat/completions").host }.getOrDefault("未配置") }
    var showComposer by rememberSaveable { mutableStateOf(true) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AiPlanEntity?>(null) }
    val scroll = rememberLazyListState()
    val selected = records.firstOrNull { it.id == state.selectedId } ?: records.firstOrNull()
    val savedReport = remember(selected?.content) { selected?.content?.let(PhotoAnalysis::decode) }
    val report = state.pendingReport ?: savedReport
    val back: () -> Unit = {
        if (state.busy || state.photos.isNotEmpty() || state.notes.isNotBlank() || state.pendingReport != null) showLeave = true
        else navController.popBackStack()
    }
    BackHandler(onBack = back)
    LaunchedEffect(state.selectedId) {
        if (state.selectedId > 0) { showComposer = false; scroll.scrollToItem(0) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris -> model.addPhotos(uris) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> model.finishCamera(success) }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text(scene.title) }, windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } })
    }) { padding ->
        if (loadedRecords == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                BallPanel {
                    Text(if (scene == PhotoScene.TRAINING) "让照片成为技术复盘的起点" else "看这一餐，也记录饮食选择", style = MaterialTheme.typography.titleMedium)
                    Text(if (scene == PhotoScene.TRAINING) "可上传1–3张动作照片或关键帧。静态图片只能观察可见姿态，不能验证完整动作、精确测量或诊断伤病。"
                        else "上传同一餐的1–3张照片，补充份量和实际吃了多少。营养仅作范围估计，一餐不能代表长期习惯。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showComposer = !showComposer }, enabled = !state.busy) { Text(if (showComposer) "收起上传区域" else "上传照片 / 开始新分析") }
                }
            }
            if (showComposer) {
                item {
                    BallPanel {
                        Text("照片 ${state.photos.size}/3 · 按上传顺序编号", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                catch (_: Exception) { model.error("无法打开照片选择器，请检查系统相册是否可用") }
                            }, enabled = !state.busy && state.photos.size < 3, modifier = Modifier.weight(1f)) { Text("选择手机相册") }
                            OutlinedButton(onClick = {
                                try {
                                    val file = model.prepareCamera()
                                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                                } catch (_: Exception) { model.finishCamera(false); model.error("相机不可用，可先拍照再从相册选择") }
                            }, enabled = !state.busy && state.photos.size < 3, modifier = Modifier.weight(1f)) { Text("打开手机相机") }
                        }
                        Text("上传前在本机缩小尺寸、校正方向并去掉定位等元数据。不保存原图到分析历史；请避免拍到旁人、证件和隐私。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.importing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                items(state.photos, key = { it.path }) { photo ->
                    BallPanel {
                        PhotoPreview(photo, state.photos.indexOf(photo) + 1)
                        TextButton(onClick = { model.remove(photo) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("移除这张图片") }
                    }
                }
                item {
                    BallPanel {
                        if (scene == PhotoScene.MEAL) {
                            ChoiceRow(listOf("早餐", "午餐", "晚餐", "加餐").map { it to it }, state.meal, model::meal)
                        }
                        OutlinedTextField(
                            value = state.notes, onValueChange = model::notes, enabled = !state.busy,
                            label = { Text(if (scene == PhotoScene.TRAINING) "动作、阶段与想改进的问题" else "份量、做法与实际吃了多少") },
                            placeholder = { Text(if (scene == PhotoScene.TRAINING) "例如：同一次急停跳投，图1启动、图2出手；想看脚步是否稳定。" else "例如：午餐，一碗米饭、鸡胸约150g，吃了八成；下午有训练。") },
                            minLines = 3, supportingText = { Text("${state.notes.length}/1500") },
                            trailingIcon = { VoiceInputButton(state.notes, model::notes, enabled = !state.busy) }, modifier = Modifier.fillMaxWidth()
                        )
                        if (scene == PhotoScene.MEAL) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(state.includeHistory, model::includeHistory, enabled = !state.busy)
                                Text("附带最近7天最多7条已保存餐食分析，观察记录中的饮食习惯线索", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                        Text("点击分析即同意将选中照片、备注和训练档案发送至 $host。${if (state.includeHistory) "另附上述餐食历史（含可能不准确的AI估计）。" else "不会附带其他历史记录。"}服务可能计费。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { navController.navigate("media_settings?mode=vision") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (ready) "查看 / 修改图片服务" else "配置图片分析服务") }
                        Button(onClick = model::generate, enabled = ready && state.photos.isNotEmpty() && !state.busy && state.pendingReport == null, modifier = Modifier.fillMaxWidth()) { Text(if (state.analyzing) "正在分析…" else "分析并保存结果") }
                    }
                }
            }
            if (state.analyzing) {
                item {
                    BallPanel {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在分析，完成后保存文字报告。请勿把 AI 估计当作测量或诊断。")
                        TextButton(onClick = model::cancelAnalysis, enabled = !state.saving) { Text("取消本次分析") }
                    }
                }
            }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (records.isNotEmpty()) {
                item {
                    TextButton(onClick = { showHistory = !showHistory }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (showHistory) "收起历史分析" else "历史分析（${records.size}）· 离线可查看") }
                }
                if (showHistory) {
                    items(records, key = { "report_${it.id}" }) { record ->
                        val previous = remember(record.content) { PhotoAnalysis.decode(record.content) }
                        OutlinedButton(onClick = { model.select(record.id); showHistory = false }, enabled = !state.busy && state.pendingReport == null, modifier = Modifier.fillMaxWidth()) {
                            Text("${record.date} · ${previous?.meal?.takeIf { it.isNotBlank() } ?: scene.title} · ${previous?.context?.take(32)?.ifBlank { "照片分析" } ?: "历史记录"}")
                        }
                    }
                }
            }
            if (report != null) {
                item {
                    BallPanel {
                        Text(if (state.pendingReport != null) "分析完成 · 尚未保存" else "${selected?.date.orEmpty()} · ${report.meal.ifBlank { scene.title }}", style = MaterialTheme.typography.titleMedium)
                        Text("${report.photoCount} 张照片 · ${report.model} · 仅保留文字报告", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (report.context.isNotBlank()) Text("当时的备注：${report.context}", style = MaterialTheme.typography.bodyMedium)
                        Markdown.Render(report.analysis)
                        Text("AI 分析可能有误，实际训练以身体反馈和专业指导为准；营养估算需用真实份量核对。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.pendingReport != null) {
                            Button(onClick = model::retrySave, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("重试保存（不再请求 AI）") }
                            TextButton(onClick = model::discardPending, enabled = !state.busy) { Text("丢弃未保存结果") }
                        } else {
                            TextButton(onClick = { pendingDelete = selected }, enabled = !state.busy) { Text("删除这份分析", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            } else if (selected != null) {
                item { BallPanel { Text("这份历史分析格式暂不可读，原始记录仍保留在备份中。") } }
            }
        }
    }
    if (showLeave) {
        AlertDialog(onDismissRequest = { showLeave = false }, title = { Text("离开照片分析？") },
            text = { Text("离开会取消未完成请求并删除临时照片、未保存的输入和结果。已保存的文字分析不会丢失。") },
            confirmButton = { TextButton(onClick = { showLeave = false; navController.popBackStack() }, enabled = !state.saving) { Text("离开") } },
            dismissButton = { TextButton(onClick = { showLeave = false }) { Text("继续查看") } })
    }
    pendingDelete?.let { record ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("删除这份分析？") },
            text = { Text("此操作删除本机这份文字报告，不删除相册照片。已有备份中的报告仍会保留。") },
            confirmButton = { TextButton(onClick = { model.delete(record); pendingDelete = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } })
    }
}

@Composable
private fun PhotoPreview(file: File, number: Int) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    }
    Text("图片 $number", style = MaterialTheme.typography.labelLarge)
    bitmap?.let { Image(it, "待分析图片 $number", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(210.dp)) }
}
