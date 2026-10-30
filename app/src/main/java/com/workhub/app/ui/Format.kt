package com.workhub.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Format {

    private val dateTimeFmt = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun localDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    /** 任务截止时间展示：今天/明天/昨天 + 时间；若时间是 00:00 则只显示日期 */
    fun dueText(millis: Long): String {
        val today = LocalDate.now()
        val d = localDate(millis)
        val prefix = when (d) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            today.minusDays(1) -> "昨天"
            else -> "${d.monthValue}月${d.dayOfMonth}日"
        }
        val t = localDateTime(millis).toLocalTime()
        return if (t == LocalTime.MIDNIGHT) prefix else "$prefix ${t.format(timeFmt)}"
    }

    fun dateTimeText(millis: Long): String = localDateTime(millis).format(dateTimeFmt)

    fun sizeText(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / 1024.0 / 1024 / 1024)
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024)
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun greeting(): String {
        val h = LocalTime.now().hour
        return when {
            h < 5 -> "夜深了，注意休息"
            h < 9 -> "早上好"
            h < 12 -> "上午好"
            h < 14 -> "中午好，记得吃饭"
            h < 18 -> "下午好"
            else -> "晚上好"
        }
    }
}
