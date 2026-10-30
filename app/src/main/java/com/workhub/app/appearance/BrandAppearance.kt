package com.workhub.app.appearance

import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Keep the launcher identity and next cold-start splash in sync with the in-app choice. */
object BrandAppearance {
    fun currentMode(context: Context): String =
        if (context.packageManager.getComponentEnabledSetting(component(context, "light")) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED) "light" else "dark"

    fun apply(context: Context, mode: String) {
        val light = mode == "light"
        val pm = context.packageManager
        val selected = component(context, if (light) "light" else "dark")
        val other = component(context, if (light) "dark" else "light")
        val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val flags = PackageManager.DONT_KILL_APP
        // Explicit states also survive app updates; never disable MainActivity itself.
        if (pm.getComponentEnabledSetting(selected) != enabled ||
            pm.getComponentEnabledSetting(other) != disabled) {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.setComponentEnabledSettings(listOf(
                    PackageManager.ComponentEnabledSetting(selected, enabled, flags),
                    PackageManager.ComponentEnabledSetting(other, disabled, flags)
                ))
            } else {
                // Enable first, so older versions always retain a launchable entry.
                pm.setComponentEnabledSetting(selected, enabled, flags)
                pm.setComponentEnabledSetting(other, disabled, flags)
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = context.getSystemService(UiModeManager::class.java)
            val nightMode = if (light) UiModeManager.MODE_NIGHT_NO else UiModeManager.MODE_NIGHT_YES
            manager.setApplicationNightMode(nightMode)
        }
    }

    private fun component(context: Context, mode: String) =
        ComponentName(context, "${context.packageName}.Launcher${if (mode == "light") "Light" else "Dark"}")
}
