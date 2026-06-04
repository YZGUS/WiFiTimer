package com.cengyi.wifitimer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WiFiWhitelistEntry::class,
        WiFiSession::class,
        IgnoreWindow::class,
        DailyStats::class,
        TargetConfig::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun whitelistDao(): WhitelistDao
    abstract fun sessionDao(): SessionDao
    abstract fun ignoreWindowDao(): IgnoreWindowDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun targetConfigDao(): TargetConfigDao
}
