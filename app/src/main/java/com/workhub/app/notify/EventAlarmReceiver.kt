package com.workhub.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.workhub.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 精确闹钟接收器：在日程提醒时间点（开始时间 - remindMinutes）被 AlarmManager 唤醒，
 * 立即推送通知，并重新排布后续的精确闹钟。
 */
class EventAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val event = db.eventDao().getById(eventId) ?: return@launch
                val now = System.currentTimeMillis()
                val triggerAt = event.startTime - event.remindMinutes * 60_000L
                val shouldNotify = event.remindMinutes > 0 &&
                    triggerAt <= now &&
                    (event.lastNotifiedAt == null || event.lastNotifiedAt < triggerAt)
                if (shouldNotify) {
                    ReminderScheduler.notifyEvents(appContext, listOf(event))
                    db.eventDao().update(event.copy(lastNotifiedAt = now))
                }
            } catch (_: Exception) {
                // 失败时由周期 Worker 兜底通知
            } finally {
                runCatching { ReminderScheduler.scheduleExactAlarms(appContext) }
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "eventId"
    }
}
