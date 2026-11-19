package br.com.morefocus.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import br.com.morefocus.data.AccessibilityServiceRepository
import br.com.morefocus.data.AndroidAccessibilityServiceRepository
import br.com.morefocus.data.AndroidInstalledAppsRepository
import br.com.morefocus.data.InstalledAppsRepository
import br.com.morefocus.data.MoreFocusSettingsRepository
import br.com.morefocus.data.SharedPreferencesMoreFocusSettingsRepository
import br.com.morefocus.notifications.DailyUsageNotification
import br.com.morefocus.settings.MoreFocusPreferences
import br.com.morefocus.ui.MoreFocusViewModel

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = MoreFocusPreferences(applicationContext)

    val dailyUsageNotification = DailyUsageNotification(applicationContext, preferences)

    val settingsRepository: MoreFocusSettingsRepository =
        SharedPreferencesMoreFocusSettingsRepository(preferences)

    val accessibilityServiceRepository: AccessibilityServiceRepository =
        AndroidAccessibilityServiceRepository(applicationContext)

    private val installedAppsRepository: InstalledAppsRepository =
        AndroidInstalledAppsRepository(applicationContext)

    val moreFocusViewModelFactory: ViewModelProvider.Factory =
        MoreFocusViewModelFactory(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = accessibilityServiceRepository,
            installedAppsRepository = installedAppsRepository
        )
}

private class MoreFocusViewModelFactory(
    private val settingsRepository: MoreFocusSettingsRepository,
    private val accessibilityServiceRepository: AccessibilityServiceRepository,
    private val installedAppsRepository: InstalledAppsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoreFocusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoreFocusViewModel(
                settingsRepository = settingsRepository,
                accessibilityServiceRepository = accessibilityServiceRepository,
                installedAppsRepository = installedAppsRepository
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
