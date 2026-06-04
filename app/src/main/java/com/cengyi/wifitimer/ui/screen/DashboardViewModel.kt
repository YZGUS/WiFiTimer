package com.cengyi.wifitimer.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cengyi.wifitimer.data.local.WiFiSession
import com.cengyi.wifitimer.data.repository.IgnoreWindowRepository
import com.cengyi.wifitimer.data.repository.SessionRepository
import com.cengyi.wifitimer.data.repository.TargetConfigRepository
import com.cengyi.wifitimer.data.repository.WhitelistRepository
import com.cengyi.wifitimer.service.ConnectionState
import com.cengyi.wifitimer.service.WiFiMonitorService
import com.cengyi.wifitimer.util.IgnoreWindowCalculator
import com.cengyi.wifitimer.util.TimeUtils
import com.cengyi.wifitimer.util.WifiUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EmptyReason { NO_WHITELIST, NOT_CONNECTED, CONNECTED_NO_TIME }

data class DashboardUiState(
    val todayEffectiveMs: Long = 0L,
    val targetMs: Long = WifiUtils.DEFAULT_TARGET_MS,
    val targetMinutes: Int = WifiUtils.DEFAULT_TARGET_MINUTES,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val sessions: List<WiFiSession> = emptyList(),
    val progress: Float = 0f,
    val isServiceRunning: Boolean = false,
    val whitelistCount: Int = 0,
    val isFrozen: Boolean = false
) {
    val isReached: Boolean get() = todayEffectiveMs >= targetMs
    val remainingMs: Long get() = maxOf(0L, targetMs - todayEffectiveMs)
    val emptyReason: EmptyReason?
        get() = when {
            whitelistCount == 0 -> EmptyReason.NO_WHITELIST
            connectionState is ConnectionState.Disconnected && sessions.isEmpty() -> EmptyReason.NOT_CONNECTED
            connectionState is ConnectionState.Connected && sessions.isEmpty() -> EmptyReason.CONNECTED_NO_TIME
            else -> null
        }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val targetConfigRepo: TargetConfigRepository,
    private val whitelistRepo: WhitelistRepository,
    private val ignoreWindowRepo: IgnoreWindowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val tickFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    init {
        viewModelScope.launch {
            val todayStr = sessionRepo.todayDate()
            val currentDateString = MutableStateFlow(todayStr)

            viewModelScope.launch {
                tickFlow
                    .map { TimeUtils.toDateStr(it) }
                    .distinctUntilChanged()
                    .collect { currentDateString.value = it }
            }

            val ignoreWindowsFlow = ignoreWindowRepo.getAll()
                .map { list -> list.filter { it.enabled } }

            val dbFlow = currentDateString.flatMapLatest { dateStr ->
                combine(
                    sessionRepo.getEffectiveTotalFlow(dateStr),
                    sessionRepo.getSessionsByDate(dateStr)
                ) { totalMs, sessions -> totalMs to sessions }
            }.distinctUntilChanged()

            val coreFlow = combine(
                dbFlow,
                targetConfigRepo.getTargetMinutesFlow(),
                WiFiMonitorService.connectionState
            ) { (totalMs, sessions), targetMinutes, connectionState ->
                RawCoreState(totalMs, sessions, targetMinutes, connectionState)
            }

            val enrichedFlow = combine(
                coreFlow,
                ignoreWindowsFlow,
                tickFlow
            ) { raw, windows, _ ->
                val now = System.currentTimeMillis()
                val targetMs = raw.targetMinutes * 60_000L
                val isFrozen = raw.connectionState is ConnectionState.Connected &&
                    IgnoreWindowCalculator.isCurrentlyInIgnoreWindow(now, windows)

                val effectiveMs = if (raw.connectionState is ConnectionState.Connected) {
                    val activeEffective = IgnoreWindowCalculator.computeEffectiveDuration(
                        raw.connectionState.startTime,
                        now,
                        windows
                    )
                    raw.totalMs + activeEffective
                } else {
                    raw.totalMs
                }

                CoreState(
                    effectiveMs = effectiveMs,
                    targetMs = targetMs,
                    targetMinutes = raw.targetMinutes,
                    connectionState = raw.connectionState,
                    sessions = raw.sessions,
                    progress = if (targetMs > 0) (effectiveMs.toFloat() / targetMs).coerceIn(0f, 1f) else 0f,
                    isFrozen = isFrozen
                )
            }

            combine(
                enrichedFlow,
                whitelistRepo.getEnabled().map { it.size },
                WiFiMonitorService.serviceRunning
            ) { core, wlCount, svcRunning ->
                DashboardUiState(
                    todayEffectiveMs = core.effectiveMs,
                    targetMs = core.targetMs,
                    targetMinutes = core.targetMinutes,
                    connectionState = core.connectionState,
                    sessions = core.sessions,
                    progress = core.progress,
                    isServiceRunning = svcRunning,
                    whitelistCount = wlCount,
                    isFrozen = core.isFrozen
                )
            }.collect { _uiState.value = it }
        }
    }

    fun updateTarget(hours: Int, minutes: Int) {
        val totalMinutes = hours * 60 + minutes
        if (totalMinutes <= 0) return
        viewModelScope.launch {
            targetConfigRepo.updateTargetMinutes(totalMinutes)
        }
    }
}

private data class RawCoreState(
    val totalMs: Long,
    val sessions: List<WiFiSession>,
    val targetMinutes: Int,
    val connectionState: ConnectionState
)

private data class CoreState(
    val effectiveMs: Long,
    val targetMs: Long,
    val targetMinutes: Int,
    val connectionState: ConnectionState,
    val sessions: List<WiFiSession>,
    val progress: Float,
    val isFrozen: Boolean
)