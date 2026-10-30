package com.workhub.app.ui.backup

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.workhub.app.data.AppRepository
import com.workhub.app.data.AppSnapshot
import com.workhub.app.files.BackupManager
import com.workhub.app.files.FileStore
import com.workhub.app.notify.ReminderScheduler
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.ui.Format
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    repo: AppRepository,
    settingsRepository: SettingsRepository,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backups by remember { mutableStateOf<List<File>>(emptyList()) }
    var autoBackups by remember { mutableStateOf<List<File>>(emptyList()) }
    var working by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    fun reloadBackups() {
        scope.launch {
            withContext(Dispatchers.IO) {
                backups = BackupManager.backupsDir(context).listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".dwb")) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                autoBackups = BackupManager.autoBackupsDir(context).listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".dwb")) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                working = true
                try {
                    val file = copyToBackups(context, uri)
                    val snapshot = BackupManager.parse(context, file)
                    repo.restoreSnapshot(snapshot)
                    snapshot.settings?.let {
                        settingsRepository.restoreBackupSettings(it)
                        if (it.checkinRemindEnabled) ReminderScheduler.scheduleCheckinReminder(context, it.checkinRemindHour)
                        else ReminderScheduler.cancelCheckinReminder(context)
                    }
                    Toast.makeText(context, "恢复成功：${snapshot.tasks.size} 任务 / ${snapshot.notes.size} 笔记", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ 恢复失败：${e.message?.take(80)}", Toast.LENGTH_LONG).show()
                } finally {
                    working = false
                    reloadBackups()
                }
            }
        }
    }

    fun doExport() {
        scope.launch {
            working = true
            try {
                val settings = settingsRepository.exportBackupSettings()
                val file = BackupManager.export(context, repo.exportSnapshot().copy(settings = settings))
                shareFile(context, file)
                Toast.makeText(context, "已导出备份文件", Toast.LENGTH_SHORT).show()
                reloadBackups()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ 导出失败：${e.message?.take(80)}", Toast.LENGTH_LONG).show()
            } finally {
                working = false
            }
        }
    }

    fun doRestore(file: File) {
        scope.launch {
            working = true
            try {
                val snapshot = BackupManager.parse(context, file)
                repo.restoreSnapshot(snapshot)
                snapshot.settings?.let {
                    settingsRepository.restoreBackupSettings(it)
                    if (it.checkinRemindEnabled) ReminderScheduler.scheduleCheckinReminder(context, it.checkinRemindHour)
                    else ReminderScheduler.cancelCheckinReminder(context)
                }
                Toast.makeText(context, "已从「${file.name}」恢复数据", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ 恢复失败：${e.message?.take(80)}", Toast.LENGTH_LONG).show()
            } finally {
                working = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("数据备份") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "训练数据默认只保存在本机，不参与 Android 云备份。换机或卸载前，请手动导出加密密钥之外的完整备份。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("导出备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "将训练、身体、计划、记录、资料和偏好打包为一个 .dwb 文件；出于安全考虑，API Key 不会导出。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { doExport() },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Text("  导出并分享", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("恢复数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "支持 .dwb 与旧版 JSON。恢复会覆盖当前业务数据，但保留本机已加密的 API Key。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "*/*")) },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Text("  选择备份文件恢复", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            item {
                Text(
                    "自动备份（保留最近 5 份）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (autoBackups.isEmpty()) {
                item {
                    Text(
                        "还没有自动备份：App 每次退到后台会静默备份一份全量数据，无需手动操作",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(autoBackups, key = { "auto_" + it.absolutePath }) { file ->
                BackupFileRow(
                    file = file,
                    onShare = { shareFile(context, file) },
                    onRestore = {
                        selectedFile = file
                        showRestoreConfirm = true
                    },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { file.delete() }
                            reloadBackups()
                        }
                    }
                )
            }
            item {
                Text(
                    "本机备份记录（${backups.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (backups.isEmpty()) {
                item {
                    Text(
                        "还没有备份文件，点上面「导出并分享」创建第一个备份",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(backups, key = { it.absolutePath }) { file ->
                BackupFileRow(
                    file = file,
                    onShare = { shareFile(context, file) },
                    onRestore = {
                        selectedFile = file
                        showRestoreConfirm = true
                    },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { file.delete() }
                            reloadBackups()
                        }
                    }
                )
            }
        }
    }

    if (showRestoreConfirm && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("恢复数据") },
            text = { Text("从「${selectedFile!!.name}」恢复？\n\n恢复将覆盖当前全部数据，建议先导出当前数据。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    doRestore(selectedFile!!)
                }) { Text("恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BackupFileRow(
    file: File,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    Format.dateTimeText(file.lastModified()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "分享")
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.Restore, contentDescription = "恢复")
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

private suspend fun copyToBackups(context: android.content.Context, uri: Uri): File {
    val name = "workhub_恢复_${System.currentTimeMillis()}.dwb"
    val target = File(BackupManager.backupsDir(context), name)
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IllegalStateException("无法读取所选文件")
    }
    return target
}

private fun shareFile(context: android.content.Context, file: File) {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType(if (file.extension == "dwb") "application/zip" else "application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享备份文件"))
    }.onFailure {
        Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
    }
}
