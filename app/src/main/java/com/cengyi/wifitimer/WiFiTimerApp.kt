package com.cengyi.wifitimer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cengyi.wifitimer.service.DailyCheckWorker
import com.cengyi.wifitimer.service.WiFiMonitorService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WiFiTimerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        WiFiMonitorService.start(this)
        DailyCheckWorker.schedule(this)
    }
}
