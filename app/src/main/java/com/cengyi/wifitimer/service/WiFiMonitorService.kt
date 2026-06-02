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
import android.util.Log
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
import com.cengyi.wifitimer.widget.TimerWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class WiFiMonitorService : LifecycleService() {

    @Inject lateinit var whitelistRepo: WhitelistRepository
    @Inject lateinit var sessionRepo: SessionRepository
    @Inject lateinit var ignoreWindowRepo: IgnoreWindowRepository
    @Inject lateinit var targetConfigRepo: com.cengyi.wifitimer.data.repository.TargetConfigRepository

    private var activeSession: ActiveSession? = null
    private var whitelistJob: kotlinx.coroutines.Job? = null
    private var widgetUpdateJob: kotlinx.coroutines.Job? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        const val CHANNEL_ID = "wifi_monitor_service"
        const val CHANNEL_DISCONNECT = "wifi_disconnect_alert"
        const val NOTIFICATION_ID = 1001
        const val DISCONNECT_NOTIFICATION_ID = 2001
        const val ACTION_START = "com.cengyi.wifitimer.START_MONITOR"

        private const val TAG = "WiFiMonitorService"

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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createDisconnectChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand, action=${intent?.action}")

        startForeground(NOTIFICATION_ID, buildNotification("WiFi监控运行中", "正在初始化..."))
        _serviceRunning.value = true

        registerWifiReceiver()
        observeWhitelistChanges()
        startWidgetUpdateLoop()
        checkAndUpdateWifiState()

        // Schedule periodic WiFi check as fallback
        MonitorCheckWorker.schedule(this)

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        whitelistJob?.cancel()
        widgetUpdateJob?.cancel()
        endActiveSession()
        _serviceRunning.value = false
        _connectionState.value = ConnectionState.Disconnected
        try {
            unregisterReceiver(wifiReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Restart service when app is swiped away from recents
        val restartIntent = Intent(this, WiFiMonitorService::class.java).apply {
            action = ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    private val wifiReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            Log.d(TAG, "Broadcast received: ${intent.action}")
            when (intent.action) {
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    checkAndUpdateWifiState()
                }
            }
        }
    }

    private fun registerWifiReceiver() {
        try {
            unregisterReceiver(wifiReceiver)
        } catch (_: Exception) {}
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(wifiReceiver, filter)
    }

    private fun observeWhitelistChanges() {
        whitelistJob?.cancel()
        whitelistJob = lifecycleScope.launch {
            whitelistRepo.getEnabled()
                .distinctUntilChanged()
                .collect {
                    Log.d(TAG, "Whitelist changed, re-checking WiFi state")
                    checkAndUpdateWifiState()
                }
        }
    }

    private fun startWidgetUpdateLoop() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                TimerWidget.triggerUpdate(this@WiFiMonitorService)
            }
        }
    }

    fun checkAndUpdateWifiState() {
        Log.d(TAG, "checkAndUpdateWifiState called, activeSession=${activeSession?.ssid}")

        val wifiInfo = getWifiInfo() ?: run {
            // No WiFi at all
            Log.d(TAG, "No WiFi connection detected")
            if (activeSession != null) {
                val wasSsid = activeSession!!.ssid
                endActiveSession()
                sendDisconnectNotification(wasSsid)
                Log.d(TAG, "Disconnected (no WiFi), sent notification for $wasSsid")
            }
            _connectionState.value = ConnectionState.Disconnected
            updateNotification()
            return
        }
        val ssid = wifiInfo.first
        val bssid = wifiInfo.second
        Log.d(TAG, "WiFi info: ssid=$ssid, bssid=$bssid")

        if (WifiUtils.isUnknownSsid(ssid)) {
            // Unknown SSID — likely no location permission on Android 10+
            // Treat as potential disconnect: end active session if any
            Log.w(TAG, "Unknown SSID detected, treating as disconnect check")
            if (activeSession != null) {
                val wasSsid = activeSession!!.ssid
                endActiveSession()
                sendDisconnectNotification(wasSsid)
                Log.d(TAG, "Disconnected (unknown SSID), sent notification for $wasSsid")
            }
            _connectionState.value = ConnectionState.Disconnected
            updateNotification()
            return
        }

        lifecycleScope.launch {
            val match = whitelistRepo.findMatch(ssid, bssid)
            val normalizedSsid = WifiUtils.normalizeSsid(ssid)
            Log.d(TAG, "Whitelist match for '$normalizedSsid': ${match?.ssid}")

            if (match != null && match.enabled) {
                val currentActive = activeSession
                if (currentActive == null) {
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
                    Log.d(TAG, "New session started: $normalizedSsid")
                } else if (currentActive.ssid != normalizedSsid ||
                    (match.bssid != null && currentActive.bssid != bssid)
                ) {
                    val wasSsid = currentActive.ssid
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
                    Log.d(TAG, "Session switched: $wasSsid -> $normalizedSsid")
                }
                updateNotification()
            } else {
                if (activeSession != null) {
                    val wasSsid = activeSession!!.ssid
                    endActiveSession()
                    sendDisconnectNotification(wasSsid)
                    Log.d(TAG, "Disconnected (not in whitelist), sent notification for $wasSsid")
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
            val targetMinutes = targetConfigRepo.getTargetMinutes()

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
                        targetMinutes * 60_000L
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
            val targetMinutes = targetConfigRepo.getTargetMinutes()
            val title = "WiFi监控运行中"
            val content = if (session != null) {
                val targetStr = WifiUtils.formatDuration(targetMinutes * 60_000L)
                val currentStr = WifiUtils.formatDuration(totalMs)
                "今日有效：$currentStr / 目标 $targetStr"
            } else {
                "当前未连接目标WiFi"
            }
            notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
            TimerWidget.triggerUpdate(this@WiFiMonitorService)
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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

    private fun createDisconnectChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DISCONNECT,
                "WiFi断开提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "离开目标WiFi时提醒"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendDisconnectNotification(ssid: String) {
        Log.d(TAG, "Sending disconnect notification for SSID: $ssid")

        // Update foreground notification to show disconnect alert (always visible)
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification("已离开目标WiFi", "已断开 $ssid 的连接")
        )

        // Also try separate high-priority notification (visible if POST_NOTIFICATIONS granted)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_DISCONNECT)
            .setContentTitle("已离开目标WiFi")
            .setContentText("已断开 $ssid 的连接")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(DISCONNECT_NOTIFICATION_ID, notification)
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
