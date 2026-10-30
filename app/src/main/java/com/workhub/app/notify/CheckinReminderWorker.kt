package com.workhub.app.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** 每日打卡提醒：通知后自动重排明天同一时间 */
class CheckinReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            notifyCheckin()
            val hour = inputData.getInt("hour", 20)
            ReminderScheduler.scheduleCheckinReminder(applicationContext, hour)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notifyCheckin() {
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🏀 今天的训练打卡了吗？")
            .setContentText("打开 DEFY · 跃过引力，查看今天的训练安排")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(9902, notification)
        } catch (_: SecurityException) {
            // 未授予通知权限
        }
    }
}
