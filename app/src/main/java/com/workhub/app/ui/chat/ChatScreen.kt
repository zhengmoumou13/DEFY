package com.workhub.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.ai.AiClient
import com.workhub.app.ai.AiPrompts
import com.workhub.app.ai.ChatMessage
import com.workhub.app.data.AppRepository
import com.workhub.app.data.ChatMessageEntity
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.settings.UserSettings
import com.workhub.app.ui.media.VoiceInputButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedButton

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val scope = rememberCoroutineScope()
    val messages by repo.allChatMessages.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())

    var input by rememberSaveable { mutableStateOf("") }
    var waiting by remember { mutableStateOf(false) }
    var errorBubble by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var requestJob by remember { mutableStateOf<Job?>(null) }
    var retryAvailable by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val totalItems = messages.size + (if (waiting || errorBubble != null) 1 else 0)

    LaunchedEffect(totalItems) {
        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
    }

    fun submit(raw: String, retry: Boolean = false) {
        val text = raw.trim()
        if (waiting || (!retry && text.isEmpty())) return
        if (settings.apiKey.isBlank()) {
            if (!retry) input = raw
            errorBubble = "请先配置文字 AI，输入内容已保留。"
            return
        }
        // Lock before the first suspension, so rapid taps cannot save/send twice.
        waiting = true
        errorBubble = null
        requestJob = scope.launch {
            try {
                if (!retry) {
                    retryAvailable = false
                    repo.saveChatMessage(ChatMessageEntity(role = "user", content = text))
                    input = ""
                }
                retryAvailable = true
                val history = repo.getChatMessages().takeLast(20).map { ChatMessage(it.role, it.content) }
                val reply = AiClient.chat(
                    baseUrl = settings.apiBaseUrl, apiKey = settings.apiKey, model = settings.model,
                    messages = history, systemPrompt = AiPrompts.chatSystemPrompt()
                )
                repo.saveChatMessage(ChatMessageEntity(role = "assistant", content = reply))
                retryAvailable = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                errorBubble = e.message ?: "发送失败，请重试"
            } finally {
                waiting = false
            }
        }
    }
    val doSend: (String) -> Unit = { submit(it) }

    val send: () -> Unit = { doSend(input) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("扣篮教练") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (messages.isNotEmpty() && !waiting) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空对话")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !waiting,
                    placeholder = { Text("描述训练目标、器械、时间或身体状态…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                VoiceInputButton(input, { input = it }, enabled = !waiting, maxLength = 4000)
                IconButton(
                    onClick = send,
                    enabled = input.isNotBlank() && !waiting,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "发送",
                        tint = if (input.isNotBlank() && !waiting) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && !waiting && errorBubble == null) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🤖", style = MaterialTheme.typography.displayMedium)
                            Text(
                                "我是你的扣篮训练教练",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                "告诉我你的训练经验、器械、可用时间和膝踝状态，建议会更准确",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )
                            val suggestions = listOf(
                                "我每周能练 3 天，怎么安排弹跳训练？",
                                "今天只有 45 分钟，训练怎么精简？",
                                "膝盖有点不舒服，今天该怎么降级？",
                                "我的助跑摸高数据说明了什么？",
                                "扣篮训练前怎么热身？",
                                "训练后怎么吃更利于恢复？"
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                suggestions.forEach { q ->
                                    androidx.compose.material3.FilterChip(
                                        selected = false,
                                        onClick = { doSend(q) },
                                        label = { Text(q, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    text = msg.content,
                    isUser = msg.role == "user"
                )
            }
            if (waiting) {
                item {
                    TextButton(onClick = {
                        requestJob?.cancel()
                        errorBubble = "已停止生成，问题已保留，可重试。"
                    }) { Text("停止生成") }
                    MessageBubble(
                        text = "正在思考…",
                        isUser = false,
                        isTyping = true
                    )
                }
            }
            errorBubble?.let {
                item {
                    MessageBubble(text = it, isUser = false, isError = true)
                    if (settings.apiKey.isBlank()) {
                        OutlinedButton(onClick = { navController.navigate("media_settings?mode=text") }) { Text("配置文字 AI") }
                    } else if (retryAvailable) {
                        OutlinedButton(onClick = { submit("", retry = true) }, enabled = !waiting) { Text("重试上一条") }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空对话") },
            text = { Text("确定要清空全部对话记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        repo.clearChat()
                        errorBubble = null
                        retryAvailable = false
                    }
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MessageBubble(
    text: String,
    isUser: Boolean,
    isTyping: Boolean = false,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🤖", style = MaterialTheme.typography.labelMedium)
            }
        }
        val bubbleColor = when {
            isUser -> MaterialTheme.colorScheme.primary
            isError -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val textColor = when {
            isUser -> MaterialTheme.colorScheme.onPrimary
            isError -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(bubbleColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (isTyping) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        " AI 正在思考…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            } else if (isUser || isError) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor)
            } else {
                // AI 回复：Markdown 渲染（表格/列表/加粗等）
                com.workhub.app.ui.markdown.Markdown.Render(text)
            }
        }
    }
}
