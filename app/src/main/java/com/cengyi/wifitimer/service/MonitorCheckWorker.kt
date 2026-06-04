package com.cengyi.wifitimer.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MonitorCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "monitor_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitorCheckWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder().build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        WiFiMonitorService.start(applicationContext)
        return Result.success()
    }
}