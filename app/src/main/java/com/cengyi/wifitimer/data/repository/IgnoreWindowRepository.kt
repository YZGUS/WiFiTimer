package com.cengyi.wifitimer.data.repository

import com.cengyi.wifitimer.data.local.IgnoreWindow
import com.cengyi.wifitimer.data.local.IgnoreWindowDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IgnoreWindowRepository @Inject constructor(
    private val dao: IgnoreWindowDao
) {
    fun getAll(): Flow<List<IgnoreWindow>> = dao.getAll()

    suspend fun getEnabledList(): List<IgnoreWindow> = dao.getEnabledList()

    suspend fun insert(window: IgnoreWindow): Long = dao.insert(window)

    suspend fun update(window: IgnoreWindow) = dao.update(window)

    suspend fun delete(window: IgnoreWindow) = dao.delete(window)

    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
