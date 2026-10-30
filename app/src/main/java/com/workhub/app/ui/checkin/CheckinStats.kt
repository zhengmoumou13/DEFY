package com.workhub.app.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.workhub.app.data.PlanLogEntity
import java.time.LocalDate

/** 打卡统计结果 */
data class CheckinStats(
    val loggedDays: Int = 0,     // 范围内有打卡记录的天数
    val doneCount: Int = 0,      // 已完成
    val partialCount: Int = 0,   // 部分完成
    val missedCount: Int = 0,    // 未执行
    val streak: Int = 0          // 连续打卡天数（已完成/部分完成）
) {
    val executedDays: Int get() = doneCount + partialCount
    val rate: Int get() = if (loggedDays == 0) 0 else executedDays * 100 / loggedDays
}

/** 计算最近 days 天的打卡统计与连续天数 */
fun computeCheckinStats(logs: List<PlanLogEntity>, today: LocalDate, days: Int): CheckinStats {
    val from = today.minusDays((days - 1).toLong())
    val byDate = logs
        .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d to it } }
        .groupBy({ it.first }, { it.second })

    var done = 0
    var partial = 0
    var missed = 0
    var logged = 0
    byDate.forEach { (d, dayLogs) ->
        if (!d.isBefore(from) && !d.isAfter(today)) {
            logged++
            when (dayLogs.maxByOrNull { it.updatedAt }?.status) {
                1 -> done++
                2 -> partial++
                3 -> missed++
            }
        }
    }

    // 连续打卡：从今天（今天未打卡则从昨天）往回数连续"执行"的天数
    var streak = 0
    var cursor = today
    val todayStatus = byDate[today]?.maxByOrNull { it.updatedAt }?.status
    if (todayStatus == null || todayStatus == 0) cursor = today.minusDays(1)
    while (true) {
        val st = byDate[cursor]?.maxByOrNull { it.updatedAt }?.status
        if (st == 1 || st == 2) streak++ else break
        cursor = cursor.minusDays(1)
    }

    return CheckinStats(
        loggedDays = logged,
        doneCount = done,
        partialCount = partial,
        missedCount = missed,
        streak = streak
    )
}

/** 最近 N 天打卡日历（绿=已完成 橙=部分完成 红=未执行 灰=未打卡） */
@Composable
fun CheckinCalendar(logs: List<PlanLogEntity>, days: Int = 14) {
    val today = LocalDate.now()
    val byDate = logs
        .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d to it } }
        .groupBy({ it.first }, { it.second })
    val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        dates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                week.forEach { d ->
                    val status = byDate[d]?.maxByOrNull { it.updatedAt }?.status
                    val bg = when (status) {
                        1 -> Color(0xFF22C55E)
                        2 -> Color(0xFFF59E0B)
                        3 -> Color(0xFFEF4444)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val fg = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(bg, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = fg)
                        }
                        Text(
                            weekdayShort(d),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun weekdayShort(d: LocalDate): String =
    listOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
