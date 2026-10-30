package com.workhub.app

import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workhub.app.settings.UserSettings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.workhub.app.files.BackupManager
import com.workhub.app.settings.SettingsRepository
import com.workhub.app.ui.AppRoot
import com.workhub.app.ui.theme.WorkHubTheme
import com.workhub.app.appearance.BrandAppearance
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val settingsRepository = remember { SettingsRepository(applicationContext) }
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                UserSettings(themeMode = BrandAppearance.currentMode(applicationContext))
            )
            val dark = settings.themeMode != "light"
            SideEffect {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.parseColor(if (dark) "#08090C" else "#F5F5F7")
                ))
                // Apply after the decor view is attached, including a cold start into light mode.
                window.decorView.post {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
            }
            WorkHubTheme(darkTheme = dark) { AppRoot() }
        }
    }

    /** 每次退到后台静默自动备份一份全量快照（只保留最近 5 份） */
    override fun onStop() {
        super.onStop()
        val app = application as WorkHubApplication
        lifecycleScope.launch {
            runCatching {
                val settings = SettingsRepository(this@MainActivity).exportBackupSettings()
                val snapshot = app.repository.exportSnapshot().copy(settings = settings)
                BackupManager.autoBackup(this@MainActivity, snapshot)
            }
        }
    }
}
