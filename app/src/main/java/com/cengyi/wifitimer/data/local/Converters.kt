package com.cengyi.wifitimer.data.local

import androidx.room.TypeConverter
import java.time.DayOfWeek

class Converters {

    @TypeConverter
    fun fromDayOfWeekSet(days: Set<DayOfWeek>): String {
        return days.joinToString(",") { it.name }
    }

    @TypeConverter
    fun toDayOfWeekSet(value: String): Set<DayOfWeek> {
        if (value.isBlank()) return emptySet()
        return value.split(",").mapNotNull {
            runCatching { DayOfWeek.valueOf(it.trim()) }.getOrNull()
        }.toSet()
    }
}
