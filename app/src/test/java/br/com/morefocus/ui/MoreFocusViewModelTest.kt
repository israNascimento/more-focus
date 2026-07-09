package br.com.morefocus.ui

import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.blocking.PauseExtensionTarget
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
                exponentialPauseLockEnabled = false,
                dailyReelsLimitSeconds = 30 * 60,
                appLimitRules = listOf(
                    AppLimitRule(
                        packageName = "com.video",
                        appName = "Video",
                        limitSeconds = 15 * 60
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
        assertFalse(state.exponentialPauseLockEnabled)
        assertEquals(30 * 60, state.dailyReelsLimitSeconds)
        assertEquals(7 * 60 * 1000L, state.dailyReelsUsageMillis)
        assertEquals(settingsRepository.dailyReelsLimitSecondOptions, state.dailyReelsLimitSecondOptions)
        assertEquals(settingsRepository.getSettings().appLimitRules, state.appLimitRules)
        assertEquals(installedAppsRepository.apps, state.selectableApps)
        assertEquals(settingsRepository.appLimitSecondOptions, state.appLimitSecondOptions)
        assertTrue(30 in state.dailyReelsLimitSecondOptions)
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
        viewModel.setExponentialPauseLockEnabled(false)

        assertFalse(settingsRepository.getSettings().focusEnabled)
        assertFalse(settingsRepository.getSettings().instagramReelsEnabled)
        assertFalse(settingsRepository.getSettings().exponentialPauseLockEnabled)
        assertFalse(viewModel.uiState.value.focusEnabled)
        assertFalse(viewModel.uiState.value.instagramReelsEnabled)
        assertFalse(viewModel.uiState.value.exponentialPauseLockEnabled)
    }

    @Test
    fun invalidDailyLimitIsSanitizedByRepository() {
        val settingsRepository = FakeMoreFocusSettingsRepository()
        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = FakeAccessibilityServiceRepository(enabled = false),
            installedAppsRepository = FakeInstalledAppsRepository()
        )

        viewModel.setDailyReelsLimitSeconds(99)

        assertEquals(DEFAULT_DAILY_REELS_LIMIT_SECONDS, settingsRepository.getSettings().dailyReelsLimitSeconds)
        assertEquals(DEFAULT_DAILY_REELS_LIMIT_SECONDS, viewModel.uiState.value.dailyReelsLimitSeconds)
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

        viewModel.addAppLimit(packageName = "com.video", limitSeconds = 30)

        val rule = viewModel.uiState.value.appLimitRules.single()
        assertEquals("com.video", rule.packageName)
        assertEquals("Video", rule.appName)
        assertEquals(30, rule.limitSeconds)
        assertTrue(rule.enabled)
    }

    @Test
    fun appLimitChangesUpdateRepositoryAndUiState() {
        val settingsRepository = FakeMoreFocusSettingsRepository(
            settings = MoreFocusSettings(
                focusEnabled = true,
                instagramReelsEnabled = true,
                dailyReelsLimitSeconds = DEFAULT_DAILY_REELS_LIMIT_SECONDS,
                appLimitRules = listOf(
                    AppLimitRule(
                        packageName = "com.video",
                        appName = "Video",
                        limitSeconds = 30 * 60
                    )
                )
            )
        )
        val viewModel = MoreFocusViewModel(
            settingsRepository = settingsRepository,
            accessibilityServiceRepository = FakeAccessibilityServiceRepository(enabled = false),
            installedAppsRepository = FakeInstalledAppsRepository()
        )

        viewModel.setAppLimitSeconds("com.video", 99)
        viewModel.setAppLimitEnabled("com.video", false)

        val updatedRule = viewModel.uiState.value.appLimitRules.single()
        assertEquals(DEFAULT_DAILY_REELS_LIMIT_SECONDS, updatedRule.limitSeconds)
        assertFalse(updatedRule.enabled)

        viewModel.removeAppLimit("com.video")

        assertTrue(viewModel.uiState.value.appLimitRules.isEmpty())
    }

    private class FakeMoreFocusSettingsRepository(
        private var settings: MoreFocusSettings = MoreFocusSettings(
            focusEnabled = true,
            instagramReelsEnabled = true,
            dailyReelsLimitSeconds = DEFAULT_DAILY_REELS_LIMIT_SECONDS
        ),
        private val dailyReelsUsageMillis: Long = 0L
    ) : MoreFocusSettingsRepository {
        override val dailyReelsLimitSecondOptions: List<Int> = DAILY_REELS_LIMIT_SECOND_OPTIONS

        override val appLimitSecondOptions: List<Int> = DAILY_REELS_LIMIT_SECOND_OPTIONS

        override fun getSettings(): MoreFocusSettings = settings

        override fun getBlockSettings(nowMillis: Long): BlockSettings =
            BlockSettings(
                focusEnabled = settings.focusEnabled,
                instagramReelsEnabled = settings.instagramReelsEnabled,
                dailyLimitSeconds = settings.dailyReelsLimitSeconds,
                dailyUsageMillis = dailyReelsUsageMillis,
                appLimitRules = settings.appLimitRules
            )

        override fun setFocusEnabled(enabled: Boolean) {
            settings = settings.copy(focusEnabled = enabled)
        }

        override fun setInstagramReelsEnabled(enabled: Boolean) {
            settings = settings.copy(instagramReelsEnabled = enabled)
        }

        override fun setExponentialPauseLockEnabled(enabled: Boolean) {
            settings = settings.copy(exponentialPauseLockEnabled = enabled)
        }

        override fun setDailyReelsLimitSeconds(limitSeconds: Int) {
            settings = settings.copy(
                dailyReelsLimitSeconds = limitSeconds.takeIf {
                    it in dailyReelsLimitSecondOptions
                } ?: DEFAULT_DAILY_REELS_LIMIT_SECONDS
            )
        }

        override fun setAppLimit(packageName: String, appName: String, limitSeconds: Int) {
            val sanitizedLimit = limitSeconds.takeIf {
                it in appLimitSecondOptions
            } ?: DEFAULT_DAILY_REELS_LIMIT_SECONDS
            val newRule = AppLimitRule(
                packageName = packageName,
                appName = appName,
                limitSeconds = sanitizedLimit
            )
            settings = settings.copy(
                appLimitRules = settings.appLimitRules
                    .filterNot { it.packageName == packageName } + newRule
            )
        }

        override fun setAppLimitSeconds(packageName: String, limitSeconds: Int) {
            settings = settings.copy(
                appLimitRules = settings.appLimitRules.map { rule ->
                    if (rule.packageName == packageName) {
                        rule.copy(
                            limitSeconds = limitSeconds.takeIf {
                                it in appLimitSecondOptions
                            } ?: DEFAULT_DAILY_REELS_LIMIT_SECONDS
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

        override fun pauseExtensionLockSeconds(
            target: PauseExtensionTarget,
            nowMillis: Long
        ): Int = 0

        override fun pauseFor(
            durationMillis: Long,
            target: PauseExtensionTarget,
            nowMillis: Long
        ) = Unit

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
        val DAILY_REELS_LIMIT_SECOND_OPTIONS = listOf(
            30,
            5 * 60,
            10 * 60,
            15 * 60,
            30 * 60,
            60 * 60
        )
        const val DEFAULT_DAILY_REELS_LIMIT_SECONDS = 15 * 60
    }
}
