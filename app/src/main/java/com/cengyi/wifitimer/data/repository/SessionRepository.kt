package com.cengyi.wifitimer.data.repository

import com.cengyi.wifitimer.data.local.DailyStats
import com.cengyi.wifitimer.data.local.DailyStatsDao
import com.cengyi.wifitimer.data.local.SessionDao
import com.cengyi.wifitimer.data.local.WiFiSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val dailyStatsDao: DailyStatsDao
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getSessionsByDate(date: String): Flow<List<WiFiSession>> =
        sessionDao.getByDate(date)

    suspend fun getSessionsByDateList(date: String): List<WiFiSession> =
        sessionDao.getByDateList(date)

    suspend fun getEffectiveTotalForDate(date: String): Long =
        sessionDao.getEffectiveTotalForDate(date)

    fun getEffectiveTotalFlow(date: String): Flow<Long> =
        sessionDao.getEffectiveTotalFlow(date)

    suspend fun insertSession(session: WiFiSession): Long =
        sessionDao.insert(session)

    suspend fun recalculateDailyStats(date: String, targetMs: Long) {
        val totalMs = sessionDao.getEffectiveTotalForDate(date)
        val count = sessionDao.getSessionCountForDate(date)
        dailyStatsDao.upsert(
            DailyStats(
                date = date,
                totalEffectiveMs = totalMs,
                sessionCount = count,
                targetReached = totalMs >= targetMs,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    fun getDailyStatsFlow(date: String): Flow<DailyStats?> =
        dailyStatsDao.getByDateFlow(date)

    suspend fun getDailyStats(date: String): DailyStats? =
        dailyStatsDao.getByDate(date)

    suspend fun getHistory(startDate: String, endDate: String): List<DailyStats> =
        dailyStatsDao.getByDateRange(startDate, endDate)

    fun todayDate(): String = LocalDate.now().format(dateFormatter)
}
