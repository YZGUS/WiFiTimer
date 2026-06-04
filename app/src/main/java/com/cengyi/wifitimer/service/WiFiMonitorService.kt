package com.cengyi.wifitimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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
    private var whitelistJob: Job? = null
    private var periodicUpdateJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiReceiver: android.content.BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var disconnectCheckJob: Job? = null
    private var endSessionJob: Job? = null
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()
    private val prefs by lazy {
        getSharedPreferences("wifi_monitor_prefs", Context.MODE_PRIVATE)
    }
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun persistActiveSession() {
        val s = activeSession ?: run {
            prefs.edit().remove("active_session_ssid").apply()
            return
        }
        prefs.edit()
            .putString("active_session_ssid", s.ssid)
            .putString("active_session_bssid", s.bssid)
            .putLong("active_session_whitelist_id", s.whitelistEntryId)
            .putLong("active_session_start_time", s.startTime)
            .putInt("active_session_target_minutes", s.targetMinutes)
            .putLong("active_session_last_confirmed", s.lastConfirmedTime)
            .apply()
    }

    private fun loadPersistedActiveSession(): ActiveSession? {
        val ssid = prefs.getString("active_session_ssid", null) ?: return null
        val bssid = prefs.getString("active_session_bssid", null)
        val whitelistId = prefs.getLong("active_session_whitelist_id", 0)
        val startTime = prefs.getLong("active_session_start_time", 0)
        val targetMinutes = prefs.getInt("active_session_target_minutes", 0)
        val lastConfirmed = prefs.getLong("active_session_last_confirmed", 0)
        if (startTime == 0L || targetMinutes == 0) return null
        return ActiveSession(
            ssid = ssid,
            bssid = bssid,
            whitelistEntryId = whitelistId,
            startTime = startTime,
            targetMinutes = targetMinutes,
            lastConfirmedTime = lastConfirmed
        )
    }

    private fun clearPersistedActiveSession() {
        prefs.edit().remove("active_session_ssid").apply()
    }

    companion object {
        const val CHANNEL_ID = "wifi_monitor_service"
        const val CHANNEL_DISCONNECT = "wifi_disconnect_alert_v2"
        const val NOTIFICATION_ID = 1001
        const val DISCONNECT_NOTIFICATION_ID = 2001
        const val ACTION_START = "com.cengyi.wifitimer.START_MONITOR"
        private const val DISCONNECT_CONFIRM_DELAY_MS = 3000L

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

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_monitor_title), getString(R.string.notification_monitor_initializing)))
        _serviceRunning.value = true

        whitelistJob?.cancel()
        periodicUpdateJob?.cancel()
        disconnectCheckJob?.cancel()

        registerWifiReceiver()
        registerNetworkCallback()
        observeWhitelistChanges()
        startPeriodicUpdateLoop()
        restoreOrProcessWifiState()

        MonitorCheckWorker.schedule(this)

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        whitelistJob?.cancel()
        periodicUpdateJob?.cancel()
        disconnectCheckJob?.cancel()
        endActiveSession()
        runBlocking {
            withTimeout(2000) { endSessionJob?.join() }
        }
        saveScope.cancel()
        releaseWakeLock()
        unregisterWifiReceiver()
        unregisterNetworkCallback()
        _serviceRunning.value = false
        _connectionState.value = ConnectionState.Disconnected
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
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

    // ---- WakeLock ----

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "wifitimer::monitor"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    // ---- NetworkCallback (primary detection, works in Doze) ----

    private fun registerNetworkCallback() {
        try {
            unregisterNetworkCallback()
        } catch (_: Exception) {}

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "NetworkCallback onAvailable")
                processWifiState()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "NetworkCallback onLost")
                processWifiState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Log.d(TAG, "NetworkCallback onCapabilitiesChanged")
                processWifiState()
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)
        networkCallback = callback
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }

    // ---- BroadcastReceiver (secondary detection) ----

    private fun registerWifiReceiver() {
        try {
            unregisterWifiReceiver()
        } catch (_: Exception) {}

        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                Log.d(TAG, "Broadcast received: ${intent.action}")
                when (intent.action) {
                    WifiManager.NETWORK_STATE_CHANGED_ACTION,
                    ConnectivityManager.CONNECTIVITY_ACTION -> {
                        processWifiState()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(receiver, filter)
        wifiReceiver = receiver
    }

    private fun unregisterWifiReceiver() {
        wifiReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        wifiReceiver = null
    }

    // ---- Whitelist observer ----

    private fun observeWhitelistChanges() {
        whitelistJob?.cancel()
        whitelistJob = lifecycleScope.launch {
            whitelistRepo.getEnabled()
                .distinctUntilChanged()
                .collect {
                    Log.d(TAG, "Whitelist changed, re-checking WiFi state")
                    processWifiState()
                }
        }
    }

    // ---- Periodic update (notification + widget + session confirmation) ----

    private fun startPeriodicUpdateLoop() {
        periodicUpdateJob?.cancel()
        periodicUpdateJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                confirmActiveSession()
                persistActiveSession()
                updateNotification()
                TimerWidget.triggerUpdate(this@WiFiMonitorService)
            }
        }
    }

    private fun confirmActiveSession() {
        val session = activeSession ?: return
        val wifiInfo = getWifiInfo()
        val now = System.currentTimeMillis()
        if (wifiInfo != null) {
            val ssid = wifiInfo.first
            if (!WifiUtils.isUnknownSsid(ssid) && ssid == session.ssid) {
                activeSession = session.copy(lastConfirmedTime = now)
                Log.d(TAG, "Session confirmed at $now for ${session.ssid}")
            }
        }
        if (wakeLock?.isHeld == true) {
            wakeLock?.acquire(10 * 60 * 1000L)
            Log.d(TAG, "WakeLock re-acquired")
        }
    }

    // ---- WiFi state check (entry point, launches coroutine with mutex) ----

    private fun restoreOrProcessWifiState() {
        lifecycleScope.launch {
            stateMutex.withLock {
                handleWifiStateInternal()
            }
        }
    }

    private fun processWifiState() {
        lifecycleScope.launch {
            stateMutex.withLock {
                handleWifiStateInternal()
            }
        }
    }

    private suspend fun handleWifiStateInternal() {
        Log.d(TAG, "handleWifiStateInternal, activeSession=${activeSession?.ssid}")

        val wifiInfo = getWifiInfo()
        if (wifiInfo == null) {
            Log.d(TAG, "No WiFi connection detected")
            handlePotentialDisconnect(reason = "no_wifi")
            return
        }

        val ssid = wifiInfo.first
        val bssid = wifiInfo.second
        Log.d(TAG, "WiFi info: ssid=$ssid, bssid=$bssid")

        if (WifiUtils.isUnknownSsid(ssid)) {
            Log.w(TAG, "Unknown SSID detected, treating as disconnect check")
            handlePotentialDisconnect(reason = "unknown_ssid")
            return
        }

        val match = whitelistRepo.findMatch(ssid, bssid)
        val normalizedSsid = WifiUtils.normalizeSsid(ssid)
        Log.d(TAG, "Whitelist match for '$normalizedSsid': ${match?.ssid}")

        if (match != null && match.enabled) {
            disconnectCheckJob?.cancel()
            disconnectCheckJob = null

            val currentActive = activeSession
            if (currentActive == null) {
                val now = System.currentTimeMillis()
                activeSession = ActiveSession(
                    ssid = normalizedSsid,
                    bssid = bssid,
                    whitelistEntryId = match.id,
                    startTime = now,
                    targetMinutes = match.targetMinutes,
                    lastConfirmedTime = now
                )
                persistActiveSession()
                _connectionState.value = ConnectionState.Connected(
                    ssid = normalizedSsid,
                    startTime = activeSession!!.startTime,
                    targetMinutes = match.targetMinutes
                )
                acquireWakeLock()
                Log.d(TAG, "New session started: $normalizedSsid")
            } else if (currentActive.ssid != normalizedSsid ||
                (match.bssid != null && currentActive.bssid != bssid)
            ) {
                val wasSsid = currentActive.ssid
                endActiveSession()
                sendDisconnectNotification(wasSsid)
                val now = System.currentTimeMillis()
                activeSession = ActiveSession(
                    ssid = normalizedSsid,
                    bssid = bssid,
                    whitelistEntryId = match.id,
                    startTime = now,
                    targetMinutes = match.targetMinutes,
                    lastConfirmedTime = now
                )
                persistActiveSession()
                _connectionState.value = ConnectionState.Connected(
                    ssid = normalizedSsid,
                    startTime = activeSession!!.startTime,
                    targetMinutes = match.targetMinutes
                )
                acquireWakeLock()
                Log.d(TAG, "Session switched: $wasSsid -> $normalizedSsid")
            } else {
                activeSession = currentActive.copy(lastConfirmedTime = System.currentTimeMillis())
                persistActiveSession()
            }
            updateNotification()
        } else {
            handlePotentialDisconnect(reason = "not_in_whitelist")
        }
    }

    // ---- Disconnect with delay confirmation ----

    private suspend fun handlePotentialDisconnect(reason: String) {
        if (activeSession == null) {
            Log.d(TAG, "No active session, marking disconnected ($reason)")
            _connectionState.value = ConnectionState.Disconnected
            releaseWakeLock()
            updateNotification()
            return
        }

        disconnectCheckJob?.cancel()
        Log.d(TAG, "Potential disconnect ($reason), scheduling ${DISCONNECT_CONFIRM_DELAY_MS}ms confirmation for ${activeSession?.ssid}")

        disconnectCheckJob = lifecycleScope.launch {
            delay(DISCONNECT_CONFIRM_DELAY_MS)
            stateMutex.withLock {
                val wifiInfo = getWifiInfo()
                if (wifiInfo != null) {
                    val ssid = wifiInfo.first
                    if (!WifiUtils.isUnknownSsid(ssid)) {
                        val match = whitelistRepo.findMatch(ssid, wifiInfo.second)
                        val normalizedSsid = WifiUtils.normalizeSsid(ssid)
                        if (match != null && match.enabled && normalizedSsid == activeSession?.ssid) {
                            Log.d(TAG, "Reconnected to same SSID after delay, resuming session: $normalizedSsid")
                            activeSession = activeSession?.copy(lastConfirmedTime = System.currentTimeMillis())
                            persistActiveSession()
                            _connectionState.value = ConnectionState.Connected(
                                ssid = normalizedSsid,
                                startTime = activeSession!!.startTime,
                                targetMinutes = activeSession!!.targetMinutes
                            )
                            updateNotification()
                            return@withLock
                        }
                    }
                }

                val wasSsid = activeSession?.ssid ?: return@withLock
                Log.d(TAG, "Disconnect confirmed after delay for $wasSsid")
                endActiveSession()
                sendDisconnectNotification(wasSsid)
                _connectionState.value = ConnectionState.Disconnected
                releaseWakeLock()
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

    // ---- Session end with lastConfirmedTime ----

    private fun endActiveSession() {
        val session = activeSession ?: return
        activeSession = null
        clearPersistedActiveSession()

        val endTime = session.lastConfirmedTime
        val rawDuration = endTime - session.startTime

        if (rawDuration < WifiUtils.MIN_DURATION_MS) return

        endSessionJob = saveScope.launch {
            val windows = ignoreWindowRepo.getEnabledList()
            val targetMinutes = targetConfigRepo.getTargetMinutes()

            val segments = TimeUtils.splitByDay(session.startTime, endTime)
            for ((dateStr, range) in segments) {
                val segStart = range.first
                val segEnd = range.second
                val segRaw = segEnd - segStart
                val segDate = LocalDate.parse(dateStr, dateFormatter)
                val effective = IgnoreWindowCalculator.computeEffectiveDuration(
                    segStart, segEnd, windows, segDate
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

    // ---- Notification ----

    private fun updateNotification() {
        lifecycleScope.launch {
            val today = LocalDate.now().format(dateFormatter)
            val totalMs = sessionRepo.getEffectiveTotalForDate(today)
            val session = activeSession
            val targetMinutes = targetConfigRepo.getTargetMinutes()
            val targetMs = targetMinutes * 60_000L
            val title = getString(R.string.notification_monitor_title)
            val content = if (session != null) {
                val windows = ignoreWindowRepo.getEnabledList()
                val now = System.currentTimeMillis()
                val activeEffective = IgnoreWindowCalculator.computeEffectiveDuration(
                    session.startTime, now, windows
                )
                val currentEffective = totalMs + activeEffective
                val currentStr = WifiUtils.formatDuration(currentEffective)
                val targetStr = WifiUtils.formatDuration(targetMs)
                getString(R.string.notification_monitor_connected, currentStr, targetStr)
            } else {
                getString(R.string.notification_monitor_disconnected)
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
                getString(R.string.channel_disconnect),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_disconnect)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendDisconnectNotification(ssid: String) {
        Log.d(TAG, "Sending disconnect notification for SSID: $ssid")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_DISCONNECT)
            .setContentTitle(getString(R.string.notification_disconnect_title))
            .setContentText(getString(R.string.notification_disconnect_text, ssid))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        notificationManager.notify(DISCONNECT_NOTIFICATION_ID, notification)

        lifecycleScope.launch {
            delay(3000)
            updateNotification()
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