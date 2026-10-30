package com.workhub.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BallTrainingDao {
    @Query("SELECT * FROM ball_training_records ORDER BY date DESC, createdAt DESC, id DESC")
    fun observeAll(): Flow<List<BallTrainingRecordEntity>>

    @Query("SELECT * FROM ball_training_records WHERE id = :id")
    suspend fun getById(id: Long): BallTrainingRecordEntity?

    @Query("SELECT * FROM ball_training_records")
    suspend fun getAll(): List<BallTrainingRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BallTrainingRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<BallTrainingRecordEntity>)

    @Delete
    suspend fun delete(record: BallTrainingRecordEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY status ASC, priority DESC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status != 2 ORDER BY dueDate ASC, priority DESC, createdAt DESC")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE repeatType > 0 AND status = 2 AND dueDate IS NOT NULL")
    suspend fun getDoneRepeats(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startTime ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE startTime BETWEEN :start AND :end ORDER BY startTime ASC")
    suspend fun getBetween(start: Long, end: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)
}

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FileRecordEntity>>

    @Query("SELECT * FROM files")
    suspend fun getAll(): List<FileRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FileRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<FileRecordEntity>)

    @Delete
    suspend fun delete(record: FileRecordEntity)
}

@Dao
interface AiPlanDao {
    @Delete
    suspend fun delete(plan: AiPlanEntity)

    @Query("SELECT * FROM ai_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AiPlanEntity?

    @Query("SELECT * FROM ai_plans WHERE type = :type AND date = :date ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observePlan(type: String, date: String): Flow<AiPlanEntity?>

    @Query("SELECT * FROM ai_plans WHERE type = :type ORDER BY date DESC, createdAt DESC, id DESC")
    fun observeByType(type: String): Flow<List<AiPlanEntity>>

    @Query("SELECT * FROM ai_plans WHERE type = :type ORDER BY date DESC")
    suspend fun getByType(type: String): List<AiPlanEntity>

    @Query("SELECT * FROM ai_plans")
    suspend fun getAll(): List<AiPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: AiPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<AiPlanEntity>)
}

@Dao
interface PomodoroDao {
    @Query("SELECT * FROM pomodoro ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<PomodoroEntity>>

    @Query("SELECT * FROM pomodoro")
    suspend fun getAll(): List<PomodoroEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PomodoroEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PomodoroEntity>)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(msgs: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface BodyRecordDao {
    @Query("SELECT * FROM body_records ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<BodyRecordEntity>>

    @Query("SELECT * FROM body_records ORDER BY date ASC, createdAt ASC")
    suspend fun getAll(): List<BodyRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BodyRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<BodyRecordEntity>)

    @Delete
    suspend fun delete(record: BodyRecordEntity)
}

@Dao
interface LongTermPlanDao {
    @Query("SELECT * FROM long_term_plans WHERE type = :type ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(type: String): Flow<LongTermPlanEntity?>

    @Query("SELECT * FROM long_term_plans WHERE type = :type ORDER BY createdAt DESC")
    fun observeAll(type: String): Flow<List<LongTermPlanEntity>>

    @Query("SELECT * FROM long_term_plans WHERE type = :type ORDER BY createdAt DESC")
    suspend fun getAll(type: String): List<LongTermPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: LongTermPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<LongTermPlanEntity>)

    @Update
    suspend fun update(plan: LongTermPlanEntity)

    @Delete
    suspend fun delete(plan: LongTermPlanEntity)
}

@Dao
interface PlanLogDao {
    @Query("SELECT * FROM plan_logs WHERE type = :type AND date = :date LIMIT 1")
    fun observeLog(type: String, date: String): Flow<PlanLogEntity?>

    @Query("SELECT * FROM plan_logs WHERE type = :type AND date = :date LIMIT 1")
    suspend fun getLog(type: String, date: String): PlanLogEntity?

    @Query("SELECT * FROM plan_logs WHERE type = :type ORDER BY date DESC")
    fun observeByType(type: String): Flow<List<PlanLogEntity>>

    @Query("SELECT * FROM plan_logs WHERE type = :type ORDER BY date DESC")
    suspend fun getByType(type: String): List<PlanLogEntity>

    @Query("SELECT * FROM plan_logs")
    suspend fun getAll(): List<PlanLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PlanLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<PlanLogEntity>)
}

@Dao
interface PlanCheckDao {
    @Query("SELECT * FROM plan_checks WHERE type = :type AND date = :date")
    fun observeByTypeDate(type: String, date: String): Flow<List<PlanCheckEntity>>

    @Query("SELECT * FROM plan_checks WHERE type = :type AND date = :date")
    suspend fun getByTypeDate(type: String, date: String): List<PlanCheckEntity>

    @Query("SELECT * FROM plan_checks")
    suspend fun getAll(): List<PlanCheckEntity>

    @Query("SELECT * FROM plan_checks WHERE type = :type AND date = :date AND itemKey = :key LIMIT 1")
    suspend fun getByKey(type: String, date: String, key: String): PlanCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(check: PlanCheckEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(checks: List<PlanCheckEntity>)
}

@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets ORDER BY date DESC, createdAt ASC")
    fun observeAllSets(): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date = :date ORDER BY createdAt ASC")
    fun observeByDate(date: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date = :date ORDER BY createdAt ASC")
    suspend fun getByDate(date: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets")
    suspend fun getAll(): List<WorkoutSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(set: WorkoutSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<WorkoutSetEntity>)

    @Delete
    suspend fun delete(set: WorkoutSetEntity)
}

@Dao
interface GymDao {
    @Query("SELECT * FROM gym_media WHERE exerciseId = :id") fun observeMedia(id: String): Flow<GymMediaEntity?>
    @Query("SELECT * FROM gym_media") suspend fun media(): List<GymMediaEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveMedia(value: GymMediaEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreMedia(values: List<GymMediaEntity>)
    @Query("DELETE FROM gym_media WHERE exerciseId = :id") suspend fun unlinkMedia(id: String)

    @Query("SELECT * FROM gym_templates ORDER BY id DESC") fun observeTemplates(): Flow<List<GymTemplateEntity>>
    @Query("SELECT * FROM gym_templates") suspend fun templates(): List<GymTemplateEntity>
    @Query("SELECT * FROM gym_templates WHERE id = :id") suspend fun template(id: Long): GymTemplateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTemplate(value: GymTemplateEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTemplates(values: List<GymTemplateEntity>)
    @Query("DELETE FROM gym_templates WHERE id = :id") suspend fun deleteTemplate(id: Long)
    @Query("SELECT * FROM gym_sessions ORDER BY startedAt DESC") fun observeSessions(): Flow<List<GymSessionEntity>>
    @Query("SELECT * FROM gym_sessions") suspend fun sessions(): List<GymSessionEntity>
    @Query("SELECT * FROM gym_sessions WHERE id = :id") fun observeSession(id: String): Flow<GymSessionEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSession(value: GymSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSessions(values: List<GymSessionEntity>)
    @Query("UPDATE gym_sessions SET completedAt = :time WHERE id = :id") suspend fun finish(id: String, time: Long)
    @Query("DELETE FROM gym_sessions WHERE id = :id AND completedAt IS NULL AND NOT EXISTS (SELECT 1 FROM workout_sets WHERE gymSessionId = :id)")
    suspend fun discardEmptySession(id: String): Int
    @Query("SELECT * FROM workout_sets WHERE gymSessionId = :id") fun observeSets(id: String): Flow<List<WorkoutSetEntity>>
    @Query("SELECT * FROM workout_sets WHERE gymSessionId = :id AND gymSlotKey = :slot LIMIT 1") suspend fun set(id: String, slot: String): WorkoutSetEntity?
}
