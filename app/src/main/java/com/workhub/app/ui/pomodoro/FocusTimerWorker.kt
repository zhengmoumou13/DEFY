package com.workhub.app.ui.pomodoro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.workhub.app.data.AppDatabase
import com.workhub.app.data.PomodoroEntity

class FocusTimerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val endAt = inputData.getLong("end_at", 0L)
        val minutes = FocusTimerStore.claimCompletion(applicationContext, endAt) ?: return Result.success()
        AppDatabase.getInstance(applicationContext).pomodoroDao()
            .insert(PomodoroEntity(durationMinutes = minutes, completedAt = endAt))
        notifyFocusComplete(applicationContext, minutes)
        return Result.success()
    }
}

internal fun notifyFocusComplete(context: Context, minutes: Int) {
    val channelId = "pomodoro_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "专注计时器", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "倒计时完成提醒" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("倒计时完成")
        .setContentText("已完成 $minutes 分钟专注")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(9901, notification)
    } catch (_: SecurityException) {
        // 用户未授予通知权限
    }
}

internal fun notifyRestComplete(context: Context) {
    val channelId = "pomodoro_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "训练计时器", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("组间休息结束")
        .setContentText("状态确认后开始下一组，动作质量下降时继续休息")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(9903, notification)
    } catch (_: SecurityException) {
        // 用户未授予通知权限
    }
}
