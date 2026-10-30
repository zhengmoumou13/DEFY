package com.workhub.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.workhub.app.MainActivity
import com.workhub.app.R
import com.workhub.app.data.AppDatabase
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 桌面小组件：显示今日任务进度与待办列表 */
class WorkHubWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> refresh(context, appWidgetManager, id) }
    }

    companion object {

        /** 数据变化后调用，刷新所有已添加的小组件 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WorkHubWidgetProvider::class.java)
            )
            ids.forEach { id -> refresh(context, manager, id) }
        }

        private fun refresh(context: Context, manager: AppWidgetManager, widgetId: Int) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
                val db = AppDatabase.getInstance(context.applicationContext)
                val tasks = db.taskDao().observeAll().first()

                val today = LocalDate.now()
                val zone = ZoneId.systemDefault()
                val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                val todayTasks = tasks.filter { it.dueDate != null && it.dueDate!! in start..end }
                val done = todayTasks.count { it.status == 2 }
                val open = todayTasks.filter { it.status != 2 }

                val views = RemoteViews(context.packageName, R.layout.widget_today)
                views.setTextViewText(R.id.widget_title, "DEFY · 今日")
                views.setTextViewText(R.id.widget_progress, "完成 $done/${todayTasks.size}")

                for (i in 1..4) {
                    val lineId = context.resources.getIdentifier("widget_line$i", "id", context.packageName)
                    val task = open.getOrNull(i - 1)
                    when {
                        task != null -> {
                            views.setTextViewText(lineId, "• ${task.title}")
                            views.setViewVisibility(lineId, View.VISIBLE)
                        }
                        i == 1 -> {
                            views.setTextViewText(lineId, "今天没有待办，休息一下吧 🎉")
                            views.setViewVisibility(lineId, View.VISIBLE)
                        }
                        else -> views.setViewVisibility(lineId, View.GONE)
                    }
                }

                val intent = Intent(context, MainActivity::class.java)
                val pi = PendingIntent.getActivity(
                    context,
                    widgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pi)
                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}
