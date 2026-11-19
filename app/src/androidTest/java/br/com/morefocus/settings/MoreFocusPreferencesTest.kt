package br.com.morefocus.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.FACEBOOK_PACKAGE_NAME
import br.com.morefocus.blocking.INSTAGRAM_PACKAGE_NAME
import br.com.morefocus.blocking.KWAI_PACKAGE_NAME
import br.com.morefocus.blocking.PauseExtensionTarget
import br.com.morefocus.blocking.TIKTOK_PACKAGE_NAME
import br.com.morefocus.blocking.X_PACKAGE_NAME
import br.com.morefocus.blocking.YOUTUBE_PACKAGE_NAME
import br.com.morefocus.data.SharedPreferencesMoreFocusSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MoreFocusPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var preferences: MoreFocusPreferences

    @Before
    fun setUp() {
        clearPreferences()
        preferences = MoreFocusPreferences(context)
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun weeklyHistoryKeepsBothSidesOfMidnightAndDistinguishesUnrecordedDays() {
        val day1 = millis(2026, 11, 16, 12)
        val midnight = millis(2026, 11, 17, 0)
        assertEquals(0L, preferences.videoUsageHistory(day1).first().usedMillis)
        preferences.recordDailyVerticalVideosUsage(midnight - 120_000L, midnight + 60_000L)
        val now = millis(2026, 11, 18, 9)
        val result = MoreFocusPreferences(context).videoUsageHistory(now)
        assertEquals(91, result.size)
        assertEquals(listOf(0L, 60_000L, 120_000L), result.take(3).map { it.usedMillis })
        assertTrue(result.drop(3).all { it.usedMillis == null })
        val nextWeek = preferences.videoUsageHistory(millis(2026, 11, 25, 9))
        assertEquals(120_000L, nextWeek[9].usedMillis)
        assertEquals(60_000L, nextWeek[8].usedMillis)
    }

    @Test
    fun firstUsageFlushCanReachTheDayBeforeHistoryWasOpened() {
        val midnight = millis(2026, 11, 17, 0)
        preferences.videoUsageHistory(midnight)
        preferences.recordDailyVerticalVideosUsage(midnight - 10_000L, midnight + 20_000L)
        val days = preferences.videoUsageHistory(midnight + 20_000L)
        assertEquals(listOf(20_000L, 10_000L, null), days.take(3).map { it.usedMillis })
    }

    @Test
    fun videoBreakdownSplitsAtMidnightAndCountsOnlyVisibleFeedTimeForEachPackage() {
        val midnight = millis(2026, 11, 17, 0)
        val repository = SharedPreferencesMoreFocusSettingsRepository(preferences)
        repository.recordDailyVerticalVideosUsage(midnight - 12_000L, midnight + 4_000L, INSTAGRAM_PACKAGE_NAME)
        repository.recordDailyVerticalVideosUsage(midnight + 4_000L, midnight + 11_000L, KWAI_PACKAGE_NAME)
        preferences.startFocusSession(midnight + 12_000L)
        repository.recordDailyVerticalVideosUsage(midnight + 11_000L, midnight + 16_000L, INSTAGRAM_PACKAGE_NAME)
        preferences.recordDailyAppUsage(INSTAGRAM_PACKAGE_NAME, midnight, midnight + 50_000L)

        val days = MoreFocusPreferences(context).videoUsageHistory(midnight + 50_000L)
        assertEquals(mapOf(INSTAGRAM_PACKAGE_NAME to 5_000L, KWAI_PACKAGE_NAME to 7_000L), days[0].videoMillisByPackage)
        assertEquals(mapOf(INSTAGRAM_PACKAGE_NAME to 12_000L), days[1].videoMillisByPackage)
        assertEquals(days[0].usedMillis, days[0].videoMillisByPackage.values.sum())
        assertEquals(days[1].usedMillis, days[1].videoMillisByPackage.values.sum())
        val afterRetention = preferences.videoUsageHistory(millis(2027, 2, 17, 12))
        assertTrue(afterRetention.all { it.videoMillisByPackage.isEmpty() })
        assertTrue(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all.keys
            .none { it.startsWith("weekly_app_video_ms_") })
    }

    @Test
    fun oldestSelectableDayKeepsUsageAppsAndGrantsUntilTheNextLocalDay() {
        val clock = DailyUsageClock()
        val now = millis(2028, 3, 13, 12)
        val oldest = clock.dayStartDaysAgo(now, 90)
        preferences.recordDailyVerticalVideosUsage(oldest - 2_000, oldest + 8_000, KWAI_PACKAGE_NAME)
        preferences.pauseFor(60_000, PauseExtensionTarget.VerticalVideos, oldest - 1)
        preferences.pauseFor(60_000, PauseExtensionTarget.VerticalVideos, oldest + 8_000)

        val history = MoreFocusPreferences(context).videoUsageHistory(now)
        assertEquals(91, history.size)
        val boundary = history.last()
        assertEquals(oldest, boundary.dateMillis)
        assertEquals(8_000L, boundary.usedMillis)
        assertEquals(mapOf(KWAI_PACKAGE_NAME to 8_000L), boundary.videoMillisByPackage)
        assertEquals(1, boundary.extensions)
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        assertTrue(prefs.all.keys.none { it.endsWith(clock.dayKey(oldest - 1)) })
        val tomorrow = preferences.videoUsageHistory(millis(2028, 3, 14, 12))
        assertTrue(tomorrow.all { it.usedMillis == 0L && it.extensions == 0 && it.videoMillisByPackage.isEmpty() })
        assertTrue(prefs.all.keys.none { it.endsWith(clock.dayKey(oldest)) })
    }

    @Test
    fun upgradingWeeklyRetentionPreservesSurvivingDaysAndMarksPrunedDaysUnknown() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putString("weekly_first_day", "2026-08-01")
            .putString("weekly_last_day", "2026-11-12")
            .putLong("weekly_video_ms_2026-11-06", 12_000L)
            .putLong("weekly_app_video_ms_${KWAI_PACKAGE_NAME}_2026-11-06", 12_000L)
            .putInt("weekly_extensions_2026-11-06", 1).commit()
        val now = millis(2026, 11, 19, 12)
        val history = preferences.videoUsageHistory(now)
        assertEquals(12_000L, history[13].usedMillis)
        assertEquals(1, history[13].extensions)
        assertEquals(12_000L, history[13].videoMillisByPackage[KWAI_PACKAGE_NAME])
        assertEquals(0L, history[12].usedMillis)
        assertTrue(history.drop(14).all { it.usedMillis == null })
        assertEquals(history, MoreFocusPreferences(context).videoUsageHistory(now))
    }

    @Test
    fun weeklyCountsEveryGrantedMinuteEvenAfterLockSaturationOrWithLockDisabled() {
        val today = millis(2026, 11, 17, 12)
        val video = PauseExtensionTarget.VerticalVideos
        repeat(7) { preferences.pauseFor(60_000L, video, today + it * 120_000) }
        assertEquals(60, preferences.pauseExtensionLockSeconds(video, today))
        preferences.setExponentialPauseLockEnabled(false)
        preferences.pauseFor(60_000L, PauseExtensionTarget.App(YOUTUBE_PACKAGE_NAME), today + 900_000L)
        val tomorrow = millis(2026, 11, 18, 12)
        preferences.pauseFor(60_000L, video, tomorrow)
        val result = MoreFocusPreferences(context).videoUsageHistory(tomorrow)
        assertEquals(listOf(1, 8), result.take(2).map { it.extensions })
        assertTrue(result.take(2).all { it.usedMillis == 0L })
    }

    @Test
    fun weeklyMigrationSeedsKnownUseOnceAndDoesNotInventPriorGrants() {
        val now = millis(2026, 11, 17, 12)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putString("daily_vertical_videos_usage_day", "2026-11-17")
            .putLong("daily_vertical_videos_usage_millis", 90_000L).commit()
        assertEquals(90_000L, preferences.videoUsageHistory(now).first().usedMillis)
        preferences.recordDailyVerticalVideosUsage(now, now + 10_000L, TIKTOK_PACKAGE_NAME)
        val today = MoreFocusPreferences(context).videoUsageHistory(now + 10_000L).first()
        assertEquals(100_000L, today.usedMillis)
        assertEquals(mapOf(TIKTOK_PACKAGE_NAME to 10_000L), today.videoMillisByPackage)
        assertEquals(0, today.extensions)
    }

    @Test
    fun weeklySchedulePersistsAndScheduledTimeDoesNotConsumeTheSharedBudget() {
        val friday = millis(2026, 11, 20, 9)
        val schedule = br.com.morefocus.blocking.FocusSchedule(enabled = true)
        preferences.setFocusSchedule(schedule)
        val reopened = MoreFocusPreferences(context)
        assertEquals(schedule, reopened.focusSchedule())
        val end = friday + 3 * 3_600_000L
        reopened.startFocusSession(end - 5 * 60_000L)
        assertTrue(reopened.getBlockSettings(end).forcesVideoExit(end))
        val used = reopened.recordDailyVerticalVideosUsage(friday - 4_000L, end + 25 * 60_000L + 2_000L)
        assertEquals(6_000L, used.usedMillis)
        assertEquals(used.usedMillis, reopened.videoUsageHistory(end + 25 * 60_000L + 2_000L).first().usedMillis)
        reopened.setFocusSchedule(schedule.copy(enabled = false))
        val nextFriday = millis(2026, 11, 27, 10)
        assertFalse(reopened.getBlockSettings(nextFriday).forcesVideoExit(nextFriday))
        assertEquals(schedule.days, reopened.focusSchedule().days)
    }

    @Test
    fun focusSessionPersistsAcrossMidnightAndRepeatedStartsKeepTheDeadline() {
        val start = millis(year = 2026, month = 11, day = 19, hour = 23) + 50 * 60_000L
        val end = start + 30 * 60_000L
        preferences.startFocusSession(start)
        val reopened = MoreFocusPreferences(context)
        reopened.startFocusSession(start + 20 * 60_000L)
        assertEquals(end, reopened.getBlockSettings(start).focusSessionEndsAtMillis)
        val settings = reopened.getBlockSettingsForPackage(YOUTUBE_PACKAGE_NAME, end - 1)
        assertTrue(settings.forcesVideoExit(end - 1))
        assertFalse(settings.forcesVideoExit(end))
        reopened.startFocusSession(end)
        assertEquals(end + 30 * 60_000L, reopened.getBlockSettings(end).focusSessionEndsAtMillis)
    }

    @Test
    fun delayedUsageFlushExcludesOnlyThePartInsideFocusAndPreservesAppTime() {
        val start = millis(year = 2026, month = 11, day = 19, hour = 10)
        val end = start + 30 * 60_000L
        preferences.startFocusSession(start)
        preferences.recordDailyVerticalVideosUsage(start - 5_000, start + 1_000)
        preferences.recordDailyVerticalVideosUsage(start + 1_000, end - 1_000)
        assertEquals(5_000L, preferences.getBlockSettings(end - 1).dailyVerticalVideosUsageMillis)
        preferences.recordDailyVerticalVideosUsage(end - 1_000, end + 2_000)
        assertEquals(7_000L, preferences.getBlockSettings(end + 2_000).dailyVerticalVideosUsageMillis)
        assertEquals(6_000L, preferences.recordDailyAppUsage(INSTAGRAM_PACKAGE_NAME, start, start + 6_000).usedMillis)
    }

    @Test
    fun exponentialPauseLockIsEnabledByDefaultAndCanBeChanged() {
        assertTrue(preferences.isExponentialPauseLockEnabled())

        preferences.setExponentialPauseLockEnabled(false)

        assertFalse(preferences.isExponentialPauseLockEnabled())
    }

    @Test
    fun thirtySecondLimitCanBePersistedForVerticalVideosAndApps() {
        preferences.setDailyVerticalVideosLimitSeconds(30)
        preferences.setAppLimit("com.example.video", "Video", 30)

        assertEquals(30, preferences.dailyVerticalVideosLimitSeconds())
        assertEquals(30, preferences.appLimitRules().single().limitSeconds)
    }

    @Test
    fun legacyAppMinuteValuesAreConvertedToSeconds() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("app_limit_packages", setOf("com.example.video"))
            .putInt("app_limit_minutes_com.example.video", 10)
            .commit()

        assertEquals(10 * 60, preferences.appLimitRules().single().limitSeconds)
    }

    @Test
    fun legacyReelsPreferencesDoNotMigrateToVerticalVideos() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("instagram_reels_enabled", false)
            .putInt("daily_reels_limit_seconds", 5 * 60)
            .putString("daily_reels_usage_day", "2026-07-08")
            .putLong("daily_reels_usage_millis", 4 * 60 * 1000L)
            .putString("pause_extension_day_instagram_reels", "2026-07-08")
            .putInt("pause_extension_count_instagram_reels", 3)
            .commit()

        val now = millis(year = 2026, month = 7, day = 8, hour = 10)

        assertTrue(preferences.isInstagramReelsEnabled())
        assertTrue(preferences.isYouTubeShortsEnabled())
        assertEquals(15 * 60, preferences.dailyVerticalVideosLimitSeconds())
        assertEquals(0L, preferences.getDailyVerticalVideosUsageState(now).usedMillis)
        assertEquals(
            0,
            preferences.pauseExtensionLockSeconds(PauseExtensionTarget.VerticalVideos, now)
        )
    }

    @Test
    fun verticalVideoTogglesCanBeChangedIndependently() {
        preferences.setInstagramReelsEnabled(false)

        assertFalse(preferences.isInstagramReelsEnabled())
        assertTrue(preferences.isYouTubeShortsEnabled())

        preferences.setYouTubeShortsEnabled(false)

        assertFalse(preferences.isInstagramReelsEnabled())
        assertFalse(preferences.isYouTubeShortsEnabled())
    }

    @Test
    fun existingHardBlockSettingAppliesToAllViewersUntilTheDailyBudgetResets() {
        val today = millis(year = 2026, month = 7, day = 8, hour = 10)
        val tomorrow = millis(year = 2026, month = 7, day = 9, hour = 10)
        preferences.setDailyVerticalVideosLimitSeconds(30)
        preferences.recordDailyVerticalVideosUsage(today - 30_000L, today)
        assertFalse(preferences.getBlockSettings(today).hardVerticalVideoLimitReached)

        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("hard_block_reels", true).commit()
        val reloaded = MoreFocusPreferences(context)
        assertFalse(reloaded.hideFeedReels())
        val todaySettings = reloaded.getBlockSettingsForPackage(INSTAGRAM_PACKAGE_NAME, today)
        assertTrue(todaySettings.hardVerticalVideoLimitReached)
        assertTrue(reloaded.getBlockSettingsForPackage(YOUTUBE_PACKAGE_NAME, today).hardVerticalVideoLimitReached)
        assertTrue(reloaded.getBlockSettingsForPackage(X_PACKAGE_NAME, today).hardVerticalVideoLimitReached)
        val tomorrowSettings = reloaded.getBlockSettingsForPackage(YOUTUBE_PACKAGE_NAME, tomorrow)
        assertFalse(tomorrowSettings.hardVerticalVideoLimitReached)
        assertTrue(reloaded.hardBlockVerticalVideos())
    }

    @Test
    fun reelsAndShortsSessionsUseTheSameDailyCounter() {
        val reelsStartedAt = millis(year = 2026, month = 7, day = 8, hour = 10)
        val reelsEndedAt = reelsStartedAt + 5 * 60 * 1000L
        val shortsEndedAt = reelsEndedAt + 10 * 60 * 1000L

        preferences.recordDailyVerticalVideosUsage(reelsStartedAt, reelsEndedAt)
        val state = preferences.recordDailyVerticalVideosUsage(reelsEndedAt, shortsEndedAt)

        assertEquals(15 * 60 * 1000L, state.usedMillis)
    }

    @Test
    fun blockSettingsForPackageLoadsOnlyTheCurrentAppRule() {
        val now = millis(year = 2026, month = 7, day = 8, hour = 10)
        preferences.setAppLimit("com.example.a", "A", 5 * 60)
        preferences.setAppLimit("com.example.b", "B", 10 * 60)

        val settings = preferences.getBlockSettingsForPackage("com.example.b", now)

        assertEquals(listOf("com.example.b"), settings.appLimitRules.map { it.packageName })
    }

    @Test
    fun monitoringScopeContainsOnlyEnabledAppLimits() {
        preferences.setAppLimit("com.example.enabled", "Enabled", 5 * 60)
        preferences.setAppLimit("com.example.disabled", "Disabled", 5 * 60)
        preferences.setAppLimitEnabled("com.example.disabled", false)

        val scope = preferences.getMonitoringScope()

        assertEquals(setOf("com.example.enabled"), scope.limitedPackages)
    }

    @Test
    fun repositoryInvalidatesMonitoringScopeAfterSettingsChange() {
        val repository = SharedPreferencesMoreFocusSettingsRepository(preferences)
        assertTrue(repository.getMonitoringScope().instagramReelsEnabled)

        repository.setInstagramReelsEnabled(false)

        assertFalse(repository.getMonitoringScope().instagramReelsEnabled)
    }

    @Test
    fun tikTokTogglePersistsIndependentlyOfTheAppLimitAndSharedCounter() {
        val repository = SharedPreferencesMoreFocusSettingsRepository(preferences)
        val start = millis(year = 2026, month = 7, day = 8, hour = 10)
        repository.recordDailyVerticalVideosUsage(start, start + 600_000)
        assertEquals(600_000L, repository.getBlockSettingsForPackage(TIKTOK_PACKAGE_NAME, start).dailyVerticalVideosUsageMillis)
        repository.recordDailyVerticalVideosUsage(start + 600_000, start + 900_000)
        assertEquals(0L, repository.getBlockSettingsForPackage(YOUTUBE_PACKAGE_NAME, start).remainingDailyVerticalVideosLimitMillis)
        assertTrue(repository.getMonitoringScope().monitorsVerticalVideos(TIKTOK_PACKAGE_NAME))
        repository.setAppLimit(TIKTOK_PACKAGE_NAME, "TikTok", 1800)
        repository.setTikTokForYouEnabled(false)
        assertFalse(repository.getMonitoringScope().monitorsVerticalVideos(TIKTOK_PACKAGE_NAME))
        assertTrue(repository.getMonitoringScope().monitorsAppLimit(TIKTOK_PACKAGE_NAME))
        assertFalse(MoreFocusPreferences(context).getBlockSettingsForPackage(TIKTOK_PACKAGE_NAME).tikTokForYouEnabled)
    }

    @Test
    fun facebookReelsRuleSharesUsageAndCanBeDisabledIndependently() {
        val repository = SharedPreferencesMoreFocusSettingsRepository(preferences)
        val start = millis(year = 2026, month = 7, day = 8, hour = 10)
        repository.recordDailyVerticalVideosUsage(start, start + 30_000L)
        assertEquals(30_000L, repository.getBlockSettingsForPackage(FACEBOOK_PACKAGE_NAME, start).dailyVerticalVideosUsageMillis)
        assertTrue(repository.getMonitoringScope().monitorsVerticalVideos(FACEBOOK_PACKAGE_NAME))
        assertFalse(repository.getMonitoringScope().masksFeedVideos(FACEBOOK_PACKAGE_NAME))
        repository.setFacebookReelsEnabled(false)
        assertFalse(repository.getMonitoringScope().monitors(FACEBOOK_PACKAGE_NAME))
        assertTrue(repository.getMonitoringScope().monitorsVerticalVideos(X_PACKAGE_NAME))
        assertFalse(MoreFocusPreferences(context).getBlockSettings().facebookReelsEnabled)
    }

    @Test
    fun kwaiUsesTheSharedBudgetAndItsOwnToggle() {
        val repository = SharedPreferencesMoreFocusSettingsRepository(preferences)
        val start = millis(year = 2026, month = 7, day = 8, hour = 10)
        repository.recordDailyVerticalVideosUsage(start, start + 30_000L)
        assertEquals(30_000L, repository.getBlockSettingsForPackage(KWAI_PACKAGE_NAME, start).dailyVerticalVideosUsageMillis)
        assertTrue(repository.getMonitoringScope().monitorsVerticalVideos(KWAI_PACKAGE_NAME))
        repository.setKwaiFeedEnabled(false)
        assertFalse(repository.getMonitoringScope().monitorsVerticalVideos(KWAI_PACKAGE_NAME))
        assertTrue(repository.getMonitoringScope().monitorsVerticalVideos(TIKTOK_PACKAGE_NAME))
        assertFalse(MoreFocusPreferences(context).getBlockSettings().kwaiFeedEnabled)
    }

    @Test
    fun xRulePersistsAndOnlyTargetsTheViewer() {
        val repository = SharedPreferencesMoreFocusSettingsRepository(preferences)
        assertTrue(repository.getMonitoringScope().monitorsVerticalVideos(X_PACKAGE_NAME))
        assertFalse(repository.getMonitoringScope().masksFeedVideos(X_PACKAGE_NAME))
        repository.setXVideosEnabled(false)
        assertFalse(repository.getMonitoringScope().monitors(X_PACKAGE_NAME))
        assertTrue(repository.getMonitoringScope().monitorsVerticalVideos(YOUTUBE_PACKAGE_NAME))
        val reloaded = MoreFocusPreferences(context)
        assertFalse(reloaded.getBlockSettings().xVideosEnabled)
        assertFalse(reloaded.getBlockSettingsForPackage(X_PACKAGE_NAME).xVideosEnabled)
    }

    @Test
    fun hideFeedShortsPersistsAndUpdatesTheMonitoringScope() {
        val repository = SharedPreferencesMoreFocusSettingsRepository(preferences)
        assertFalse(repository.getMonitoringScope().masksFeedVideos(YOUTUBE_PACKAGE_NAME))
        repository.setHideFeedShorts(true)
        assertTrue(repository.getMonitoringScope().masksFeedVideos(YOUTUBE_PACKAGE_NAME))
        assertTrue(MoreFocusPreferences(context).hideFeedShorts())
        assertFalse(preferences.hideFeedReels())
    }

    @Test
    fun countersAreDailyAndSeparatedByTarget() {
        val firstDay = millis(year = 2026, month = 7, day = 8, hour = 10)
        val nextDay = millis(year = 2026, month = 7, day = 9, hour = 10)
        val appA = PauseExtensionTarget.App("com.example.a")
        val appB = PauseExtensionTarget.App("com.example.b")

        assertEquals(0, preferences.pauseExtensionLockSeconds(appA, firstDay))
        preferences.pauseFor(60_000L, appA, firstDay)

        assertEquals(3, preferences.pauseExtensionLockSeconds(appA, firstDay))
        assertEquals(0, preferences.pauseExtensionLockSeconds(appB, firstDay))
        assertEquals(
            0,
            preferences.pauseExtensionLockSeconds(
                PauseExtensionTarget.VerticalVideos,
                firstDay
            )
        )

        preferences.pauseFor(60_000L, appA, firstDay)
        assertEquals(9, preferences.pauseExtensionLockSeconds(appA, firstDay))
        assertEquals(0, preferences.pauseExtensionLockSeconds(appA, nextDay))
    }

    @Test
    fun disablingLockDoesNotIncrementOrEraseCurrentDayCounter() {
        val now = millis(year = 2026, month = 7, day = 8, hour = 10)
        val target = PauseExtensionTarget.VerticalVideos
        preferences.pauseFor(60_000L, target, now)
        assertEquals(3, preferences.pauseExtensionLockSeconds(target, now))

        preferences.setExponentialPauseLockEnabled(false)
        preferences.pauseFor(60_000L, target, now)
        assertEquals(0, preferences.pauseExtensionLockSeconds(target, now))

        preferences.setExponentialPauseLockEnabled(true)
        assertEquals(3, preferences.pauseExtensionLockSeconds(target, now))
    }

    @Test
    fun removingAppLimitAlsoClearsItsCounter() {
        val now = millis(year = 2026, month = 7, day = 8, hour = 10)
        val target = PauseExtensionTarget.App("com.example.video")
        preferences.setAppLimit(target.packageName, "Video", 15 * 60)
        preferences.pauseFor(60_000L, target, now)
        assertEquals(3, preferences.pauseExtensionLockSeconds(target, now))

        preferences.removeAppLimit(target.packageName)

        assertEquals(0, preferences.pauseExtensionLockSeconds(target, now))
    }

    private fun clearPreferences() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis

    private companion object {
        const val PREFERENCES_NAME = "more_focus_preferences"
    }
}
