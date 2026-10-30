package com.workhub.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.workhub.app.data.AppDatabase
import com.workhub.app.data.AppRepository
import com.workhub.app.notify.ReminderScheduler
import com.workhub.app.appearance.BrandAppearance
import com.workhub.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class WorkHubApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: AppRepository by lazy { AppRepository(database, this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // Includes restored backups and existing users upgrading from the fixed dark icon.
            SettingsRepository(this@WorkHubApplication).themeMode.distinctUntilChanged().collect { mode ->
                try {
                    BrandAppearance.apply(this@WorkHubApplication, mode)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("BrandAppearance", "Could not synchronize launcher appearance", e)
                }
            }
        }
        createNotificationChannel()
        ReminderScheduler.schedule(this)
        ReminderScheduler.scheduleExactAlarms(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ReminderScheduler.CHANNEL_ID,
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "日程与待办事项提醒" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
