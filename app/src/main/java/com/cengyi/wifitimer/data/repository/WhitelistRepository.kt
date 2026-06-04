package com.cengyi.wifitimer.data.repository

import com.cengyi.wifitimer.data.local.WiFiWhitelistEntry
import com.cengyi.wifitimer.data.local.WhitelistDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhitelistRepository @Inject constructor(
    private val dao: WhitelistDao,
    private val sessionDao: com.cengyi.wifitimer.data.local.SessionDao
) {
    fun getAll(): Flow<List<WiFiWhitelistEntry>> = dao.getAll()

    fun getEnabled(): Flow<List<WiFiWhitelistEntry>> = dao.getEnabled()

    suspend fun getEnabledList(): List<WiFiWhitelistEntry> = dao.getEnabledList()

    suspend fun getById(id: Long): WiFiWhitelistEntry? = dao.getById(id)

    suspend fun insert(entry: WiFiWhitelistEntry): Long = dao.insert(entry)

    suspend fun update(entry: WiFiWhitelistEntry) = dao.update(entry)

    suspend fun delete(entry: WiFiWhitelistEntry) {
        sessionDao.deleteByWhitelistEntryId(entry.id)
        dao.delete(entry)
    }

    suspend fun deleteById(id: Long) {
        sessionDao.deleteByWhitelistEntryId(id)
        dao.deleteById(id)
    }

    suspend fun findMatch(ssid: String, bssid: String?): WiFiWhitelistEntry? {
        val normalized = ssid.trim('"')
        for (entry in dao.getEnabledList()) {
            if (entry.ssid.trim('"') != normalized) continue
            if (entry.bssid != null) {
                if (bssid != null && entry.bssid.equals(bssid, ignoreCase = true)) {
                    return entry
                }
            } else {
                return entry
            }
        }
        return null
    }
}
