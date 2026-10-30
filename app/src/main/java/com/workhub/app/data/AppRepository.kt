package com.workhub.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/** 统一数据入口：UI 只与这个仓库交互 */
class AppRepository(
    val db: AppDatabase,
    private val context: android.content.Context
) {

    // ---------- 任务 ----------
    val allTasks: Flow<List<TaskEntity>> = db.taskDao().observeAll()
    val openTasks: Flow<List<TaskEntity>> = db.taskDao().observeOpen()

    suspend fun getTask(id: Long): TaskEntity? = db.taskDao().getById(id)

    suspend fun saveTask(task: TaskEntity): Long = db.taskDao().insert(task).also {
        com.workhub.app.ui.widget.WorkHubWidgetProvider.refreshAll(context)
    }

    /** Import selected plan actions atomically; repeated imports do not duplicate a day's tasks. */
    suspend fun savePlanTasks(tasks: List<TaskEntity>) {
        db.withTransaction {
            val existing = db.taskDao().getAll().map { Triple(it.title, it.dueDate, it.detail) }.toMutableSet()
            tasks.forEach { task ->
                if (existing.add(Triple(task.title, task.dueDate, task.detail))) db.taskDao().insert(task)
            }
        }
        com.workhub.app.ui.widget.WorkHubWidgetProvider.refreshAll(context)
    }

    suspend fun updateTask(task: TaskEntity) {
        db.taskDao().update(task)
        com.workhub.app.ui.widget.WorkHubWidgetProvider.refreshAll(context)
    }

    suspend fun deleteTask(task: TaskEntity) {
        db.taskDao().delete(task)
        com.workhub.app.ui.widget.WorkHubWidgetProvider.refreshAll(context)
    }

    /** 重复任务：已完成且已过期的实例，重置为待办并顺延到下一个周期 */
    suspend fun refreshRepeatingTasks() {
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        db.taskDao().getDoneRepeats().forEach { t ->
            val due = t.dueDate ?: return@forEach
            if (due < todayStart) {
                db.taskDao().update(t.copy(status = 0, dueDate = nextOccurrence(due, t.repeatType)))
            }
        }
    }

    private fun nextOccurrence(due: Long, type: Int): Long {
        var d = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        var guard = 0
        while (d.isBefore(today) && guard < 1000) {
            d = when (type) {
                1 -> d.plusDays(1)
                2 -> d.plusWeeks(1)
                3 -> d.plusMonths(1)
                else -> d.plusDays(1)
            }
            guard++
        }
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    // ---------- 笔记 ----------
    val allNotes: Flow<List<NoteEntity>> = db.noteDao().observeAll()

    suspend fun getNote(id: Long): NoteEntity? = db.noteDao().getById(id)

    suspend fun saveNote(note: NoteEntity): Long = db.noteDao().insert(note)

    suspend fun updateNote(note: NoteEntity) = db.noteDao().update(note)

    suspend fun deleteNote(note: NoteEntity) = db.noteDao().delete(note)

    // ---------- 日程 ----------
    val allEvents: Flow<List<EventEntity>> = db.eventDao().observeAll()

    suspend fun getEventsBetween(start: Long, end: Long): List<EventEntity> =
        db.eventDao().getBetween(start, end)

    suspend fun saveEvent(event: EventEntity): Long =
        db.eventDao().insert(event).also {
            com.workhub.app.notify.ReminderScheduler.scheduleExactAlarms(context)
        }

    suspend fun updateEvent(event: EventEntity) {
        db.eventDao().update(event)
        com.workhub.app.notify.ReminderScheduler.scheduleExactAlarms(context)
    }

    suspend fun deleteEvent(event: EventEntity) {
        db.eventDao().delete(event)
        com.workhub.app.notify.ReminderScheduler.scheduleExactAlarms(context)
    }

    // ---------- 文件 ----------
    val allFiles: Flow<List<FileRecordEntity>> = db.fileDao().observeAll()

    suspend fun saveFile(record: FileRecordEntity): Long = db.fileDao().insert(record)

    suspend fun deleteFile(record: FileRecordEntity) = db.fileDao().delete(record)

    // ---------- AI 计划 ----------
    fun observePlan(type: String, date: String): Flow<AiPlanEntity?> =
        db.aiPlanDao().observePlan(type, date)

    fun observePlansByType(type: String): Flow<List<AiPlanEntity>> =
        db.aiPlanDao().observeByType(type)

    suspend fun savePlan(plan: AiPlanEntity): Long = db.aiPlanDao().insert(plan)

    suspend fun getPlan(id: Long): AiPlanEntity? = db.aiPlanDao().getById(id)

    suspend fun deletePhotoAnalysis(record: AiPlanEntity) {
        require(record.type in listOf("photo_training", "photo_meal"))
        db.aiPlanDao().delete(record)
    }

    // ---------- AI 对话 ----------
    val allChatMessages: Flow<List<ChatMessageEntity>> = db.chatDao().observeAll()

    suspend fun getChatMessages(): List<ChatMessageEntity> = db.chatDao().getAll()

    suspend fun saveChatMessage(msg: ChatMessageEntity): Long = db.chatDao().insert(msg)

    suspend fun clearChat() = db.chatDao().clear()

    // ---------- 身体数据 ----------
    val allBodyRecords: Flow<List<BodyRecordEntity>> = db.bodyRecordDao().observeAll()

    suspend fun saveBodyRecord(record: BodyRecordEntity): Long = db.bodyRecordDao().insert(record)

    suspend fun deleteBodyRecord(record: BodyRecordEntity) = db.bodyRecordDao().delete(record)

    // ---------- 长期计划 ----------
    fun observeLatestPlan(type: String): Flow<LongTermPlanEntity?> =
        db.longTermPlanDao().observeLatest(type)

    fun observeLongTermPlans(type: String): Flow<List<LongTermPlanEntity>> =
        db.longTermPlanDao().observeAll(type)

    suspend fun saveLongTermPlan(plan: LongTermPlanEntity): Long = db.longTermPlanDao().insert(plan)

    suspend fun updateLongTermPlan(plan: LongTermPlanEntity) = db.longTermPlanDao().update(plan)

    suspend fun deleteLongTermPlan(plan: LongTermPlanEntity) = db.longTermPlanDao().delete(plan)

    // ---------- 计划打卡 ----------
    fun observePlanLog(type: String, date: String): Flow<PlanLogEntity?> =
        db.planLogDao().observeLog(type, date)

    suspend fun savePlanLog(log: PlanLogEntity): Long = db.planLogDao().insert(log)

    fun observePlanLogs(type: String): Flow<List<PlanLogEntity>> =
        db.planLogDao().observeByType(type)

    // ---------- 计划逐项勾选 ----------
    fun observePlanChecks(type: String, date: String): Flow<List<PlanCheckEntity>> =
        db.planCheckDao().observeByTypeDate(type, date)

    // ---------- 训练过程记录 ----------
    fun observeWorkoutSets(date: String): Flow<List<WorkoutSetEntity>> =
        db.workoutSetDao().observeByDate(date)

    fun observeAllWorkoutSets(): Flow<List<WorkoutSetEntity>> =
        db.workoutSetDao().observeAllSets()

    suspend fun addWorkoutSet(set: WorkoutSetEntity): Long = db.workoutSetDao().insert(set)

    suspend fun deleteWorkoutSet(set: WorkoutSetEntity) = db.workoutSetDao().delete(set)

    val allBallTrainingRecords: Flow<List<BallTrainingRecordEntity>> = db.ballTrainingDao().observeAll()

    suspend fun getBallTrainingRecord(id: Long): BallTrainingRecordEntity? = db.ballTrainingDao().getById(id)

    suspend fun saveBallTrainingRecord(record: BallTrainingRecordEntity): Long {
        val error = com.workhub.app.training.validateBallRecord(record)
        require(error == null) { error.orEmpty() }
        return db.ballTrainingDao().insert(record)
    }

    suspend fun deleteBallTrainingRecord(record: BallTrainingRecordEntity) = db.ballTrainingDao().delete(record)

    suspend fun togglePlanCheck(type: String, date: String, itemKey: String, label: String, checked: Boolean) {
        val existing = db.planCheckDao().getByKey(type, date, itemKey)
        if (existing != null) {
            db.planCheckDao().insert(
                existing.copy(label = label, checked = checked, updatedAt = System.currentTimeMillis())
            )
        } else {
            db.planCheckDao().insert(
                PlanCheckEntity(
                    type = type, date = date, itemKey = itemKey,
                    label = label, checked = checked
                )
            )
        }
    }

    suspend fun renamePlanCheck(type: String, date: String, itemKey: String, label: String) {
        val existing = db.planCheckDao().getByKey(type, date, itemKey)
        if (existing != null) {
            db.planCheckDao().insert(existing.copy(label = label, updatedAt = System.currentTimeMillis()))
        } else {
            db.planCheckDao().insert(
                PlanCheckEntity(type = type, date = date, itemKey = itemKey, label = label)
            )
        }
    }

    // ---------- 番茄钟 ----------
    val allPomodoros: Flow<List<PomodoroEntity>> = db.pomodoroDao().observeAll()

    suspend fun savePomodoro(record: PomodoroEntity): Long = db.pomodoroDao().insert(record)

    suspend fun saveGymMedia(exerciseId: String, file: FileRecordEntity, attribution: String) = db.withTransaction {
        val id = db.fileDao().insert(file)
        db.gymDao().saveMedia(GymMediaEntity(exerciseId, id, attribution.ifBlank { "本地导入" }))
    }

    suspend fun saveGymSet(set: WorkoutSetEntity) = db.withTransaction {
        require(!set.gymSessionId.isNullOrBlank() && set.gymSlotKey.isNotBlank())
        val previous = db.gymDao().set(set.gymSessionId, set.gymSlotKey)
        db.workoutSetDao().insert(set.copy(id = previous?.id ?: 0))
    }

    // ---------- 备份/恢复 ----------
    suspend fun exportSnapshot(): AppSnapshot =
        AppSnapshot(
            tasks = db.taskDao().getAll(),
            notes = db.noteDao().getAll(),
            events = db.eventDao().getAll(),
            files = db.fileDao().getAll(),
            aiPlans = db.aiPlanDao().getAll(),
            pomodoros = db.pomodoroDao().getAll(),
            chatMessages = db.chatDao().getAll(),
            bodyRecords = db.bodyRecordDao().getAll(),
            longTermPlans = db.longTermPlanDao().getAll("fitness") +
                db.longTermPlanDao().getAll("diet"),
            planLogs = db.planLogDao().getAll(),
            planChecks = db.planCheckDao().getAll(),
            workoutSets = db.workoutSetDao().getAll(),
            ballTrainingRecords = db.ballTrainingDao().getAll(),
            gymTemplates = db.gymDao().templates(),
            gymSessions = db.gymDao().sessions(),
            gymMedia = db.gymDao().media()
        )

    suspend fun restoreSnapshot(snapshot: AppSnapshot) {
        db.withTransaction {
            db.clearAllTables()
            db.taskDao().insertAll(snapshot.tasks)
            db.noteDao().insertAll(snapshot.notes)
            db.eventDao().insertAll(snapshot.events)
            db.fileDao().insertAll(snapshot.files)
            db.aiPlanDao().insertAll(snapshot.aiPlans)
            db.pomodoroDao().insertAll(snapshot.pomodoros)
            db.chatDao().insertAll(snapshot.chatMessages)
            db.bodyRecordDao().insertAll(snapshot.bodyRecords)
            db.longTermPlanDao().insertAll(snapshot.longTermPlans)
            db.planLogDao().insertAll(snapshot.planLogs)
            db.planCheckDao().insertAll(snapshot.planChecks)
            db.workoutSetDao().insertAll(snapshot.workoutSets)
            db.ballTrainingDao().insertAll(snapshot.ballTrainingRecords)
            db.gymDao().restoreTemplates(snapshot.gymTemplates)
            db.gymDao().restoreSessions(snapshot.gymSessions)
            db.gymDao().restoreMedia(snapshot.gymMedia)
        }
    }
}
