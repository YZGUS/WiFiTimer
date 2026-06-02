package com.cengyi.wifitimer.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cengyi.wifitimer.data.repository.TargetConfigRepository
import com.cengyi.wifitimer.data.repository.UserPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val targetConfigRepo: TargetConfigRepository,
    private val userPrefsRepo: UserPrefsRepository
) : ViewModel() {

    private val _targetHours = MutableStateFlow(9)
    val targetHours: StateFlow<Int> = _targetHours

    private val _targetMinutes = MutableStateFlow(30)
    val targetMinutes: StateFlow<Int> = _targetMinutes

    fun setTargetHours(hours: Int) { _targetHours.value = hours }
    fun setTargetMinutes(minutes: Int) { _targetMinutes.value = minutes }

    fun completeOnboarding() {
        viewModelScope.launch {
            targetConfigRepo.updateTargetMinutes(_targetHours.value * 60 + _targetMinutes.value)
            userPrefsRepo.setOnboardingCompleted()
        }
    }
}
