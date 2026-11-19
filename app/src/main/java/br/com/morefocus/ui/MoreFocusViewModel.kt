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
    val showUsageNotification: Boolean = true,
    val instagramReelsEnabled: Boolean = true,
    val hideFeedReels: Boolean = false,
    val hideFeedShorts: Boolean = false,
    val hardBlockVerticalVideos: Boolean = false,
    val youtubeShortsEnabled: Boolean = true,
    val xVideosEnabled: Boolean = true,
    val facebookReelsEnabled: Boolean = true,
    val kwaiFeedEnabled: Boolean = true,
    val tikTokForYouEnabled: Boolean = true,
    val exponentialPauseLockEnabled: Boolean = true,
    val dailyVerticalVideosLimitSeconds: Int = 15 * 60,
    val dailyVerticalVideosUsageMillis: Long = 0L,
    val dailyVerticalVideosLimitSecondOptions: List<Int> = emptyList(),
    val appLimitRules: List<AppLimitRule> = emptyList(),
    val selectableApps: List<SelectableApp> = emptyList(),
    val appLimitSecondOptions: List<Int> = emptyList()
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

    fun setShowUsageNotification(show: Boolean) {
        settingsRepository.setShowUsageNotification(show)
        refresh()
    }

    fun setInstagramReelsEnabled(enabled: Boolean) {
        settingsRepository.setInstagramReelsEnabled(enabled)
        refresh()
    }

    fun setHideFeedReels(enabled: Boolean) {
        settingsRepository.setHideFeedReels(enabled)
        refresh()
    }

    fun setHideFeedShorts(enabled: Boolean) {
        settingsRepository.setHideFeedShorts(enabled)
        refresh()
    }

    fun setHardBlockVerticalVideos(enabled: Boolean) {
        settingsRepository.setHardBlockVerticalVideos(enabled)
        refresh()
    }

    fun setYouTubeShortsEnabled(enabled: Boolean) {
        settingsRepository.setYouTubeShortsEnabled(enabled)
        refresh()
    }

    fun setTikTokForYouEnabled(enabled: Boolean) {
        settingsRepository.setTikTokForYouEnabled(enabled)
        refresh()
    }

    fun setXVideosEnabled(enabled: Boolean) {
        settingsRepository.setXVideosEnabled(enabled)
        refresh()
    }

    fun setFacebookReelsEnabled(enabled: Boolean) {
        settingsRepository.setFacebookReelsEnabled(enabled)
        refresh()
    }

    fun setKwaiFeedEnabled(enabled: Boolean) {
        settingsRepository.setKwaiFeedEnabled(enabled)
        refresh()
    }

    fun setExponentialPauseLockEnabled(enabled: Boolean) {
        settingsRepository.setExponentialPauseLockEnabled(enabled)
        refresh()
    }

    fun setDailyVerticalVideosLimitSeconds(limitSeconds: Int) {
        settingsRepository.setDailyVerticalVideosLimitSeconds(limitSeconds)
        refresh()
    }

    fun addAppLimit(packageName: String, limitSeconds: Int) {
        val selectedApp = installedAppsRepository.getSelectableApps()
            .firstOrNull { it.packageName == packageName }
        settingsRepository.setAppLimit(
            packageName = packageName,
            appName = selectedApp?.displayName ?: packageName,
            limitSeconds = limitSeconds
        )
        refresh()
    }

    fun setAppLimitSeconds(packageName: String, limitSeconds: Int) {
        settingsRepository.setAppLimitSeconds(packageName, limitSeconds)
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
            showUsageNotification = settings.showUsageNotification,
            instagramReelsEnabled = settings.instagramReelsEnabled,
            hideFeedReels = settings.hideFeedReels,
            hideFeedShorts = settings.hideFeedShorts,
            hardBlockVerticalVideos = settings.hardBlockVerticalVideos,
            youtubeShortsEnabled = settings.youtubeShortsEnabled,
            xVideosEnabled = settings.xVideosEnabled,
            facebookReelsEnabled = settings.facebookReelsEnabled,
            kwaiFeedEnabled = settings.kwaiFeedEnabled,
            tikTokForYouEnabled = settings.tikTokForYouEnabled,
            exponentialPauseLockEnabled = settings.exponentialPauseLockEnabled,
            dailyVerticalVideosLimitSeconds = settings.dailyVerticalVideosLimitSeconds,
            dailyVerticalVideosUsageMillis = blockSettings.dailyVerticalVideosUsageMillis,
            dailyVerticalVideosLimitSecondOptions =
                settingsRepository.dailyVerticalVideosLimitSecondOptions,
            appLimitRules = settings.appLimitRules,
            selectableApps = installedAppsRepository.getSelectableApps(),
            appLimitSecondOptions = settingsRepository.appLimitSecondOptions
        )
    }
}
