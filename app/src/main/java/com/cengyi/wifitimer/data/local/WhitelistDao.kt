package com.cengyi.wifitimer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {

    @Query("SELECT * FROM wifi_whitelist ORDER BY createdAt ASC")
    fun getAll(): Flow<List<WiFiWhitelistEntry>>

    @Query("SELECT * FROM wifi_whitelist WHERE enabled = 1 ORDER BY createdAt ASC")
    fun getEnabled(): Flow<List<WiFiWhitelistEntry>>

    @Query("SELECT * FROM wifi_whitelist WHERE enabled = 1")
    suspend fun getEnabledList(): List<WiFiWhitelistEntry>

    @Query("SELECT * FROM wifi_whitelist WHERE id = :id")
    suspend fun getById(id: Long): WiFiWhitelistEntry?

    @Insert
    suspend fun insert(entry: WiFiWhitelistEntry): Long

    @Update
    suspend fun update(entry: WiFiWhitelistEntry)

    @Delete
    suspend fun delete(entry: WiFiWhitelistEntry)

    @Query("DELETE FROM wifi_whitelist WHERE id = :id")
    suspend fun deleteById(id: Long)
}
