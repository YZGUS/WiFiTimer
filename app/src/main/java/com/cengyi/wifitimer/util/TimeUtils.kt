package com.cengyi.wifitimer.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun toDateStr(timestampMs: Long): String {
        return Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(dateFormatter)
    }

    fun toTimeStr(timestampMs: Long): String {
        return Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(timeFormatter)
    }

    fun todayStr(): String = LocalDate.now().format(dateFormatter)

    fun todayStartMs(): Long {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    fun todayEndMs(): Long {
        return LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * 将时间戳拆分为属于不同日期的子区间（处理跨天 session）
     */
    fun splitByDay(startMs: Long, endMs: Long): List<Pair<String, Pair<Long, Long>>> {
        val result = mutableListOf<Pair<String, Pair<Long, Long>>>()
        var current = startMs
        while (current < endMs) {
            val dateStr = toDateStr(current)
            val nextDayStart = LocalDate.parse(dateStr, dateFormatter)
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val segmentEnd = minOf(endMs, nextDayStart)
            result.add(dateStr to (current to segmentEnd))
            current = segmentEnd
        }
        return result
    }
}
