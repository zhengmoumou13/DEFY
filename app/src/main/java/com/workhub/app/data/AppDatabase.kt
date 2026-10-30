package com.workhub.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        NoteEntity::class,
        EventEntity::class,
        FileRecordEntity::class,
        AiPlanEntity::class,
        PomodoroEntity::class,
        ChatMessageEntity::class,
        BodyRecordEntity::class,
        LongTermPlanEntity::class,
        PlanLogEntity::class,
        PlanCheckEntity::class,
        WorkoutSetEntity::class,
        BallTrainingRecordEntity::class, GymTemplateEntity::class, GymSessionEntity::class, GymMediaEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun eventDao(): EventDao
    abstract fun fileDao(): FileDao
    abstract fun aiPlanDao(): AiPlanDao
    abstract fun pomodoroDao(): PomodoroDao
    abstract fun chatDao(): ChatDao
    abstract fun bodyRecordDao(): BodyRecordDao
    abstract fun longTermPlanDao(): LongTermPlanDao
    abstract fun planLogDao(): PlanLogDao
    abstract fun planCheckDao(): PlanCheckDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun ballTrainingDao(): BallTrainingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ball_training_records ADD COLUMN movementKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ball_training_records ADD COLUMN planId INTEGER")
                db.execSQL("ALTER TABLE ball_training_records ADD COLUMN planItemId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ball_training_records ADD COLUMN durationSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ball_training_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`position` TEXT NOT NULL, `attempts` INTEGER NOT NULL, " +
                        "`made` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v1 → v2：任务表新增 repeatType 字段；新增番茄钟记录表 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatType INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pomodoro` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`durationMinutes` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v9 → v10：新增训练组记录表；打卡记录加 AI 建议并修复重复（唯一索引） */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`exercise` TEXT NOT NULL, " +
                        "`weightKg` REAL, " +
                        "`reps` INTEGER, " +
                        "`note` TEXT NOT NULL DEFAULT '', " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN advice TEXT NOT NULL DEFAULT ''")
                // 去重：每个 type+date 只保留最新一条，然后建唯一索引
                db.execSQL(
                    "DELETE FROM plan_logs WHERE id NOT IN " +
                        "(SELECT MAX(id) FROM plan_logs GROUP BY type, date)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_logs_type_date` " +
                        "ON plan_logs(`type`, `date`)"
                )
            }
        }

        /** v8 → v9：计划打卡新增训练时长/感受与饮食逐餐记录 */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN durationMinutes INTEGER")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN feeling INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN breakfast TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN lunch TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN dinner TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN snack TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN waterCups INTEGER")
            }
        }

        /** v7 → v8：身体记录新增动态与力量指标 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE body_records ADD COLUMN benchKg REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN pullupCount REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN plankSec REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN standingJumpCm REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN standingJumpReachCm REAL")
            }
        }

        /** v6 → v7：新增计划逐项勾选表 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_checks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`itemKey` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`checked` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v5 → v6：新增计划执行打卡表 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`status` INTEGER NOT NULL DEFAULT 0, " +
                        "`detail` TEXT NOT NULL DEFAULT '', " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v4 → v5：身体记录表新增扣篮功能性字段 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE body_records ADD COLUMN heightCm REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN wingspanCm REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN standingReachCm REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN maxReachCm REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN squatKg REAL")
                db.execSQL("ALTER TABLE body_records ADD COLUMN deadliftKg REAL")
            }
        }

        /** v3 → v4：新增身体记录表与长期计划表 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`weightKg` REAL, " +
                        "`bodyFatPct` REAL, " +
                        "`chestCm` REAL, " +
                        "`waistCm` REAL, " +
                        "`hipsCm` REAL, " +
                        "`armCm` REAL, " +
                        "`thighCm` REAL, " +
                        "`muscleKg` REAL, " +
                        "`note` TEXT NOT NULL DEFAULT '', " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `long_term_plans` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`startDate` TEXT NOT NULL, " +
                        "`durationDays` INTEGER NOT NULL, " +
                        "`goal` TEXT NOT NULL DEFAULT '', " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v2 → v3：新增 AI 对话记录表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "workhub.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
        /** v10 → v11：训练组支持次数、时长、距离、高度、RPE 与左右侧。 */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN metricType TEXT NOT NULL DEFAULT 'reps'")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN durationSeconds INTEGER")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN distanceMeters REAL")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN heightCm REAL")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN rpe INTEGER")
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN side TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v11 → v12：训练状态评估与长期计划暂停。 */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN sleepQuality INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN energyLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN sorenessLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN painLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plan_logs ADD COLUMN painArea TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE long_term_plans ADD COLUMN pausedAt TEXT")
                db.execSQL("ALTER TABLE long_term_plans ADD COLUMN pausedDays INTEGER NOT NULL DEFAULT 0")
            }
        }

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS gym_templates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, content TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS gym_sessions (id TEXT PRIMARY KEY NOT NULL, date TEXT NOT NULL, content TEXT NOT NULL, startedAt INTEGER NOT NULL, completedAt INTEGER)")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN gymSessionId TEXT")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN gymSlotKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE UNIQUE INDEX index_workout_sets_gymSessionId_gymSlotKey ON workout_sets(gymSessionId, gymSlotKey)")
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS gym_media (exerciseId TEXT PRIMARY KEY NOT NULL, fileId INTEGER NOT NULL, attribution TEXT NOT NULL)")
    }
}
