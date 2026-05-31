package com.cengyi.wifitimer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_whitelist")
data class WiFiWhitelistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ssid: String,
    val bssid: String? = null,
    val alias: String = "",
    val targetMinutes: Int = 570,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
