package com.cengyi.wifitimer.util

object WifiUtils {

    /**
     * 规范化 SSID：去除系统返回的首尾双引号
     */
    fun normalizeSsid(ssid: String): String {
        return ssid.trim('"')
    }

    /**
     * 判断两个 SSID 是否匹配（忽略引号，大小写敏感）
     */
    fun ssidMatches(a: String, b: String): Boolean {
        return normalizeSsid(a) == normalizeSsid(b)
    }

    /**
     * 判断是否为未知 SSID（Android 10+ 无位置权限时返回 "<unknown ssid>"）
     */
    fun isUnknownSsid(ssid: String): Boolean {
        val normalized = normalizeSsid(ssid)
        return normalized.isBlank() ||
                normalized.equals("<unknown ssid>", ignoreCase = true) ||
                normalized == "0x" ||
                normalized == "unknown ssid"
    }

    /**
     * 格式化时长（毫秒 → "X小时Y分"）
     */
    fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分"
            else -> "0分"
        }
    }

    /**
     * 格式化时长（毫秒 → "Xh Ym"）
     */
    fun formatDurationShort(ms: Long): String {
        val totalMinutes = ms / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }

    /** 默认目标时长 9.5 小时 = 570 分钟 */
    const val DEFAULT_TARGET_MINUTES = 570
    const val DEFAULT_TARGET_MS = DEFAULT_TARGET_MINUTES * 60_000L

    /** 最短有效 session 时长 30 秒（过滤抖动） */
    const val MIN_DURATION_MS = 30_000L
}
