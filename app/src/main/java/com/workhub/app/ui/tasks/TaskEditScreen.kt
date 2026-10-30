package com.workhub.app.ui.tasks

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.TaskEntity
import com.workhub.app.ui.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    repo: AppRepository,
    settingsRepository: com.workhub.app.settings.SettingsRepository,
    navController: NavHostController,
    taskId: Long
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.workhub.app.settings.UserSettings()
    )

    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(0) }
    var priority by remember { mutableStateOf(1) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var repeatType by remember { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // AI 拆解
    var showBreakdown by remember { mutableStateOf(false) }
    var breakdownLoading by remember { mutableStateOf(false) }
    var breakdownError by remember { mutableStateOf<String?>(null) }
    var breakdownResult by remember { mutableStateOf<String?>(null) }

    val doBreakdown = {
        val t = title.trim().ifBlank { "未命名任务" }
        scope.launch {
            breakdownLoading = true
            breakdownError = null
            try {
                if (settings.apiKey.isBlank()) {
                    throw IllegalStateException("尚未配置文字 AI，请到“我的 → 文字 AI”填写并测试连接")
                }
                breakdownResult = com.workhub.app.ai.AiClient.chatSingle(
                    settings.apiBaseUrl, settings.apiKey, settings.model,
                    com.workhub.app.ai.AiPrompts.taskBreakdown(t, detail.trim())
                )
            } catch (e: Exception) {
                breakdownError = e.message ?: "生成失败，请重试"
            } finally {
                breakdownLoading = false
            }
        }
    }

    LaunchedEffect(taskId) {
        if (taskId > 0) {
            repo.getTask(taskId)?.let { t ->
                title = t.title
                detail = t.detail
                status = t.status
                priority = t.priority
                dueDate = t.dueDate
                repeatType = t.repeatType
            }
        }
    }

    val save: () -> Unit = {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(context, "请填写任务标题", Toast.LENGTH_SHORT).show()
        } else {
            val finalDue = dueDate ?: if (repeatType > 0) {
                java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
            } else null
            scope.launch {
                if (taskId > 0) {
                    repo.getTask(taskId)?.let {
                        repo.updateTask(
                            it.copy(
                                title = trimmed,
                                detail = detail.trim(),
                                status = status,
                                priority = priority,
                                dueDate = finalDue,
                                repeatType = repeatType
                            )
                        )
                    }
                } else {
                    repo.saveTask(
                        TaskEntity(
                            title = trimmed,
                            detail = detail.trim(),
                            status = status,
                            priority = priority,
                            dueDate = finalDue,
                            repeatType = repeatType
                        )
                    )
                }
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (taskId > 0) "编辑任务" else "新建任务") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (taskId > 0) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                    TextButton(onClick = save) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("任务标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedButton(
                onClick = { showBreakdown = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🤖 AI 拆解：把任务拆成可执行步骤")
            }

            Text("状态", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("待办", "进行中", "已完成").forEachIndexed { i, label ->
                    FilterChip(
                        selected = status == i,
                        onClick = { status = i },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("优先级", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("低", "中", "高").forEachIndexed { i, label ->
                    FilterChip(
                        selected = priority == i,
                        onClick = { priority = i },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider()

            Text("重复", style = MaterialTheme.typography.titleSmall)
            listOf(
                listOf(0 to "不重复", 1 to "每天"),
                listOf(2 to "每周", 3 to "每月")
            ).forEach { options ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (i, label) ->
                        FilterChip(
                            selected = repeatType == i,
                            onClick = { repeatType = i },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (repeatType > 0 && dueDate == null) {
                Text(
                    "重复任务保存时会自动设置截止时间为今天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("截止时间", style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(dueDate?.let { Format.dueText(it) } ?: "设置截止时间")
                }
                if (dueDate != null) {
                    TextButton(onClick = { dueDate = null }) { Text("清除") }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        dueDate = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除任务") },
            text = { Text("确定要删除这个任务吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        repo.getTask(taskId)?.let { repo.deleteTask(it) }
                        navController.popBackStack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showBreakdown) {
        AlertDialog(
            onDismissRequest = { showBreakdown = false },
            title = { Text("🤖 AI 任务拆解") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "任务：${title.trim().ifBlank { "未命名任务" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when {
                        breakdownLoading -> {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
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
                            com.workhub.app.ui.markdown.Markdown.Render(breakdownResult!!)
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
