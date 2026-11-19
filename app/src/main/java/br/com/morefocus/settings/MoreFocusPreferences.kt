package br.com.morefocus.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.blocking.ExponentialPauseLockPolicy
import br.com.morefocus.blocking.MonitoringScope
import br.com.morefocus.blocking.PauseExtensionTarget

class MoreFocusPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val dailyUsageClock = DailyUsageClock()

    fun observeDailyVideoUsage(onChanged: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_SHOW_USAGE_NOTIFICATION,
                KEY_FOCUS_ENABLED,
                KEY_VERTICAL_VIDEOS_INSTAGRAM_REELS_ENABLED,
                KEY_VERTICAL_VIDEOS_YOUTUBE_SHORTS_ENABLED,
                KEY_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS,
                KEY_DAILY_VERTICAL_VIDEOS_USAGE_DAY,
                KEY_DAILY_VERTICAL_VIDEOS_USAGE_MILLIS -> onChanged()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getBlockSettings(nowMillis: Long = System.currentTimeMillis()): BlockSettings {
        val dailyUsageState = getDailyVerticalVideosUsageState(nowMillis)
        return BlockSettings(
            focusEnabled = preferences.getBoolean(KEY_FOCUS_ENABLED, true),
            instagramReelsEnabled = isInstagramReelsEnabled(),
            youtubeShortsEnabled = isYouTubeShortsEnabled(),
            pauseUntilMillis = preferences.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L),
            dailyVerticalVideosLimitSeconds = dailyVerticalVideosLimitSeconds(),
            dailyVerticalVideosUsageMillis = dailyUsageState.usedMillis,
            appLimitRules = appLimitRules(nowMillis),
            hardBlockReels = hardBlockReels()
        )
    }

    fun getBlockSettingsForPackage(
        packageName: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): BlockSettings {
        val dailyUsageState = getDailyVerticalVideosUsageState(nowMillis)
        val configuredPackages = appLimitPackages()
        val activeRule = packageName
            ?.takeIf { it in configuredPackages }
            ?.let { appLimitRule(it, nowMillis) }

        return BlockSettings(
            focusEnabled = preferences.getBoolean(KEY_FOCUS_ENABLED, true),
            instagramReelsEnabled = isInstagramReelsEnabled(),
            youtubeShortsEnabled = isYouTubeShortsEnabled(),
            pauseUntilMillis = preferences.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L),
            dailyVerticalVideosLimitSeconds = dailyVerticalVideosLimitSeconds(),
            dailyVerticalVideosUsageMillis = dailyUsageState.usedMillis,
            appLimitRules = listOfNotNull(activeRule),
            hardBlockReels = hardBlockReels()
        )
    }

    fun getMonitoringScope(): MonitoringScope =
        MonitoringScope(
            focusEnabled = preferences.getBoolean(KEY_FOCUS_ENABLED, true),
            instagramReelsEnabled = isInstagramReelsEnabled(),
            youtubeShortsEnabled = isYouTubeShortsEnabled(),
            hideFeedReels = hideFeedReels(),
            limitedPackages = appLimitPackages().filterTo(mutableSetOf()) { packageName ->
                preferences.getBoolean(appEnabledKey(packageName), true)
            }
        )

    fun isFocusEnabled(): Boolean =
        preferences.getBoolean(KEY_FOCUS_ENABLED, true)

    fun showUsageNotification(): Boolean = preferences.getBoolean(KEY_SHOW_USAGE_NOTIFICATION, true)

    fun setShowUsageNotification(show: Boolean) {
        preferences.edit { putBoolean(KEY_SHOW_USAGE_NOTIFICATION, show) }
    }

    fun setFocusEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_FOCUS_ENABLED, enabled) }
    }

    fun isInstagramReelsEnabled(): Boolean =
        preferences.getBoolean(KEY_VERTICAL_VIDEOS_INSTAGRAM_REELS_ENABLED, true)

    fun setInstagramReelsEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_VERTICAL_VIDEOS_INSTAGRAM_REELS_ENABLED, enabled)
        }
    }

    fun hideFeedReels(): Boolean = preferences.getBoolean(KEY_HIDE_FEED_REELS, false)

    fun setHideFeedReels(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_HIDE_FEED_REELS, enabled) }
    }

    fun hardBlockReels(): Boolean = preferences.getBoolean(KEY_HARD_BLOCK_REELS, false)

    fun setHardBlockReels(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_HARD_BLOCK_REELS, enabled) }
    }

    fun isYouTubeShortsEnabled(): Boolean =
        preferences.getBoolean(KEY_VERTICAL_VIDEOS_YOUTUBE_SHORTS_ENABLED, true)

    fun setYouTubeShortsEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_VERTICAL_VIDEOS_YOUTUBE_SHORTS_ENABLED, enabled)
        }
    }

    fun isExponentialPauseLockEnabled(): Boolean =
        preferences.getBoolean(KEY_EXPONENTIAL_PAUSE_LOCK_ENABLED, true)

    fun setExponentialPauseLockEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_EXPONENTIAL_PAUSE_LOCK_ENABLED, enabled) }
    }

    fun dailyVerticalVideosLimitSeconds(): Int =
        sanitizedLimitSeconds(
            preferences.getInt(
                KEY_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS,
                DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS
            )
        )

    fun setDailyVerticalVideosLimitSeconds(limitSeconds: Int) {
        preferences.edit {
            putInt(
                KEY_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS,
                sanitizedLimitSeconds(limitSeconds)
            )
        }
    }

    fun appLimitRules(nowMillis: Long = System.currentTimeMillis()): List<AppLimitRule> =
        appLimitPackages()
            .map { packageName -> appLimitRule(packageName, nowMillis) }
            .sortedWith(
                compareBy<AppLimitRule> { it.appName.lowercase() }
                    .thenBy { it.packageName }
            )

    fun setAppLimit(packageName: String, appName: String, limitSeconds: Int) {
        val sanitizedPackageName = packageName.trim().takeIf { it.isNotBlank() } ?: return
        val sanitizedAppName = appName.trim().takeIf { it.isNotBlank() } ?: sanitizedPackageName
        val sanitizedLimit = sanitizedLimitSeconds(limitSeconds)
        val packages = appLimitPackages() + sanitizedPackageName

        preferences.edit {
            putStringSet(KEY_APP_LIMIT_PACKAGES, packages)
            putString(appNameKey(sanitizedPackageName), sanitizedAppName)
            putInt(appLimitSecondsKey(sanitizedPackageName), sanitizedLimit)
            remove(appLimitMinutesKey(sanitizedPackageName))
            putBoolean(appEnabledKey(sanitizedPackageName), true)
        }
    }

    fun setAppLimitSeconds(packageName: String, limitSeconds: Int) {
        val sanitizedPackageName = packageName.trim().takeIf { it.isNotBlank() } ?: return
        if (sanitizedPackageName !in appLimitPackages()) return

        preferences.edit {
            putInt(appLimitSecondsKey(sanitizedPackageName), sanitizedLimitSeconds(limitSeconds))
            remove(appLimitMinutesKey(sanitizedPackageName))
        }
    }

    fun setAppLimitEnabled(packageName: String, enabled: Boolean) {
        val sanitizedPackageName = packageName.trim().takeIf { it.isNotBlank() } ?: return
        if (sanitizedPackageName !in appLimitPackages()) return

        preferences.edit {
            putBoolean(appEnabledKey(sanitizedPackageName), enabled)
        }
    }

    fun removeAppLimit(packageName: String) {
        val sanitizedPackageName = packageName.trim().takeIf { it.isNotBlank() } ?: return
        val packages = appLimitPackages() - sanitizedPackageName

        preferences.edit {
            putStringSet(KEY_APP_LIMIT_PACKAGES, packages)
            remove(appNameKey(sanitizedPackageName))
            remove(appLimitMinutesKey(sanitizedPackageName))
            remove(appLimitSecondsKey(sanitizedPackageName))
            remove(appEnabledKey(sanitizedPackageName))
            remove(appUsageDayKey(sanitizedPackageName))
            remove(appUsageMillisKey(sanitizedPackageName))
            remove(pauseExtensionDayKey(PauseExtensionTarget.App(sanitizedPackageName)))
            remove(pauseExtensionCountKey(PauseExtensionTarget.App(sanitizedPackageName)))
        }
    }

    fun pauseExtensionLockSeconds(
        target: PauseExtensionTarget,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        if (!isExponentialPauseLockEnabled()) return 0

        return ExponentialPauseLockPolicy.lockSeconds(
            pauseExtensionCount(target, nowMillis)
        )
    }

    fun pauseFor(
        durationMillis: Long,
        target: PauseExtensionTarget,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val shouldIncrementExtensionCount = isExponentialPauseLockEnabled()
        val nextExtensionCount = if (shouldIncrementExtensionCount) {
            ExponentialPauseLockPolicy.increment(pauseExtensionCount(target, nowMillis))
        } else {
            0
        }

        preferences.edit {
            putLong(KEY_PAUSE_UNTIL_MILLIS, nowMillis + durationMillis)
            if (shouldIncrementExtensionCount) {
                putString(pauseExtensionDayKey(target), dailyUsageClock.dayKey(nowMillis))
                putInt(pauseExtensionCountKey(target), nextExtensionCount)
            }
        }
    }

    fun pauseUntilMillis(): Long =
        preferences.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L)

    fun getDailyVerticalVideosUsageState(
        nowMillis: Long = System.currentTimeMillis()
    ): DailyUsageState {
        val storedState = storedDailyUsageState()
        val currentState = dailyUsageClock.stateFor(storedState, nowMillis)
        if (currentState != storedState) saveDailyUsageState(currentState)
        return currentState
    }

    fun recordDailyVerticalVideosUsage(
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState {
        val updatedState = dailyUsageClock.addVisibleTime(
            state = storedDailyUsageState(),
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis
        )
        saveDailyUsageState(updatedState)
        return updatedState
    }

    fun getDailyAppUsageState(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyUsageState {
        val storedState = storedDailyAppUsageState(packageName)
        val currentState = dailyUsageClock.stateFor(
            storedState,
            nowMillis
        )
        if (currentState != storedState) saveDailyAppUsageState(packageName, currentState)
        return currentState
    }

    fun recordDailyAppUsage(
        packageName: String,
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState {
        val updatedState = dailyUsageClock.addVisibleTime(
            state = storedDailyAppUsageState(packageName),
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis
        )
        saveDailyAppUsageState(packageName, updatedState)
        return updatedState
    }

    private fun storedDailyUsageState(): DailyUsageState =
        DailyUsageState(
            dayKey = preferences.getString(
                KEY_DAILY_VERTICAL_VIDEOS_USAGE_DAY,
                null
            ).orEmpty(),
            usedMillis = preferences.getLong(
                KEY_DAILY_VERTICAL_VIDEOS_USAGE_MILLIS,
                0L
            )
        )

    private fun saveDailyUsageState(state: DailyUsageState) {
        preferences.edit {
            putString(KEY_DAILY_VERTICAL_VIDEOS_USAGE_DAY, state.dayKey)
            putLong(KEY_DAILY_VERTICAL_VIDEOS_USAGE_MILLIS, state.usedMillis)
        }
    }

    private fun appLimitPackages(): Set<String> =
        preferences.getStringSet(KEY_APP_LIMIT_PACKAGES, emptySet())
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun appLimitRule(packageName: String, nowMillis: Long): AppLimitRule {
        val usageState = getDailyAppUsageState(packageName, nowMillis)
        return AppLimitRule(
            packageName = packageName,
            appName = preferences.getString(appNameKey(packageName), null)
                ?.takeIf { it.isNotBlank() }
                ?: packageName,
            limitSeconds = appLimitSeconds(packageName),
            usageMillis = usageState.usedMillis,
            enabled = preferences.getBoolean(appEnabledKey(packageName), true)
        )
    }

    private fun appLimitSeconds(packageName: String): Int {
        if (preferences.contains(appLimitSecondsKey(packageName))) {
            return sanitizedLimitSeconds(
                preferences.getInt(
                    appLimitSecondsKey(packageName),
                    DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS
                )
            )
        }

        val legacyMinutes = preferences.getInt(
            appLimitMinutesKey(packageName),
            DEFAULT_LIMIT_MINUTES
        )
        return sanitizedLimitSeconds(legacyMinutes * SECONDS_PER_MINUTE)
    }

    private fun sanitizedLimitSeconds(limitSeconds: Int): Int =
        limitSeconds.takeIf { it in DAILY_VERTICAL_VIDEOS_LIMIT_SECOND_OPTIONS }
            ?: DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS

    private fun storedDailyAppUsageState(packageName: String): DailyUsageState =
        DailyUsageState(
            dayKey = preferences.getString(appUsageDayKey(packageName), null).orEmpty(),
            usedMillis = preferences.getLong(appUsageMillisKey(packageName), 0L)
        )

    private fun saveDailyAppUsageState(packageName: String, state: DailyUsageState) {
        preferences.edit {
            putString(appUsageDayKey(packageName), state.dayKey)
            putLong(appUsageMillisKey(packageName), state.usedMillis)
        }
    }

    private fun pauseExtensionCount(target: PauseExtensionTarget, nowMillis: Long): Int {
        val storedDayKey = preferences.getString(pauseExtensionDayKey(target), null)
        if (storedDayKey != dailyUsageClock.dayKey(nowMillis)) return 0

        return preferences.getInt(pauseExtensionCountKey(target), 0)
            .coerceIn(0, ExponentialPauseLockPolicy.MAX_EXTENSION_COUNT)
    }

    private fun appNameKey(packageName: String): String =
        "$KEY_APP_LIMIT_APP_NAME_PREFIX$packageName"

    private fun appLimitMinutesKey(packageName: String): String =
        "$KEY_APP_LIMIT_MINUTES_PREFIX$packageName"

    private fun appLimitSecondsKey(packageName: String): String =
        "$KEY_APP_LIMIT_SECONDS_PREFIX$packageName"

    private fun appEnabledKey(packageName: String): String =
        "$KEY_APP_LIMIT_ENABLED_PREFIX$packageName"

    private fun appUsageDayKey(packageName: String): String =
        "$KEY_APP_LIMIT_USAGE_DAY_PREFIX$packageName"

    private fun appUsageMillisKey(packageName: String): String =
        "$KEY_APP_LIMIT_USAGE_MILLIS_PREFIX$packageName"

    private fun pauseExtensionDayKey(target: PauseExtensionTarget): String =
        "$KEY_PAUSE_EXTENSION_DAY_PREFIX${target.preferenceKey()}"

    private fun pauseExtensionCountKey(target: PauseExtensionTarget): String =
        "$KEY_PAUSE_EXTENSION_COUNT_PREFIX${target.preferenceKey()}"

    private fun PauseExtensionTarget.preferenceKey(): String = when (this) {
        PauseExtensionTarget.VerticalVideos -> "vertical_videos"
        is PauseExtensionTarget.App -> "app_$packageName"
    }

    companion object {
        val DAILY_VERTICAL_VIDEOS_LIMIT_SECOND_OPTIONS = listOf(
            30,
            5 * 60,
            10 * 60,
            15 * 60,
            30 * 60,
            60 * 60
        )
        const val DEFAULT_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS = 15 * 60
        private const val DEFAULT_LIMIT_MINUTES = 15
        private const val SECONDS_PER_MINUTE = 60

        private const val PREFERENCES_NAME = "more_focus_preferences"
        private const val KEY_FOCUS_ENABLED = "focus_enabled"
        private const val KEY_SHOW_USAGE_NOTIFICATION = "show_usage_notification"
        private const val KEY_HIDE_FEED_REELS = "hide_feed_reels"
        private const val KEY_HARD_BLOCK_REELS = "hard_block_reels"
        private const val KEY_VERTICAL_VIDEOS_INSTAGRAM_REELS_ENABLED =
            "vertical_videos_instagram_reels_enabled"
        private const val KEY_VERTICAL_VIDEOS_YOUTUBE_SHORTS_ENABLED =
            "vertical_videos_youtube_shorts_enabled"
        private const val KEY_EXPONENTIAL_PAUSE_LOCK_ENABLED =
            "exponential_pause_lock_enabled"
        private const val KEY_PAUSE_UNTIL_MILLIS = "pause_until_millis"
        private const val KEY_DAILY_VERTICAL_VIDEOS_LIMIT_SECONDS =
            "daily_vertical_videos_limit_seconds"
        private const val KEY_DAILY_VERTICAL_VIDEOS_USAGE_DAY =
            "daily_vertical_videos_usage_day"
        private const val KEY_DAILY_VERTICAL_VIDEOS_USAGE_MILLIS =
            "daily_vertical_videos_usage_millis"
        private const val KEY_APP_LIMIT_PACKAGES = "app_limit_packages"
        private const val KEY_APP_LIMIT_APP_NAME_PREFIX = "app_limit_app_name_"
        private const val KEY_APP_LIMIT_MINUTES_PREFIX = "app_limit_minutes_"
        private const val KEY_APP_LIMIT_SECONDS_PREFIX = "app_limit_seconds_"
        private const val KEY_APP_LIMIT_ENABLED_PREFIX = "app_limit_enabled_"
        private const val KEY_APP_LIMIT_USAGE_DAY_PREFIX = "app_limit_usage_day_"
        private const val KEY_APP_LIMIT_USAGE_MILLIS_PREFIX = "app_limit_usage_millis_"
        private const val KEY_PAUSE_EXTENSION_DAY_PREFIX = "pause_extension_day_"
        private const val KEY_PAUSE_EXTENSION_COUNT_PREFIX = "pause_extension_count_"
    }
}
