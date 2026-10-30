package com.workhub.app.files

import android.content.Context
import com.workhub.app.data.AppSnapshot
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 跃过引力 备份文件读写：保存在应用私有目录 workhub/backups/ 下。 */
object BackupManager {

    private const val AUTO_KEEP = 5

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun backupsDir(context: Context): File =
        File(FileStore.dir(context), "backups").apply { mkdirs() }

    fun autoBackupsDir(context: Context): File =
        File(backupsDir(context), "auto").apply { mkdirs() }

    private const val SNAPSHOT_ENTRY = "snapshot.json"

    /** 导出完整备份包：数据库快照 + 文件资料本体。 */
    suspend fun export(context: Context, snapshot: AppSnapshot): File =
        withContext(Dispatchers.IO) {
            val name = "workhub_备份_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                ".dwb"
            val file = File(backupsDir(context), name)
            writeArchive(file, snapshot)
            file
        }

    /**
     * 静默自动备份：写入 auto/ 子目录，只保留最近 [AUTO_KEEP] 份，超出自动滚动删除。
     * 全程静默，失败不抛异常（返回 false 供调用方记录日志）。
     */
    suspend fun autoBackup(context: Context, snapshot: AppSnapshot): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = autoBackupsDir(context)
                val name = "workhub_auto_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                    ".dwb"
                writeArchive(File(dir, name), snapshot)
                dir.listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(".dwb") || it.name.endsWith(".json")) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(AUTO_KEEP)
                    ?.forEach { it.delete() }
                true
            }.getOrDefault(false)
        }

    private fun writeArchive(file: File, snapshot: AppSnapshot) {
        val archivedFiles = snapshot.files.map { record ->
            val source = File(record.path)
            if (source.isFile) {
                record.copy(path = "files/${record.id}_${source.name.replace('/', '_')}")
            } else record
        }
        val archivedSnapshot = snapshot.copy(files = archivedFiles)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(SNAPSHOT_ENTRY))
            zip.write(json.encodeToString(AppSnapshot.serializer(), archivedSnapshot).toByteArray())
            zip.closeEntry()
            archivedFiles.zip(snapshot.files).forEach { (archived, original) ->
                if (!archived.path.startsWith("files/")) return@forEach
                val source = File(original.path)
                if (!source.isFile) return@forEach
                zip.putNextEntry(ZipEntry(archived.path))
                source.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** 解析完整备份包；继续兼容旧版 JSON。 */
    suspend fun parse(context: Context, file: File): AppSnapshot =
        withContext(Dispatchers.IO) {
            if (isZip(file)) parseArchive(context, file)
            else json.decodeFromString(AppSnapshot.serializer(), file.readText())
        }

    private fun isZip(file: File): Boolean = file.inputStream().buffered().use { input ->
        val first = input.read()
        val second = input.read()
        first == 'P'.code && second == 'K'.code
    }

    private fun parseArchive(context: Context, file: File): AppSnapshot {
        ZipFile(file).use { zip ->
            val snapshotEntry = zip.getEntry(SNAPSHOT_ENTRY)
                ?: throw IllegalStateException("备份包缺少数据快照")
            val snapshot = zip.getInputStream(snapshotEntry).bufferedReader().use { reader ->
                json.decodeFromString(AppSnapshot.serializer(), reader.readText())
            }
            val restoredFiles = snapshot.files.map { record ->
                if (!record.path.startsWith("files/")) return@map record
                val entry = zip.getEntry(record.path) ?: return@map record.copy(path = "")
                val safeName = File(record.path).name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = File(FileStore.dir(context), "restored_${System.currentTimeMillis()}_$safeName")
                zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                record.copy(path = target.absolutePath, size = target.length())
            }.filter { it.path.isNotBlank() }
            return snapshot.copy(files = restoredFiles)
        }
    }
}
