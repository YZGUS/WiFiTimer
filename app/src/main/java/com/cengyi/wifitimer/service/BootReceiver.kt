package com.cengyi.wifitimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WiFiMonitorService.start(context)
            DailyCheckWorker.schedule(context)
            MonitorCheckWorker.schedule(context)
        }
    }
}
