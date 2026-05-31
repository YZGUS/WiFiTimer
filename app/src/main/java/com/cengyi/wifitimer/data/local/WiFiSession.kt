package com.cengyi.wifitimer.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_sessions")
data class WiFiSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ssid: String,
    val bssid: String? = null,
    val whitelistEntryId: Long,
    val startTime: Long,
    val endTime: Long,
    val rawDurationMs: Long,
    val effectiveDurationMs: Long,
    val date: String
)
