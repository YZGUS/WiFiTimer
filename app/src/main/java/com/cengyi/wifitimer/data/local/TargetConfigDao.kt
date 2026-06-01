package com.cengyi.wifitimer.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetConfigDao {

    @Query("SELECT * FROM target_config WHERE id = 1")
    suspend fun get(): TargetConfig?

    @Query("SELECT * FROM target_config WHERE id = 1")
    fun getFlow(): Flow<TargetConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: TargetConfig)
}
