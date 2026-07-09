package br.com.morefocus.ui

import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.data.AccessibilityServiceRepository
import br.com.morefocus.data.InstalledAppsRepository
import br.com.morefocus.data.MoreFocusSettings
import br.com.morefocus.data.MoreFocusSettingsRepository
import br.com.morefocus.data.SelectableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreFocusViewModelTest {
    @Test
    fun initialStateLoadsSettingsAndAccessibilityStatus() {
        val settingsRepository = FakeMoreFocusSettingsRepository(
            settings = MoreFocusSettings(
                focusEnabled = false,
                instagramReelsEnabled = false,
                dailyReelsLimitMinutes = 30,
                appLimitRules = listOf(
                    AppLimitRule(
                        packageName = "com.video",
                        appName = "Video",
                        limitMinutes = 15
                    )
                )
            ),
            dailyReelsUsageMillis = 7 * 60 * 1000L
        )
        val accessibilityRepository = FakeAccessibilityServiceRepository(enabled = true)
        val installedAppsRepository = FakeInstalledAppsRepository(
            apps = listOf(SelectableApp("com.video", "Video"))
        )

        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = accessibilityRepository,
            installedAppsRepository = installedAppsRepository
        )

        val state = viewModel.uiState.value
        assertTrue(state.serviceEnabled)
        assertFalse(state.focusEnabled)
        assertFalse(state.instagramReelsEnabled)
        assertEquals(30, state.dailyReelsLimitMinutes)
        assertEquals(7 * 60 * 1000L, state.dailyReelsUsageMillis)
        assertEquals(settingsRepository.dailyReelsLimitMinuteOptions, state.dailyReelsLimitMinuteOptions)
        assertEquals(settingsRepository.getSettings().appLimitRules, state.appLimitRules)
        assertEquals(installedAppsRepository.apps, state.selectableApps)
        assertEquals(settingsRepository.appLimitMinuteOptions, state.appLimitMinuteOptions)
    }

    @Test
    fun toggleChangesUpdateRepositoryAndUiState() {
        val settingsRepository = FakeMoreFocusSettingsRepository()
        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = FakeAccessibilityServiceRepository(enabled = false),
            installedAppsRepository = FakeInstalledAppsRepository()
        )

        viewModel.setFocusEnabled(false)
        viewModel.setInstagramReelsEnabled(false)

        assertFalse(settingsRepository.getSettings().focusEnabled)
        assertFalse(settingsRepository.getSettings().instagramReelsEnabled)
        assertFalse(viewModel.uiState.value.focusEnabled)
        assertFalse(viewModel.uiState.value.instagramReelsEnabled)
    }

    @Test
    fun invalidDailyLimitIsSanitizedByRepository() {
        val settingsRepository = FakeMoreFocusSettingsRepository()
        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = FakeAccessibilityServiceRepository(enabled = false),
            installedAppsRepository = FakeInstalledAppsRepository()
        )

        viewModel.setDailyReelsLimitMinutes(99)

        assertEquals(DEFAULT_DAILY_REELS_LIMIT_MINUTES, settingsRepository.getSettings().dailyReelsLimitMinutes)
        assertEquals(DEFAULT_DAILY_REELS_LIMIT_MINUTES, viewModel.uiState.value.dailyReelsLimitMinutes)
    }

    @Test
    fun refreshReloadsAccessibilityStatus() {
        val accessibilityRepository = FakeAccessibilityServiceRepository(enabled = false)
        val viewModel = MoreFocusViewModel(
            settingsRepository = FakeMoreFocusSettingsRepository(),
            accessibilityServiceRepository = accessibilityRepository,
            installedAppsRepository = FakeInstalledAppsRepository()
        )

        assertFalse(viewModel.uiState.value.serviceEnabled)

        accessibilityRepository.enabled = true
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.serviceEnabled)
    }

    @Test
    fun addAppLimitUsesInstalledAppNameAndRefreshesState() {
        val settingsRepository = FakeMoreFocusSettingsRepository()
        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = FakeAccessibilityServiceRepository(enabled = false),
            installedAppsRepository = FakeInstalledAppsRepository(
                apps = listOf(SelectableApp("com.video", "Video"))
            )
        )

        viewModel.addAppLimit(packageName = "com.video", limitMinutes = 30)

        val rule = viewModel.uiState.value.appLimitRules.single()
        assertEquals("com.video", rule.packageName)
        assertEquals("Video", rule.appName)
        assertEquals(30, rule.limitMinutes)
        assertTrue(rule.enabled)
    }

    @Test
    fun appLimitChangesUpdateRepositoryAndUiState() {
        val settingsRepository = FakeMoreFocusSettingsRepository(
            settings = MoreFocusSettings(
                focusEnabled = true,
                instagramReelsEnabled = true,
                dailyReelsLimitMinutes = DEFAULT_DAILY_REELS_LIMIT_MINUTES,
                appLimitRules = listOf(
                    AppLimitRule(
                        packageName = "com.video",
                        appName = "Video",
                        limitMinutes = 30
                    )
                )
            )
        )
        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = FakeAccessibilityServiceRepository(enabled = false),
            installedAppsRepository = FakeInstalledAppsRepository()
        )

        viewModel.setAppLimitMinutes("com.video", 99)
        viewModel.setAppLimitEnabled("com.video", false)

        val updatedRule = viewModel.uiState.value.appLimitRules.single()
        assertEquals(DEFAULT_DAILY_REELS_LIMIT_MINUTES, updatedRule.limitMinutes)
        assertFalse(updatedRule.enabled)

        viewModel.removeAppLimit("com.video")

        assertTrue(viewModel.uiState.value.appLimitRules.isEmpty())
    }

    private class FakeMoreFocusSettingsRepository(
        private var settings: MoreFocusSettings = MoreFocusSettings(
            focusEnabled = true,
            instagramReelsEnabled = true,
            dailyReelsLimitMinutes = DEFAULT_DAILY_REELS_LIMIT_MINUTES
        ),
        private val dailyReelsUsageMillis: Long = 0L
    ) : MoreFocusSettingsRepository {
        override val dailyReelsLimitMinuteOptions: List<Int> = DAILY_REELS_LIMIT_MINUTE_OPTIONS

        override val appLimitMinuteOptions: List<Int> = DAILY_REELS_LIMIT_MINUTE_OPTIONS

        override fun getSettings(): MoreFocusSettings = settings

        override fun getBlockSettings(nowMillis: Long): BlockSettings =
            BlockSettings(
                focusEnabled = settings.focusEnabled,
                instagramReelsEnabled = settings.instagramReelsEnabled,
                dailyLimitMinutes = settings.dailyReelsLimitMinutes,
                dailyUsageMillis = dailyReelsUsageMillis,
                appLimitRules = settings.appLimitRules
            )

        override fun setFocusEnabled(enabled: Boolean) {
            settings = settings.copy(focusEnabled = enabled)
        }

        override fun setInstagramReelsEnabled(enabled: Boolean) {
            settings = settings.copy(instagramReelsEnabled = enabled)
        }

        override fun setDailyReelsLimitMinutes(limitMinutes: Int) {
            settings = settings.copy(
                dailyReelsLimitMinutes = limitMinutes.takeIf {
                    it in dailyReelsLimitMinuteOptions
                } ?: DEFAULT_DAILY_REELS_LIMIT_MINUTES
            )
        }

        override fun setAppLimit(packageName: String, appName: String, limitMinutes: Int) {
            val sanitizedLimit = limitMinutes.takeIf {
                it in appLimitMinuteOptions
            } ?: DEFAULT_DAILY_REELS_LIMIT_MINUTES
            val newRule = AppLimitRule(
                packageName = packageName,
                appName = appName,
                limitMinutes = sanitizedLimit
            )
            settings = settings.copy(
                appLimitRules = settings.appLimitRules
                    .filterNot { it.packageName == packageName } + newRule
            )
        }

        override fun setAppLimitMinutes(packageName: String, limitMinutes: Int) {
            settings = settings.copy(
                appLimitRules = settings.appLimitRules.map { rule ->
                    if (rule.packageName == packageName) {
                        rule.copy(
                            limitMinutes = limitMinutes.takeIf {
                                it in appLimitMinuteOptions
                            } ?: DEFAULT_DAILY_REELS_LIMIT_MINUTES
                        )
                    } else {
                        rule
                    }
                }
            )
        }

        override fun setAppLimitEnabled(packageName: String, enabled: Boolean) {
            settings = settings.copy(
                appLimitRules = settings.appLimitRules.map { rule ->
                    if (rule.packageName == packageName) {
                        rule.copy(enabled = enabled)
                    } else {
                        rule
                    }
                }
            )
        }

        override fun removeAppLimit(packageName: String) {
            settings = settings.copy(
                appLimitRules = settings.appLimitRules.filterNot { it.packageName == packageName }
            )
        }

        override fun pauseFor(durationMillis: Long, nowMillis: Long) = Unit

        override fun recordDailyReelsUsage(
            startedAtMillis: Long?,
            endedAtMillis: Long
        ): DailyUsageState =
            DailyUsageState(dayKey = "2026-07-08", usedMillis = 0L)

        override fun recordDailyAppUsage(
            packageName: String,
            startedAtMillis: Long?,
            endedAtMillis: Long
        ): DailyUsageState =
            DailyUsageState(dayKey = "2026-07-08", usedMillis = 0L)
    }

    private class FakeAccessibilityServiceRepository(
        var enabled: Boolean
    ) : AccessibilityServiceRepository {
        override fun isMoreFocusAccessibilityServiceEnabled(): Boolean = enabled
    }

    private class FakeInstalledAppsRepository(
        val apps: List<SelectableApp> = emptyList()
    ) : InstalledAppsRepository {
        override fun getSelectableApps(): List<SelectableApp> = apps
    }

    private companion object {
        val DAILY_REELS_LIMIT_MINUTE_OPTIONS = listOf(5, 10, 15, 30, 60)
        const val DEFAULT_DAILY_REELS_LIMIT_MINUTES = 15
    }
}
