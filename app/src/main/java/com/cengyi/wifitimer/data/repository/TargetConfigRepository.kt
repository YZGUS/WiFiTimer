package com.cengyi.wifitimer.data.repository

import com.cengyi.wifitimer.data.local.TargetConfig
import com.cengyi.wifitimer.data.local.TargetConfigDao
import com.cengyi.wifitimer.util.WifiUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TargetConfigRepository @Inject constructor(
    private val dao: TargetConfigDao
) {
    fun getFlow(): Flow<TargetConfig?> = dao.getFlow()

    fun getTargetMinutesFlow(): Flow<Int> = dao.getFlow().map { it?.targetMinutes ?: WifiUtils.DEFAULT_TARGET_MINUTES }

    suspend fun get(): TargetConfig? = dao.get()

    suspend fun getTargetMinutes(): Int = dao.get()?.targetMinutes ?: WifiUtils.DEFAULT_TARGET_MINUTES

    suspend fun upsert(config: TargetConfig) = dao.upsert(config)

    suspend fun updateTargetMinutes(minutes: Int) {
        val existing = dao.get()
        if (existing != null) {
            dao.upsert(existing.copy(targetMinutes = minutes))
        } else {
            dao.upsert(TargetConfig(targetMinutes = minutes))
        }
    }
}
