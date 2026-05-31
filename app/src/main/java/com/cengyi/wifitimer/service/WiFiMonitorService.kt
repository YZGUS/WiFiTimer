package com.cengyi.wifitimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cengyi.wifitimer.R
import com.cengyi.wifitimer.data.local.WiFiSession
import com.cengyi.wifitimer.data.repository.IgnoreWindowRepository
import com.cengyi.wifitimer.data.repository.SessionRepository
import com.cengyi.wifitimer.data.repository.WhitelistRepository
import com.cengyi.wifitimer.ui.MainActivity
import com.cengyi.wifitimer.util.IgnoreWindowCalculator
import com.cengyi.wifitimer.util.TimeUtils
import com.cengyi.wifitimer.util.WifiUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class WiFiMonitorService : LifecycleService() {

    @Inject lateinit var whitelistRepo: WhitelistRepository
    @Inject lateinit var sessionRepo: SessionRepository
    @Inject lateinit var ignoreWindowRepo: IgnoreWindowRepository

    private var activeSession: ActiveSession? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        const val CHANNEL_ID = "wifi_monitor_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.cengyi.wifitimer.START_MONITOR"
        const val ACTION_STOP = "com.cengyi.wifitimer.STOP_MONITOR"

        private val _serviceRunning = MutableStateFlow(false)
        val serviceRunning: StateFlow<Boolean> = _serviceRunning

        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val connectionState: StateFlow<ConnectionState> = _connectionState

        fun start(context: Context) {
            val intent = Intent(context, WiFiMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WiFiMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                endActiveSession()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification("WiFi监控运行中", "正在初始化..."))
        _serviceRunning.value = true

        registerWifiReceiver()
        checkAndUpdateWifiState()

        return START_STICKY
    }

    override fun onDestroy() {
        endActiveSession()
        _serviceRunning.value = false
        _connectionState.value = ConnectionState.Disconnected
        try {
            unregisterReceiver(wifiReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private val wifiReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            when (intent.action) {
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    checkAndUpdateWifiState()
                }
            }
        }
    }

    private fun registerWifiReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(wifiReceiver, filter)
    }

    private fun checkAndUpdateWifiState() {
        val wifiInfo = getWifiInfo() ?: return
        val ssid = wifiInfo.first
        val bssid = wifiInfo.second

        if (WifiUtils.isUnknownSsid(ssid)) {
            // 无法获取 SSID，保持当前状态不变
            return
        }

        lifecycleScope.launch {
            val match = whitelistRepo.findMatch(ssid, bssid)
            val normalizedSsid = WifiUtils.normalizeSsid(ssid)

            if (match != null && match.enabled) {
                // 连接到目标 WiFi
                val currentActive = activeSession
                if (currentActive == null) {
                    // 新 session
                    activeSession = ActiveSession(
                        ssid = normalizedSsid,
                        bssid = bssid,
                        whitelistEntryId = match.id,
                        startTime = System.currentTimeMillis(),
                        targetMinutes = match.targetMinutes
                    )
                    _connectionState.value = ConnectionState.Connected(
                        ssid = normalizedSsid,
                        startTime = activeSession!!.startTime,
                        targetMinutes = match.targetMinutes
                    )
                } else if (currentActive.ssid != normalizedSsid ||
                    (match.bssid != null && currentActive.bssid != bssid)
                ) {
                    // 切换到不同目标 WiFi，结束旧 session，开新 session
                    endActiveSession()
                    activeSession = ActiveSession(
                        ssid = normalizedSsid,
                        bssid = bssid,
                        whitelistEntryId = match.id,
                        startTime = System.currentTimeMillis(),
                        targetMinutes = match.targetMinutes
                    )
                    _connectionState.value = ConnectionState.Connected(
                        ssid = normalizedSsid,
                        startTime = activeSession!!.startTime,
                        targetMinutes = match.targetMinutes
                    )
                }
                updateNotification()
            } else {
                // 未连接目标 WiFi
                if (activeSession != null) {
                    endActiveSession()
                }
                _connectionState.value = ConnectionState.Disconnected
                updateNotification()
            }
        }
    }

    private fun getWifiInfo(): Pair<String, String?>? {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return null

        val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (!hasWifi) return null

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo ?: return null
        val ssid = info.ssid ?: return null
        val bssid = info.bssid
        return ssid to bssid
    }

    private fun endActiveSession() {
        val session = activeSession ?: return
        activeSession = null
        val endTime = System.currentTimeMillis()
        val rawDuration = endTime - session.startTime

        if (rawDuration < WifiUtils.MIN_DURATION_MS) return

        lifecycleScope.launch {
            val windows = ignoreWindowRepo.getEnabledList()

            // 处理跨天：拆分 session
            val segments = TimeUtils.splitByDay(session.startTime, endTime)
            for ((dateStr, range) in segments) {
                val segStart = range.first
                val segEnd = range.second
                val segRaw = segEnd - segStart
                val effective = IgnoreWindowCalculator.computeEffectiveDuration(
                    segStart, segEnd, windows
                )
                if (effective > 0) {
                    sessionRepo.insertSession(
                        WiFiSession(
                            ssid = session.ssid,
                            bssid = session.bssid,
                            whitelistEntryId = session.whitelistEntryId,
                            startTime = segStart,
                            endTime = segEnd,
                            rawDurationMs = segRaw,
                            effectiveDurationMs = effective,
                            date = dateStr
                        )
                    )
                    sessionRepo.recalculateDailyStats(
                        dateStr,
                        session.targetMinutes * 60_000L
                    )
                }
            }
        }
    }

    private fun updateNotification() {
        lifecycleScope.launch {
            val today = LocalDate.now().format(dateFormatter)
            val totalMs = sessionRepo.getEffectiveTotalForDate(today)
            val session = activeSession
            val title = "WiFi监控运行中"
            val content = if (session != null) {
                val targetStr = WifiUtils.formatDuration(session.targetMinutes * 60_000L)
                val currentStr = WifiUtils.formatDuration(totalMs)
                "今日有效：$currentStr / 目标 $targetStr"
            } else {
                "当前未连接目标WiFi"
            }
            notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_monitor),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WiFi连接监控服务状态"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connected(
        val ssid: String,
        val startTime: Long,
        val targetMinutes: Int
    ) : ConnectionState()
}
