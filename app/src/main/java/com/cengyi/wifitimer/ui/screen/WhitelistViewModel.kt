package com.cengyi.wifitimer.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cengyi.wifitimer.data.local.WiFiWhitelistEntry
import com.cengyi.wifitimer.data.repository.WhitelistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhitelistViewModel @Inject constructor(
    private val repo: WhitelistRepository
) : ViewModel() {

    val entries: StateFlow<List<WiFiWhitelistEntry>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editEntry = MutableStateFlow<WiFiWhitelistEntry?>(null)
    val editEntry: StateFlow<WiFiWhitelistEntry?> = _editEntry.asStateFlow()

    fun addEntry(ssid: String, bssid: String?, alias: String) {
        viewModelScope.launch {
            repo.insert(
                WiFiWhitelistEntry(
                    ssid = ssid,
                    bssid = bssid?.ifBlank { null },
                    alias = alias
                )
            )
        }
    }

    fun updateEntry(entry: WiFiWhitelistEntry) {
        viewModelScope.launch { repo.update(entry) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repo.deleteById(id) }
    }

    fun toggleEnabled(entry: WiFiWhitelistEntry) {
        viewModelScope.launch {
            repo.update(entry.copy(enabled = !entry.enabled))
        }
    }

    fun startEdit(entry: WiFiWhitelistEntry) {
        _editEntry.value = entry
    }

    fun clearEdit() {
        _editEntry.value = null
    }
}
