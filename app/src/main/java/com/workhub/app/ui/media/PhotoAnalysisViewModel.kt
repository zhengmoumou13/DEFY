package com.workhub.app.ui.media

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workhub.app.ai.MediaAiClient
import com.workhub.app.ai.PhotoAnalysis
import com.workhub.app.ai.PhotoAnalysisReport
import com.workhub.app.ai.PhotoScene
import com.workhub.app.data.AiPlanEntity
import com.workhub.app.data.AppRepository
import com.workhub.app.media.AnalysisPhotos
import com.workhub.app.settings.SettingsRepository
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PhotoAnalysisState(
    val photos: List<File> = emptyList(),
    val notes: String = "",
    val meal: String = "午餐",
    val includeHistory: Boolean = false,
    val importing: Boolean = false,
    val analyzing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val pendingReport: PhotoAnalysisReport? = null,
    val selectedId: Long = 0
) {
    val busy: Boolean get() = importing || analyzing || saving
}

class PhotoAnalysisViewModel(
    application: Application,
    private val repo: AppRepository,
    private val settings: SettingsRepository,
    val scene: PhotoScene
) : AndroidViewModel(application) {
    private val context: Application get() = getApplication()
    var state by mutableStateOf(PhotoAnalysisState())
        private set
    private var cameraFile: File? = null
    private var generation: Job? = null
    private var pendingDate: String = ""

    init {
        viewModelScope.launch(Dispatchers.IO) { AnalysisPhotos.cleanExpired(context) }
    }

    fun notes(value: String) { if (!state.busy && value.length <= 1500) state = state.copy(notes = value) }
    fun meal(value: String) { if (!state.busy) state = state.copy(meal = value) }
    fun includeHistory(value: Boolean) { if (!state.busy) state = state.copy(includeHistory = value) }
    fun select(id: Long) { state = state.copy(selectedId = id) }
    fun error(message: String) { state = state.copy(error = message) }

    fun prepareCamera(): File {
        cameraFile?.delete()
        return AnalysisPhotos.cameraFile(context).also { cameraFile = it }
    }

    fun finishCamera(success: Boolean) {
        val file = cameraFile ?: return
        cameraFile = null
        if (success) addPhotos(listOf(Uri.fromFile(file)), file) else file.delete()
    }

    fun addPhotos(uris: List<Uri>, cameraSource: File? = null) {
        if (state.busy || uris.isEmpty()) { cameraSource?.delete(); return }
        val remaining = 3 - state.photos.size
        val selected = uris.distinct().take(remaining)
        if (selected.isEmpty()) { cameraSource?.delete(); return }
        state = state.copy(importing = true, error = if (uris.size > remaining) "最多3张，已按顺序选取前${remaining}张" else null)
        viewModelScope.launch {
            try {
                selected.forEach { uri ->
                    val photo = AnalysisPhotos.prepare(context, uri)
                    state = state.copy(photos = state.photos + photo)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state = state.copy(error = failure.message ?: "无法读取图片，请重新选择")
            } finally {
                cameraSource?.delete()
                state = state.copy(importing = false)
            }
        }
    }

    fun remove(file: File) {
        if (!state.busy) {
            state = state.copy(photos = state.photos - file)
            file.delete()
        }
    }

    fun generate() {
        if (state.busy || state.photos.isEmpty() || state.pendingReport != null) return
        val draft = state
        pendingDate = LocalDate.now().toString()
        state = state.copy(analyzing = true, error = null)
        generation = viewModelScope.launch {
            try {
                val current = settings.settings.first()
                val history = if (scene == PhotoScene.MEAL && draft.includeHistory) repo.observePlansByType(scene.recordType).first() else emptyList()
                val images = AnalysisPhotos.dataUrls(draft.photos)
                val result = MediaAiClient.analyze(
                    current.visionBaseUrl, current.visionApiKey, current.visionModel,
                    PhotoAnalysis.prompt(scene, draft.notes, draft.meal, current, history), images
                )
                state = state.copy(pendingReport = PhotoAnalysisReport(scene.key, draft.notes, if (scene == PhotoScene.MEAL) draft.meal else "", draft.photos.size, current.visionModel, result, draft.includeHistory))
                persistPending()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state = state.copy(error = failure.message ?: "分析失败，照片与备注已保留，请重试")
            } finally {
                state = state.copy(analyzing = false, saving = false)
            }
        }
    }

    fun cancelAnalysis() { generation?.cancel(); state = state.copy(error = "已取消分析，照片与备注已保留") }

    fun retrySave() {
        if (state.busy || state.pendingReport == null) return
        viewModelScope.launch {
            try { persistPending() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state = state.copy(error = "保存失败，分析结果仍在下方，可再次保存") }
            finally { state = state.copy(saving = false) }
        }
    }

    private suspend fun persistPending() {
        val report = state.pendingReport ?: return
        state = state.copy(saving = true)
        val id = repo.savePlan(AiPlanEntity(type = scene.recordType, date = pendingDate, content = PhotoAnalysis.encode(report)))
        state = state.copy(selectedId = id, pendingReport = null, error = null)
    }

    fun discardPending() { if (!state.busy) state = state.copy(pendingReport = null) }

    fun delete(record: AiPlanEntity) {
        if (state.busy || record.type != scene.recordType) return
        state = state.copy(saving = true)
        viewModelScope.launch {
            try {
                repo.deletePhotoAnalysis(record)
                if (state.selectedId == record.id) state = state.copy(selectedId = 0)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state = state.copy(error = "删除失败，请重试") }
            finally { state = state.copy(saving = false) }
        }
    }

    override fun onCleared() {
        state.photos.forEach { it.delete() }
        cameraFile?.delete()
        super.onCleared()
    }
}
