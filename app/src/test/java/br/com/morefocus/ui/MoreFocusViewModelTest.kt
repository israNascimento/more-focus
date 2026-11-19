package br.com.morefocus.ui

import br.com.morefocus.blocking.FocusSchedule
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
                youtubeShortsEnabled = false,
                exponentialPauseLockEnabled = false,
                dailyVerticalVideosLimitSeconds = 30 * 60,
                appLimitRules = listOf(
                    AppLimitRule(
                        packageName = "com.video",
                        appName = "Video",
                        limitSeconds = 15 * 60
                    )
                )
            ),
            dailyVerticalVideosUsageMillis = 7 * 60 * 1000L
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
        assertFalse(state.youtubeShortsEnabled)
        assertFalse(state.exponentialPauseLockEnabled)
        assertEquals(30 * 60, state.dailyVerticalVideosLimitSeconds)
        assertEquals(7 * 60 * 1000L, state.dailyVerticalVideosUsageMillis)
        assertEquals(
            settingsRepository.dailyVerticalVideosLimitSecondOptions,
            state.dailyVerticalVideosLimitSecondOptions
        )
        assertEquals(settingsRepository.getSettings().appLimitRules, state.appLimitRules)
        assertEquals(installedAppsRepository.apps, state.selectableApps)
        assertEquals(settingsRepository.appLimitSecondOptions, state.appLimitSecondOptions)
        assertTrue(30 in state.dailyVerticalVideosLimitSecondOptions)
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
        viewModel.setHideFeedReels(true)
        assertTrue(viewModel.uiState.value.hideFeedReels)
        assertFalse(viewModel.uiState.value.hideFeedShorts)
        viewModel.setHideFeedShorts(true)
        assertTrue(viewModel.uiState.value.hideFeedShorts)
        assertFalse(viewModel.uiState.value.hardBlockVerticalVideos)
        viewModel.setHardBlockVerticalVideos(true)
        assertTrue(viewModel.uiState.value.hardBlockVerticalVideos)
        viewModel.setShowUsageNotification(false)
        assertFalse(viewModel.uiState.value.showUsageNotification)
        viewModel.setYouTubeShortsEnabled(false)
        viewModel.setXVideosEnabled(false)
        viewModel.setFacebookReelsEnabled(false)
        viewModel.setKwaiFeedEnabled(false)
        viewModel.setTikTokForYouEnabled(false)
        viewModel.setExponentialPauseLockEnabled(false)

        assertFalse(settingsRepository.getSettings().focusEnabled)
        assertFalse(settingsRepository.getSettings().instagramReelsEnabled)
        assertFalse(settingsRepository.getSettings().youtubeShortsEnabled)
        assertFalse(settingsRepository.getSettings().exponentialPauseLockEnabled)
        assertFalse(viewModel.uiState.value.focusEnabled)
        assertFalse(viewModel.uiState.value.instagramReelsEnabled)
        assertFalse(viewModel.uiState.value.youtubeShortsEnabled)
        assertFalse(viewModel.uiState.value.xVideosEnabled)
        assertFalse(viewModel.uiState.value.facebookReelsEnabled)
        assertFalse(viewModel.uiState.value.kwaiFeedEnabled)
        assertFalse(viewModel.uiState.value.tikTokForYouEnabled)
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

        viewModel.setDailyVerticalVideosLimitSeconds(99)

        assertEquals(
            DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS,
            settingsRepository.getSettings().dailyVerticalVideosLimitSeconds
        )
        assertEquals(
            DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS,
            viewModel.uiState.value.dailyVerticalVideosLimitSeconds
        )
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
                youtubeShortsEnabled = true,
                dailyVerticalVideosLimitSeconds =
                    DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS,
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
        assertEquals(DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS, updatedRule.limitSeconds)
        assertFalse(updatedRule.enabled)

        viewModel.removeAppLimit("com.video")

        assertTrue(viewModel.uiState.value.appLimitRules.isEmpty())
    }

    private class FakeMoreFocusSettingsRepository(
        private var settings: MoreFocusSettings = MoreFocusSettings(
            focusEnabled = true,
            instagramReelsEnabled = true,
            youtubeShortsEnabled = true,
            dailyVerticalVideosLimitSeconds = DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS
        ),
        private val dailyVerticalVideosUsageMillis: Long = 0L
    ) : MoreFocusSettingsRepository {
        override val dailyVerticalVideosLimitSecondOptions: List<Int> =
            DAILY_VERTICAL_VIDEOS_LIMIT_SECOND_OPTIONS

        override val appLimitSecondOptions: List<Int> =
            DAILY_VERTICAL_VIDEOS_LIMIT_SECOND_OPTIONS

        override fun observeDailyVideoUsage(onChanged: () -> Unit): () -> Unit = {}

        override fun weeklyVideoUsage() = emptyList<br.com.morefocus.blocking.VideoUsageDay>()

        override fun getSettings(): MoreFocusSettings = settings

        private var focusSessionEnd = 0L

        override fun setFocusSchedule(schedule: FocusSchedule) { settings = settings.copy(focusSchedule = schedule) }

        override fun startFocusSession() { focusSessionEnd = System.currentTimeMillis() + 1_800_000L }

        override fun getBlockSettings(nowMillis: Long): BlockSettings =
            BlockSettings(
                focusEnabled = settings.focusEnabled,
                instagramReelsEnabled = settings.instagramReelsEnabled,
                focusSessionEndsAtMillis = focusSessionEnd,
                hardBlockVerticalVideos = settings.hardBlockVerticalVideos,
                youtubeShortsEnabled = settings.youtubeShortsEnabled,
                xVideosEnabled = settings.xVideosEnabled,
                facebookReelsEnabled = settings.facebookReelsEnabled,
                kwaiFeedEnabled = settings.kwaiFeedEnabled,
                tikTokForYouEnabled = settings.tikTokForYouEnabled,
                dailyVerticalVideosLimitSeconds = settings.dailyVerticalVideosLimitSeconds,
                dailyVerticalVideosUsageMillis = dailyVerticalVideosUsageMillis,
                appLimitRules = settings.appLimitRules
            )

        override fun setFocusEnabled(enabled: Boolean) {
            settings = settings.copy(focusEnabled = enabled)
        }

        override fun setInstagramReelsEnabled(enabled: Boolean) {
            settings = settings.copy(instagramReelsEnabled = enabled)
        }

        override fun setHideFeedReels(enabled: Boolean) {
            settings = settings.copy(hideFeedReels = enabled)
        }

        override fun setHideFeedShorts(enabled: Boolean) {
            settings = settings.copy(hideFeedShorts = enabled)
        }

        override fun setHardBlockVerticalVideos(enabled: Boolean) {
            settings = settings.copy(hardBlockVerticalVideos = enabled)
        }

        override fun setShowUsageNotification(show: Boolean) {
            settings = settings.copy(showUsageNotification = show)
        }

        override fun setYouTubeShortsEnabled(enabled: Boolean) {
            settings = settings.copy(youtubeShortsEnabled = enabled)
        }

        override fun setTikTokForYouEnabled(enabled: Boolean) {
            settings = settings.copy(tikTokForYouEnabled = enabled)
        }

        override fun setXVideosEnabled(enabled: Boolean) {
            settings = settings.copy(xVideosEnabled = enabled)
        }

        override fun setFacebookReelsEnabled(enabled: Boolean) {
            settings = settings.copy(facebookReelsEnabled = enabled)
        }

        override fun setKwaiFeedEnabled(enabled: Boolean) {
            settings = settings.copy(kwaiFeedEnabled = enabled)
        }

        override fun setExponentialPauseLockEnabled(enabled: Boolean) {
            settings = settings.copy(exponentialPauseLockEnabled = enabled)
        }

        override fun setDailyVerticalVideosLimitSeconds(limitSeconds: Int) {
            settings = settings.copy(
                dailyVerticalVideosLimitSeconds = limitSeconds.takeIf {
                    it in dailyVerticalVideosLimitSecondOptions
                } ?: DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS
            )
        }

        override fun setAppLimit(packageName: String, appName: String, limitSeconds: Int) {
            val sanitizedLimit = limitSeconds.takeIf {
                it in appLimitSecondOptions
            } ?: DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS
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
                            } ?: DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS
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

        override fun recordDailyVerticalVideosUsage(
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
        val DAILY_VERTICAL_VIDEOS_LIMIT_SECOND_OPTIONS = listOf(
            30,
            5 * 60,
            10 * 60,
            15 * 60,
            30 * 60,
            60 * 60
        )
        const val DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS = 15 * 60
    }
}
