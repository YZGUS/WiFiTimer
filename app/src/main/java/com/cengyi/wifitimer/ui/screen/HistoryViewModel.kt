package com.cengyi.wifitimer.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cengyi.wifitimer.data.local.DailyStats
import com.cengyi.wifitimer.data.local.WiFiSession
import com.cengyi.wifitimer.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HistoryUiState(
    val selectedDate: String = "",
    val stats: DailyStats? = null,
    val sessions: List<WiFiSession> = emptyList(),
    val weekStats: List<DailyStats> = emptyList()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepo: SessionRepository
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        selectDate(LocalDate.now().format(dateFormatter))
    }

    fun selectDate(date: String) {
        viewModelScope.launch {
            val stats = sessionRepo.getDailyStats(date)
            val sessions = sessionRepo.getSessionsByDateList(date)
            val today = LocalDate.parse(date, dateFormatter)
            val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            val weekEnd = weekStart.plusDays(6)
            val weekStats = sessionRepo.getHistory(
                weekStart.format(dateFormatter),
                weekEnd.format(dateFormatter)
            )
            _uiState.value = HistoryUiState(
                selectedDate = date,
                stats = stats,
                sessions = sessions,
                weekStats = weekStats
            )
        }
    }

    fun previousDay() {
        val current = LocalDate.parse(_uiState.value.selectedDate, dateFormatter)
        selectDate(current.minusDays(1).format(dateFormatter))
    }

    fun nextDay() {
        val current = LocalDate.parse(_uiState.value.selectedDate, dateFormatter)
        val tomorrow = current.plusDays(1)
        if (!tomorrow.isAfter(LocalDate.now())) {
            selectDate(tomorrow.format(dateFormatter))
        }
    }
}
