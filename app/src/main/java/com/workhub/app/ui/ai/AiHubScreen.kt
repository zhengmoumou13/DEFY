package com.workhub.app.ui.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.ai.AiClient
import com.workhub.app.ai.AiPrompts
import com.workhub.app.data.AppRepository
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.ui.markdown.Markdown
import kotlinx.coroutines.launch

private data class AiScene(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val route: String? = null
)

private val CORE_SCENES = listOf(
    AiScene("问扣篮教练", "动作、技术、负荷与恢复", Icons.Filled.ChatBubbleOutline, Color(0xFF315D6B), "chat"),
    AiScene("今日弹跳训练", "主目标、组次、强度与休息", Icons.Filled.FitnessCenter, Color(0xFFFF5A1F), "fitness"),
    AiScene("今日饮食恢复", "训练前后补给与日常饮食", Icons.Filled.Restaurant, Color(0xFF178066), "diet"),
    AiScene("训练周期复盘", "打卡、负荷与下周调整", Icons.Filled.Assessment, Color(0xFFD94124), "checkin_report"),
    AiScene("动作照片分析", "观察姿态、技术要点与补拍建议", Icons.Filled.PhotoCamera, Color(0xFFFF5A1F), "photo_analysis?scene=training"),
    AiScene("当餐照片分析", "份量估算、营养与饮食记录", Icons.Filled.Restaurant, Color(0xFF178066), "photo_analysis?scene=meal")
)

private val MORE_SCENES = listOf(
    AiScene("今日学习计划", "时间块安排与学习方法", Icons.Filled.MenuBook, Color(0xFF52656D), "study"),
    AiScene("今日执行复盘", "依据真实记录安排明日第一步", Icons.Filled.Today, Color(0xFFFF5A1F), "daily_report"),
    AiScene("工作周报", "用任务与专注数据整理周报", Icons.Filled.TaskAlt, Color(0xFF315D6B), "weekly_report"),
    AiScene("任务拆解", "拆成有产出、可验收的步骤", Icons.Filled.Tune, Color(0xFF4B5255), null)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController,
    showBack: Boolean = true
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())

    var selectedCategory by rememberSaveable { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    var breakdownInput by remember { mutableStateOf("") }
    var breakdownDetail by remember { mutableStateOf("") }
    var breakdownResult by remember { mutableStateOf<String?>(null) }
    var breakdownLoading by remember { mutableStateOf(false) }
    var breakdownError by remember { mutableStateOf<String?>(null) }

    val doBreakdown = {
        val title = breakdownInput.trim()
        if (title.isEmpty()) {
            breakdownError = "请先填写任务名称"
        } else {
            scope.launch {
                breakdownLoading = true
                breakdownError = null
                try {
                    if (settings.apiKey.isBlank()) {
                        throw IllegalStateException("尚未配置文字 AI，请到“我的 → 文字 AI”填写并测试连接")
                    }
                    breakdownResult = AiClient.chatSingle(
                        settings.apiBaseUrl, settings.apiKey, settings.model,
                        AiPrompts.taskBreakdown(title, breakdownDetail.trim())
                    )
                } catch (e: Exception) {
                    breakdownError = e.message ?: "生成失败，请重试"
                } finally {
                    breakdownLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (showBack) "AI 教练中心" else "训练中心") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("练得有方向，也恢复得充分。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("训练", "饮食与恢复", "效率工具").forEachIndexed { index, title ->
                            FilterChip(selected = selectedCategory == index, onClick = { selectedCategory = index }, label = { Text(title) })
                        }
                    }
                }
            }
            if (selectedCategory == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(onClick = { navController.navigate("gym") }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("力量训练 · openGym", style = MaterialTheme.typography.titleMedium)
                            Text("动作库 / 可编辑模板 / 逐组记录", style = MaterialTheme.typography.bodyMedium)
                            Text("打开训练工作台 →", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(onClick = { navController.navigate("fitness") }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("今日弹跳训练", style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("查看计划、记录组次，按自己的节奏完成。", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("打开训练 →", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(onClick = { navController.navigate("ball_training") }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Filled.SportsBasketball, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text("有球训练", style = MaterialTheme.typography.titleMedium)
                                Text("投篮 · 终结 · 运控", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("进入 →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            val scenes = when (selectedCategory) {
                0 -> CORE_SCENES.filter { it.route in listOf("chat", "checkin_report", "photo_analysis?scene=training") }
                1 -> CORE_SCENES.filter { it.route in listOf("diet", "photo_analysis?scene=meal") }
                else -> MORE_SCENES
            }
            items(scenes, key = { it.label }) { scene ->
                SceneCard(scene) {
                    if (scene.route != null) navController.navigate(scene.route) else showBreakdown = true
                }
            }
        }
    }

    if (showBreakdown) {
        AlertDialog(
            onDismissRequest = { showBreakdown = false },
            title = { Text("AI 任务拆解") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = breakdownInput,
                        onValueChange = { breakdownInput = it },
                        label = { Text("任务名称") },
                        placeholder = { Text("如：完成年度述职报告") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = breakdownDetail,
                        onValueChange = { breakdownDetail = it },
                        label = { Text("补充说明（可选）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    when {
                        breakdownLoading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(" AI 正在拆解…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        breakdownError != null -> {
                            Text(
                                breakdownError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        breakdownResult != null -> {
                            Column(
                                Modifier
                                    .verticalScroll(rememberScrollState())
                                    .heightIn(min = 120.dp, max = 520.dp)
                            ) {
                                Markdown.Render(breakdownResult!!)
                            }
                            Row {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(breakdownResult!!))
                                }) { Text("复制") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { doBreakdown() },
                    enabled = !breakdownLoading
                ) {
                    Text(if (breakdownResult == null) "开始拆解" else "重新拆解")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBreakdown = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun SceneCard(scene: AiScene, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 152.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    scene.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp)
                )
            }
            Text(
                scene.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                scene.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
