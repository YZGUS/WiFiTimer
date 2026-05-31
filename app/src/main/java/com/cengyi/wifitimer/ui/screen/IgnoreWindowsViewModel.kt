package com.cengyi.wifitimer.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cengyi.wifitimer.data.local.IgnoreWindow
import com.cengyi.wifitimer.data.repository.IgnoreWindowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class IgnoreWindowEditState(
    val id: Long = 0,
    val label: String = "",
    val startHour: Int = 12,
    val startMinute: Int = 0,
    val endHour: Int = 13,
    val endMinute: Int = 0,
    val repeatDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val enabled: Boolean = true,
    val isNew: Boolean = true
)

@HiltViewModel
class IgnoreWindowsViewModel @Inject constructor(
    private val repo: IgnoreWindowRepository
) : ViewModel() {

    val windows: StateFlow<List<IgnoreWindow>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editState = MutableStateFlow<IgnoreWindowEditState?>(null)
    val editState: StateFlow<IgnoreWindowEditState?> = _editState.asStateFlow()

    fun addWindow(
        label: String,
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int,
        repeatDays: Set<DayOfWeek>
    ) {
        viewModelScope.launch {
            repo.insert(
                IgnoreWindow(
                    label = label,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    repeatDays = repeatDays
                )
            )
        }
    }

    fun updateWindow(window: IgnoreWindow) {
        viewModelScope.launch { repo.update(window) }
    }

    fun deleteWindow(id: Long) {
        viewModelScope.launch { repo.deleteById(id) }
    }

    fun toggleEnabled(window: IgnoreWindow) {
        viewModelScope.launch { repo.update(window.copy(enabled = !window.enabled)) }
    }

    fun startEdit(window: IgnoreWindow) {
        _editState.value = IgnoreWindowEditState(
            id = window.id,
            label = window.label,
            startHour = window.startHour,
            startMinute = window.startMinute,
            endHour = window.endHour,
            endMinute = window.endMinute,
            repeatDays = window.repeatDays,
            enabled = window.enabled,
            isNew = false
        )
    }

    fun startNew() {
        _editState.value = IgnoreWindowEditState()
    }

    fun clearEdit() {
        _editState.value = null
    }

    fun saveFromDialog(
        label: String,
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int,
        repeatDays: Set<DayOfWeek>
    ) {
        val state = _editState.value ?: return
        viewModelScope.launch {
            if (state.isNew) {
                repo.insert(
                    IgnoreWindow(
                        label = label,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        repeatDays = repeatDays,
                        enabled = true
                    )
                )
            } else {
                repo.update(
                    IgnoreWindow(
                        id = state.id,
                        label = label,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        repeatDays = repeatDays,
                        enabled = state.enabled
                    )
                )
            }
        }
        _editState.value = null
    }
}
