package com.cengyi.wifitimer.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cengyi.wifitimer.data.local.WiFiSession
import com.cengyi.wifitimer.data.repository.SessionRepository
import com.cengyi.wifitimer.data.repository.TargetConfigRepository
import com.cengyi.wifitimer.service.ConnectionState
import com.cengyi.wifitimer.service.WiFiMonitorService
import com.cengyi.wifitimer.util.WifiUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val todayEffectiveMs: Long = 0L,
    val targetMs: Long = WifiUtils.DEFAULT_TARGET_MS,
    val targetMinutes: Int = WifiUtils.DEFAULT_TARGET_MINUTES,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val sessions: List<WiFiSession> = emptyList(),
    val progress: Float = 0f
) {
    val isReached: Boolean get() = todayEffectiveMs >= targetMs
    val remainingMs: Long get() = maxOf(0L, targetMs - todayEffectiveMs)
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val targetConfigRepo: TargetConfigRepository
) : ViewModel() {

    private val todayDate = sessionRepo.todayDate()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val tickFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(10_000)
        }
    }

    init {
        viewModelScope.launch {
            combine(
                sessionRepo.getEffectiveTotalFlow(todayDate),
                sessionRepo.getSessionsByDate(todayDate),
                targetConfigRepo.getTargetMinutesFlow(),
                WiFiMonitorService.connectionState,
                tickFlow
            ) { totalMs, sessions, targetMinutes, connectionState, _ ->
                val targetMs = targetMinutes * 60_000L
                val effectiveMs = if (connectionState is ConnectionState.Connected) {
                    val activeMs = System.currentTimeMillis() - connectionState.startTime
                    totalMs + activeMs
                } else {
                    totalMs
                }
                DashboardUiState(
                    todayEffectiveMs = effectiveMs,
                    targetMs = targetMs,
                    targetMinutes = targetMinutes,
                    connectionState = connectionState,
                    sessions = sessions,
                    progress = if (targetMs > 0) (effectiveMs.toFloat() / targetMs).coerceIn(0f, 1f) else 0f
                )
            }.collect { _uiState.value = it }
        }
    }

    fun updateTarget(hours: Int, minutes: Int) {
        viewModelScope.launch {
            targetConfigRepo.updateTargetMinutes(hours * 60 + minutes)
        }
    }
}
