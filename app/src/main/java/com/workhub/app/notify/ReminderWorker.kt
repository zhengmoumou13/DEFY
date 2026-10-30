package com.workhub.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.workhub.app.data.AppDatabase

/** 每 15 分钟检查一次：到达提醒时间窗口的日程，推送通知 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 每次巡检时重新排布精确闹钟（设备重启/应用更新后 alarm 会丢失，靠这里补齐）
            ReminderScheduler.scheduleExactAlarms(applicationContext)
            val db = AppDatabase.getInstance(applicationContext)
            val now = System.currentTimeMillis()
            val horizon = now + 24 * 60 * 60 * 1000L
            val upcoming = db.eventDao().getBetween(now, horizon)

            val toNotify = upcoming.filter { e ->
                e.remindMinutes > 0 &&
                    (e.startTime - e.remindMinutes * 60_000L) <= now &&
                    (e.lastNotifiedAt == null || e.lastNotifiedAt < (e.startTime - e.remindMinutes * 60_000L))
            }

            if (toNotify.isNotEmpty()) {
                ReminderScheduler.notifyEvents(applicationContext, toNotify)
                toNotify.forEach { e ->
                    runCatching { db.eventDao().update(e.copy(lastNotifiedAt = now)) }
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
