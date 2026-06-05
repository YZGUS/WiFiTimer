package com.cengyi.wifitimer.service

data class ActiveSession(
    val ssid: String,
    val bssid: String?,
    val whitelistEntryId: Long,
    val startTime: Long,
    val targetMinutes: Int
)