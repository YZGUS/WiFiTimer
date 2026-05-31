package com.cengyi.wifitimer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "ignore_windows")
data class IgnoreWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true
)

fun IgnoreWindow.toTodayRanges(): List<Pair<Long, Long>> {
    val today = LocalDate.now()
    val start = LocalDateTime.of(today, LocalTime.of(startHour, startMinute))

    // 跨午夜：结束时间在次日
    val endDate = if (endHour * 60 + endMinute <= startHour * 60 + startMinute) {
        today.plusDays(1)
    } else {
        today
    }
    val end = LocalDateTime.of(endDate, LocalTime.of(endHour, endMinute))

    val startMs = start.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(start)).toEpochMilli()
    val endMs = end.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(end)).toEpochMilli()

    // 如果跨午夜，拆分为两段
    val midnight = today.plusDays(1).atStartOfDay()
    val midnightMs = midnight.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(midnight)).toEpochMilli()

    return if (endMs > midnightMs) {
        listOf(startMs to midnightMs, midnightMs to endMs)
    } else {
        listOf(startMs to endMs)
    }
}
