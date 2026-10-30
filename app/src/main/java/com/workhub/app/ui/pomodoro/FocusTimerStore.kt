package com.workhub.app.ui.pomodoro

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal data class FocusTimerState(
    val type: Int = 1,
    val selectedMinutes: Int = 25,
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val endAt: Long = 0L,
    val baseElapsedSeconds: Int = 0,
    val pausedRemainingSeconds: Int = 25 * 60,
    val completed: Boolean = false
)

internal object FocusTimerStore {
    private const val PREFS = "focus_timer_state"
    private const val WORK_NAME = "focus_timer_completion"

    fun load(context: Context): FocusTimerState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return FocusTimerState(
            type = prefs.getInt("type", 1),
            selectedMinutes = prefs.getInt("selected_minutes", 25),
            running = prefs.getBoolean("running", false),
            startedAt = prefs.getLong("started_at", 0L),
            endAt = prefs.getLong("end_at", 0L),
            baseElapsedSeconds = prefs.getInt("base_elapsed", 0),
            pausedRemainingSeconds = prefs.getInt("paused_remaining", 25 * 60),
            completed = prefs.getBoolean("completed", false)
        )
    }

    fun start(context: Context, type: Int, selectedMinutes: Int, elapsed: Int, remaining: Int): FocusTimerState {
        val now = System.currentTimeMillis()
        val state = FocusTimerState(
            type = type,
            selectedMinutes = selectedMinutes,
            running = true,
            startedAt = now,
            endAt = if (type == 1) now + remaining.coerceAtLeast(0) * 1_000L else 0L,
            baseElapsedSeconds = elapsed,
            pausedRemainingSeconds = remaining,
            completed = false
        )
        save(context, state)
        if (type == 1) scheduleCompletion(context, state)
        return state
    }

    fun pause(context: Context, state: FocusTimerState): FocusTimerState {
        val paused = state.copy(running = false, startedAt = 0L, endAt = 0L)
        save(context, paused)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        return paused
    }

    fun reset(context: Context, type: Int, selectedMinutes: Int): FocusTimerState {
        val state = FocusTimerState(
            type = type,
            selectedMinutes = selectedMinutes,
            pausedRemainingSeconds = selectedMinutes * 60
        )
        save(context, state)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        return state
    }

    @Synchronized
    fun claimCompletion(context: Context, expectedEndAt: Long): Int? {
        val state = load(context)
        if (!state.running || state.type != 1 || state.endAt != expectedEndAt) return null
        if (System.currentTimeMillis() < state.endAt) return null
        save(
            context,
            state.copy(running = false, startedAt = 0L, endAt = 0L, pausedRemainingSeconds = 0, completed = true)
        )
        return state.selectedMinutes
    }

    fun savePausedProgress(
        context: Context,
        type: Int,
        selectedMinutes: Int,
        elapsed: Int,
        remaining: Int,
        completed: Boolean = false
    ): FocusTimerState {
        val state = FocusTimerState(
            type = type,
            selectedMinutes = selectedMinutes,
            baseElapsedSeconds = elapsed,
            pausedRemainingSeconds = remaining,
            completed = completed
        )
        save(context, state)
        return state
    }

    private fun save(context: Context, state: FocusTimerState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("type", state.type)
            .putInt("selected_minutes", state.selectedMinutes)
            .putBoolean("running", state.running)
            .putLong("started_at", state.startedAt)
            .putLong("end_at", state.endAt)
            .putInt("base_elapsed", state.baseElapsedSeconds)
            .putInt("paused_remaining", state.pausedRemainingSeconds)
            .putBoolean("completed", state.completed)
            .commit()
    }

    private fun scheduleCompletion(context: Context, state: FocusTimerState) {
        val delay = (state.endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder().putLong("end_at", state.endAt).build()
        val request = OneTimeWorkRequestBuilder<FocusTimerWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
