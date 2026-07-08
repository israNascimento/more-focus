package br.com.morefocus.ui

import androidx.lifecycle.ViewModel
import br.com.morefocus.data.AccessibilityServiceRepository
import br.com.morefocus.data.MoreFocusSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MoreFocusUiState(
    val serviceEnabled: Boolean = false,
    val focusEnabled: Boolean = true,
    val instagramReelsEnabled: Boolean = true,
    val dailyReelsLimitMinutes: Int = 15,
    val dailyReelsLimitMinuteOptions: List<Int> = emptyList()
)

class MoreFocusViewModel(
    private val settingsRepository: MoreFocusSettingsRepository,
    private val accessibilityServiceRepository: AccessibilityServiceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(loadUiState())
    val uiState: StateFlow<MoreFocusUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = loadUiState()
    }

    fun setFocusEnabled(enabled: Boolean) {
        settingsRepository.setFocusEnabled(enabled)
        refresh()
    }

    fun setInstagramReelsEnabled(enabled: Boolean) {
        settingsRepository.setInstagramReelsEnabled(enabled)
        refresh()
    }

    fun setDailyReelsLimitMinutes(limitMinutes: Int) {
        settingsRepository.setDailyReelsLimitMinutes(limitMinutes)
        refresh()
    }

    private fun loadUiState(): MoreFocusUiState {
        val settings = settingsRepository.getSettings()
        return MoreFocusUiState(
            serviceEnabled = accessibilityServiceRepository.isMoreFocusAccessibilityServiceEnabled(),
            focusEnabled = settings.focusEnabled,
            instagramReelsEnabled = settings.instagramReelsEnabled,
            dailyReelsLimitMinutes = settings.dailyReelsLimitMinutes,
            dailyReelsLimitMinuteOptions = settingsRepository.dailyReelsLimitMinuteOptions
        )
    }
}
