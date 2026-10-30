package com.workhub.app.ui.files

import androidx.navigation.NavHostController
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.WindowInsets
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workhub.app.data.AppRepository
import com.workhub.app.data.FileRecordEntity
import com.workhub.app.files.FileStore
import com.workhub.app.ui.EmptyHint
import com.workhub.app.ui.Format
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(repo: AppRepository, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files by repo.allFiles.collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingDelete by remember { mutableStateOf<FileRecordEntity?>(null) }
    var importing by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                try {
                    val record = FileStore.import(context, uri)
                    if (record != null) {
                        repo.saveFile(record)
                        Toast.makeText(context, "已导入：${record.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "导入失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    importing = false
                }
            }
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("文件资料") }, windowInsets = WindowInsets(0, 0, 0, 0), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.UploadFile, contentDescription = "导入文件")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "文件保存在手机本地（不上传网络），导入后可随时打开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (files.isEmpty() && !importing) {
                    item { EmptyHint("还没有文件，点右下角导入", emoji = "📁") }
                }
                if (importing) {
                    item { EmptyHint("正在导入…") }
                }
                items(files, key = { it.id }) { f ->
                    FileCard(
                        record = f,
                        onOpen = { openFile(context, f) },
                        onDelete = { pendingDelete = f }
                    )
                }
            }
        }
    }

    pendingDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除文件") },
            text = { Text("确定要删除「${f.name}」吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        FileStore.deleteFile(File(f.path))
                        repo.deleteFile(f)
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FileCard(
    record: FileRecordEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                fileIcon(record.mimeType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    record.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${Format.sizeText(record.size)} · ${Format.dateTimeText(record.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun fileIcon(mime: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Filled.Image
    mime.startsWith("audio/") -> Icons.Filled.MusicNote
    mime.startsWith("video/") -> Icons.Filled.Movie
    mime.startsWith("text/") || mime == "application/pdf" -> Icons.Filled.Description
    else -> Icons.Filled.InsertDriveFile
}

private fun openFile(context: android.content.Context, record: FileRecordEntity) {
    val file = File(record.path)
    if (!file.exists()) {
        Toast.makeText(context, "文件已不存在", Toast.LENGTH_SHORT).show()
        return
    }
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, record.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开 ${record.name}"))
    }.onFailure {
        Toast.makeText(context, "没有能打开该文件的应用", Toast.LENGTH_SHORT).show()
    }
}
