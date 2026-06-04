package com.cengyi.wifitimer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "target_config")
data class TargetConfig(
    @PrimaryKey val id: Int = 1,
    val targetMinutes: Int = 570,
    val label: String = "工作日目标",
    val enabled: Boolean = true,
    val endHour: Int = 18,
    val endMinute: Int = 0
)
