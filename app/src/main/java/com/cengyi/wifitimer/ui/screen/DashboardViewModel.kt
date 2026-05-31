package com.cengyi.wifitimer.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cengyi.wifitimer.data.local.WiFiSession
import com.cengyi.wifitimer.data.repository.IgnoreWindowRepository
import com.cengyi.wifitimer.data.repository.SessionRepository
import com.cengyi.wifitimer.data.repository.WhitelistRepository
import com.cengyi.wifitimer.service.ConnectionState
import com.cengyi.wifitimer.service.WiFiMonitorService
import com.cengyi.wifitimer.util.WifiUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val todayEffectiveMs: Long = 0L,
    val targetMs: Long = WifiUtils.DEFAULT_TARGET_MS,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val sessions: List<WiFiSession> = emptyList(),
    val isServiceRunning: Boolean = false,
    val progress: Float = 0f
) {
    val isReached: Boolean get() = todayEffectiveMs >= targetMs
    val remainingMs: Long get() = maxOf(0L, targetMs - todayEffectiveMs)
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val whitelistRepo: WhitelistRepository,
    private val ignoreWindowRepo: IgnoreWindowRepository
) : ViewModel() {

    private val todayDate = sessionRepo.todayDate()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionRepo.getEffectiveTotalFlow(todayDate),
                sessionRepo.getSessionsByDate(todayDate),
                whitelistRepo.getEnabled(),
                WiFiMonitorService.serviceRunning,
                WiFiMonitorService.connectionState
            ) { totalMs, sessions, enabledList, serviceRunning, connectionState ->
                val targetMs = enabledList.minOfOrNull { it.targetMinutes * 60_000L }
                    ?: WifiUtils.DEFAULT_TARGET_MS
                DashboardUiState(
                    todayEffectiveMs = totalMs,
                    targetMs = targetMs,
                    connectionState = connectionState,
                    sessions = sessions,
                    isServiceRunning = serviceRunning,
                    progress = if (targetMs > 0) (totalMs.toFloat() / targetMs).coerceIn(0f, 1f) else 0f
                )
            }.collect { _uiState.value = it }
        }
    }
}
