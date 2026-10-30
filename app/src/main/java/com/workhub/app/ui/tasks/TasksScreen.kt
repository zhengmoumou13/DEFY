package com.workhub.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.TaskEntity
import com.workhub.app.ui.EmptyHint
import com.workhub.app.ui.Format
import com.workhub.app.ui.theme.PriorityHigh
import com.workhub.app.ui.theme.PriorityMid
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(repo: AppRepository, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val all by repo.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableIntStateOf(0) } // 0全部 1待办 2进行中 3已完成
    var query by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    val base = when (filter) {
        1 -> all.filter { it.status == 0 }
        2 -> all.filter { it.status == 1 }
        3 -> all.filter { it.status == 2 }
        else -> all
    }
    val filtered = if (query.isBlank()) base else {
        val q = query.trim().lowercase()
        base.filter {
            it.title.lowercase().contains(q) ||
                it.detail.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("任务") }, windowInsets = WindowInsets(0, 0, 0, 0), navigationIcon = { androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("task_edit?taskId=-1") }) {
                Icon(Icons.Filled.Add, contentDescription = "新建任务")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索任务标题、备注…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("全部", "待办", "进行中", "已完成").forEachIndexed { i, label ->
                    FilterChip(
                        selected = filter == i,
                        onClick = { filter = i },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtered.isEmpty()) {
                    item { EmptyHint("还没有任务，点右下角 + 新建", emoji = "🗒️") }
                }
                items(filtered, key = { it.id }) { task ->
                    val currentTask by rememberUpdatedState(task)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                // 右滑：完成 / 取消完成（不离开列表）
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    scope.launch {
                                        repo.updateTask(
                                            currentTask.copy(status = if (currentTask.status == 2) 0 else 2)
                                        )
                                    }
                                    false
                                }
                                // 左滑：删除，支持 Snackbar 撤销
                                SwipeToDismissBoxValue.EndToStart -> {
                                    val t = currentTask
                                    scope.launch {
                                        repo.deleteTask(t)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "已删除「${t.title}」",
                                            actionLabel = "撤销",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) repo.saveTask(t)
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.animateItem(),
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            // 右滑显示“完成”，左滑显示“删除”
                            val completing =
                                dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                            val container =
                                if (completing) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            val iconColor =
                                if (completing) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(container, MaterialTheme.shapes.medium),
                                contentAlignment = if (completing) Alignment.CenterStart
                                else Alignment.CenterEnd
                            ) {
                                Icon(
                                    if (completing) Icons.Filled.Check else Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = iconColor
                                )
                            }
                        }
                    ) {
                        TaskCard(
                            task = task,
                            onClick = { navController.navigate("task_edit?taskId=${task.id}") },
                            onToggle = {
                                scope.launch {
                                    repo.updateTask(task.copy(status = if (task.status == 2) 0 else 2))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDone = task.status == 2
    val isOverdue = !isDone && task.dueDate != null && task.dueDate!! < System.currentTimeMillis()
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isDone, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(8.dp)
                            .background(
                                when (task.priority) {
                                    2 -> PriorityHigh
                                    1 -> PriorityMid
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                                CircleShape
                            )
                    )
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (task.priority == 2) FontWeight.SemiBold else FontWeight.Normal,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                task.dueDate?.let {
                    Text(
                        Format.dueText(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) PriorityHigh else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
