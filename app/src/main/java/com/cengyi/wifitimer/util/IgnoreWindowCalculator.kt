package com.cengyi.wifitimer.util

import com.cengyi.wifitimer.data.local.IgnoreWindow
import com.cengyi.wifitimer.data.local.toRangesForDate
import com.cengyi.wifitimer.data.local.toTodayRanges
import java.time.DayOfWeek
import java.time.LocalDate

object IgnoreWindowCalculator {

    /**
     * 计算有效时长：从原始 session 区间中扣除所有启用的忽略时段
     * @param date 会话片段所属的日期，用于匹配忽略时段的 repeatDays
     */
    fun computeEffectiveDuration(
        sessionStartMs: Long,
        sessionEndMs: Long,
        windows: List<IgnoreWindow>,
        date: LocalDate = LocalDate.now()
    ): Long {
        if (sessionEndMs <= sessionStartMs) return 0L

        val dateDow = date.dayOfWeek

        val ranges = mutableListOf<Pair<Long, Long>>()
        for (w in windows) {
            if (!w.enabled) continue
            if (w.repeatDays.isNotEmpty() && dateDow !in w.repeatDays) continue
            ranges.addAll(w.toRangesForDate(date))
        }

        // 合并重叠区间
        val merged = mergeRanges(ranges)

        // 从 session 区间中扣减
        var effectiveMs = sessionEndMs - sessionStartMs
        for ((ignoreStart, ignoreEnd) in merged) {
            val overlapStart = maxOf(sessionStartMs, ignoreStart)
            val overlapEnd = minOf(sessionEndMs, ignoreEnd)
            val overlap = overlapEnd - overlapStart
            if (overlap > 0) {
                effectiveMs -= overlap
            }
        }

        return maxOf(0L, effectiveMs)
    }

    /**
     * 判断当前时刻是否处于忽略时段内
     */
    fun isCurrentlyInIgnoreWindow(
        currentTimeMs: Long,
        windows: List<IgnoreWindow>
    ): Boolean {
        val today = LocalDate.now()
        val todayDow = today.dayOfWeek

        for (w in windows) {
            if (!w.enabled) continue
            if (w.repeatDays.isNotEmpty() && todayDow !in w.repeatDays) continue
            for ((start, end) in w.toTodayRanges()) {
                if (currentTimeMs in start until end) return true
            }
        }
        return false
    }

    private fun mergeRanges(ranges: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val result = mutableListOf<Pair<Long, Long>>()
        var (curStart, curEnd) = sorted[0]
        for (i in 1 until sorted.size) {
            val (start, end) = sorted[i]
            if (start <= curEnd) {
                curEnd = maxOf(curEnd, end)
            } else {
                result.add(curStart to curEnd)
                curStart = start
                curEnd = end
            }
        }
        result.add(curStart to curEnd)
        return result
    }
}
