package com.cengyi.wifitimer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: WiFiSession): Long

    @Query("SELECT * FROM wifi_sessions WHERE date = :date ORDER BY startTime ASC")
    fun getByDate(date: String): Flow<List<WiFiSession>>

    @Query("SELECT * FROM wifi_sessions WHERE date = :date ORDER BY startTime ASC")
    suspend fun getByDateList(date: String): List<WiFiSession>

    @Query("SELECT COALESCE(SUM(effectiveDurationMs), 0) FROM wifi_sessions WHERE date = :date")
    suspend fun getEffectiveTotalForDate(date: String): Long

    @Query("SELECT COALESCE(SUM(effectiveDurationMs), 0) FROM wifi_sessions WHERE date = :date")
    fun getEffectiveTotalFlow(date: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM wifi_sessions WHERE date = :date")
    suspend fun getSessionCountForDate(date: String): Int

    @Query("SELECT * FROM wifi_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, startTime ASC")
    suspend fun getByDateRange(startDate: String, endDate: String): List<WiFiSession>

    @Query("DELETE FROM wifi_sessions WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM wifi_sessions WHERE whitelistEntryId = :entryId")
    suspend fun deleteByWhitelistEntryId(entryId: Long)
}
