package com.cengyi.wifitimer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: DailyStats)

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getByDate(date: String): DailyStats?

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun getByDateFlow(date: String): Flow<DailyStats?>

    @Query("SELECT * FROM daily_stats WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<DailyStats>
}
