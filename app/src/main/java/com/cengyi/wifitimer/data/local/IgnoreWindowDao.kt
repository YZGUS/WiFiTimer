package com.cengyi.wifitimer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoreWindowDao {

    @Query("SELECT * FROM ignore_windows ORDER BY startHour, startMinute ASC")
    fun getAll(): Flow<List<IgnoreWindow>>

    @Query("SELECT * FROM ignore_windows WHERE enabled = 1")
    suspend fun getEnabledList(): List<IgnoreWindow>

    @Insert
    suspend fun insert(window: IgnoreWindow): Long

    @Update
    suspend fun update(window: IgnoreWindow)

    @Delete
    suspend fun delete(window: IgnoreWindow)

    @Query("DELETE FROM ignore_windows WHERE id = :id")
    suspend fun deleteById(id: Long)
}
