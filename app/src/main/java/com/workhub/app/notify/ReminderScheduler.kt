package com.workhub.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.workhub.app.MainActivity
import com.workhub.app.data.AppDatabase
import com.workhub.app.data.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    const val CHANNEL_ID = "event_reminders"

    private const val CHECKIN_WORK = "checkin_reminder"

    /** 注册每 15 分钟执行一次的提醒检查任务（兜底：补偿错过的精确闹钟） */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "reminder_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * 为未来 24 小时内的日程注册 AlarmManager 精确闹钟。
     * 系统会精确到日程提醒时间点唤醒 [EventAlarmReceiver] 推送通知；
     * 未授予精确闹钟权限时静默跳过，由周期 Worker 兜底。
     */
    fun scheduleExactAlarms(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val alarmManager =
                appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                return@launch
            }
            runCatching {
                val db = AppDatabase.getInstance(appContext)
                val now = System.currentTimeMillis()
                val horizon = now + 24 * 60 * 60 * 1000L
                val upcoming = db.eventDao().getBetween(now, horizon)
                for (e in upcoming) {
                    if (e.remindMinutes <= 0) continue
                    val triggerAt = e.startTime - e.remindMinutes * 60_000L
                    val alreadyFired = e.lastNotifiedAt != null && e.lastNotifiedAt >= triggerAt
                    if (triggerAt > now && !alreadyFired) {
                        setExactAlarm(appContext, alarmManager, e.id, triggerAt)
                    }
                }
            }
        }
    }

    private fun setExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        eventId: Long,
        triggerAt: Long
    ) {
        val pendingIntent = eventAlarmPendingIntent(context, eventId)
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }.onFailure {
            // 个别 ROM 限制精确闹钟，退化为允许休眠时的普通设置
            runCatching {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pendingIntent)
            }
        }
    }

    private fun eventAlarmPendingIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, EventAlarmReceiver::class.java)
            .putExtra(EventAlarmReceiver.EXTRA_EVENT_ID, eventId)
        return PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 安排每日打卡提醒（到点通知并自动重排明天） */
    fun scheduleCheckinReminder(context: Context, hour: Int, minute: Int = 0) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59))
            .withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val data = Data.Builder().putInt("hour", hour).build()
        val request = OneTimeWorkRequestBuilder<CheckinReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHECKIN_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelCheckinReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CHECKIN_WORK)
    }

    fun notifyEvents(context: Context, events: List<EventEntity>) {
        for (e in events) {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                e.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("⏰ ${e.title}")
                .setContentText("时间：${formatTime(e.startTime)}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(e.id.toInt(), notification)
            } catch (_: SecurityException) {
                // 用户未授予通知权限，静默忽略
            }
        }
    }

    private fun formatTime(epochMillis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
    }
}
