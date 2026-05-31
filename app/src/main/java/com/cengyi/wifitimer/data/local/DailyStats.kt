package com.cengyi.wifitimer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey val date: String,
    val totalEffectiveMs: Long,
    val sessionCount: Int,
    val targetReached: Boolean,
    val lastUpdated: Long
)
