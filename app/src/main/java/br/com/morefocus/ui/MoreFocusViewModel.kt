package br.com.morefocus.ui

import androidx.lifecycle.ViewModel
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.data.AccessibilityServiceRepository
import br.com.morefocus.data.InstalledAppsRepository
import br.com.morefocus.data.MoreFocusSettingsRepository
import br.com.morefocus.data.SelectableApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MoreFocusUiState(
    val serviceEnabled: Boolean = false,
    val focusEnabled: Boolean = true,
    val instagramReelsEnabled: Boolean = true,
    val dailyReelsLimitMinutes: Int = 15,
    val dailyReelsUsageMillis: Long = 0L,
    val dailyReelsLimitMinuteOptions: List<Int> = emptyList(),
    val appLimitRules: List<AppLimitRule> = emptyList(),
    val selectableApps: List<SelectableApp> = emptyList(),
    val appLimitMinuteOptions: List<Int> = emptyList()
)

class MoreFocusViewModel(
    private val settingsRepository: MoreFocusSettingsRepository,
    private val accessibilityServiceRepository: AccessibilityServiceRepository,
    private val installedAppsRepository: InstalledAppsRepository
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

    fun addAppLimit(packageName: String, limitMinutes: Int) {
        val selectedApp = installedAppsRepository.getSelectableApps()
            .firstOrNull { it.packageName == packageName }
        settingsRepository.setAppLimit(
            packageName = packageName,
            appName = selectedApp?.displayName ?: packageName,
            limitMinutes = limitMinutes
        )
        refresh()
    }

    fun setAppLimitMinutes(packageName: String, limitMinutes: Int) {
        settingsRepository.setAppLimitMinutes(packageName, limitMinutes)
        refresh()
    }

    fun setAppLimitEnabled(packageName: String, enabled: Boolean) {
        settingsRepository.setAppLimitEnabled(packageName, enabled)
        refresh()
    }

    fun removeAppLimit(packageName: String) {
        settingsRepository.removeAppLimit(packageName)
        refresh()
    }

    private fun loadUiState(): MoreFocusUiState {
        val settings = settingsRepository.getSettings()
        val blockSettings = settingsRepository.getBlockSettings()
        return MoreFocusUiState(
            serviceEnabled = accessibilityServiceRepository.isMoreFocusAccessibilityServiceEnabled(),
            focusEnabled = settings.focusEnabled,
            instagramReelsEnabled = settings.instagramReelsEnabled,
            dailyReelsLimitMinutes = settings.dailyReelsLimitMinutes,
            dailyReelsUsageMillis = blockSettings.dailyUsageMillis,
            dailyReelsLimitMinuteOptions = settingsRepository.dailyReelsLimitMinuteOptions,
            appLimitRules = settings.appLimitRules,
            selectableApps = installedAppsRepository.getSelectableApps(),
            appLimitMinuteOptions = settingsRepository.appLimitMinuteOptions
        )
    }
}
