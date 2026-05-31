package com.cengyi.wifitimer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.cengyi.wifitimer.R
import com.cengyi.wifitimer.data.repository.IgnoreWindowRepository
import com.cengyi.wifitimer.data.repository.SessionRepository
import com.cengyi.wifitimer.data.repository.WhitelistRepository
import com.cengyi.wifitimer.util.WifiUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionRepo: SessionRepository,
    private val whitelistRepo: WhitelistRepository,
    private val ignoreWindowRepo: IgnoreWindowRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "daily_check"
        private const val REMINDER_CHANNEL_ID = "wifi_reminder"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyCheckWorker>(
                1, TimeUnit.HOURS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val totalMs = sessionRepo.getEffectiveTotalForDate(today)

        // 获取目标时长（取白名单中最小的目标）
        val entries = whitelistRepo.getEnabledList()
        val targetMs = entries.minOfOrNull { it.targetMinutes * 60_000L }
            ?: WifiUtils.DEFAULT_TARGET_MS

        // 更新每日统计
        sessionRepo.recalculateDailyStats(today, targetMs)

        // 判断是否需要提醒
        val remaining = targetMs - totalMs
        val now = LocalDateTime.now()
        val endOfWork = LocalDateTime.of(now.toLocalDate(), LocalTime.of(18, 0))
        val minutesToEnd = Duration.between(now, endOfWork).toMinutes()

        when {
            remaining <= 0 -> {
                showNotification(
                    "今日已达标 ✓",
                    "累计 ${WifiUtils.formatDuration(totalMs)}"
                )
            }
            remaining <= 60 * 60_000 && minutesToEnd in 60..240 -> {
                showNotification(
                    "还差 ${WifiUtils.formatDuration(remaining)}",
                    "加油！距下班还有 ${minutesToEnd}分钟"
                )
            }
            minutesToEnd in 0..30 && remaining > 0 -> {
                showNotification(
                    "距下班仅 ${minutesToEnd}分钟",
                    "还差 ${WifiUtils.formatDuration(remaining)}，注意达标"
                )
            }
        }

        return Result.success()
    }

    private fun showNotification(title: String, content: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                applicationContext.getString(R.string.channel_reminder),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
