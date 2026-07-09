package br.com.morefocus.settings

import android.content.Context
import androidx.core.content.edit
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.blocking.ExponentialPauseLockPolicy
import br.com.morefocus.blocking.PauseExtensionTarget

class MoreFocusPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val dailyUsageClock = DailyUsageClock()

    fun getBlockSettings(nowMillis: Long = System.currentTimeMillis()): BlockSettings {
        val dailyUsageState = getDailyReelsUsageState(nowMillis)
        return BlockSettings(
            focusEnabled = preferences.getBoolean(KEY_FOCUS_ENABLED, true),
            instagramReelsEnabled = preferences.getBoolean(KEY_INSTAGRAM_REELS_ENABLED, true),
            pauseUntilMillis = preferences.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L),
            dailyLimitSeconds = dailyReelsLimitSeconds(),
            dailyUsageMillis = dailyUsageState.usedMillis,
            appLimitRules = appLimitRules(nowMillis)
        )
    }

    fun isFocusEnabled(): Boolean =
        preferences.getBoolean(KEY_FOCUS_ENABLED, true)

    fun setFocusEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_FOCUS_ENABLED, enabled) }
    }

    fun isInstagramReelsEnabled(): Boolean =
        preferences.getBoolean(KEY_INSTAGRAM_REELS_ENABLED, true)

    fun setInstagramReelsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_INSTAGRAM_REELS_ENABLED, enabled) }
    }

    fun isExponentialPauseLockEnabled(): Boolean =
        preferences.getBoolean(KEY_EXPONENTIAL_PAUSE_LOCK_ENABLED, true)

    fun setExponentialPauseLockEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_EXPONENTIAL_PAUSE_LOCK_ENABLED, enabled) }
    }

    fun dailyReelsLimitSeconds(): Int {
        if (preferences.contains(KEY_DAILY_REELS_LIMIT_SECONDS)) {
            return sanitizedLimitSeconds(
                preferences.getInt(
                    KEY_DAILY_REELS_LIMIT_SECONDS,
                    DEFAULT_DAILY_REELS_LIMIT_SECONDS
                )
            )
        }

        val legacyMinutes = preferences.getInt(
            KEY_DAILY_REELS_LIMIT_MINUTES,
            DEFAULT_DAILY_REELS_LIMIT_MINUTES
        )
        return sanitizedLimitSeconds(legacyMinutes * SECONDS_PER_MINUTE)
    }

    fun setDailyReelsLimitSeconds(limitSeconds: Int) {
        preferences.edit {
            putInt(KEY_DAILY_REELS_LIMIT_SECONDS, sanitizedLimitSeconds(limitSeconds))
        }
    }

    fun appLimitRules(nowMillis: Long = System.currentTimeMillis()): List<AppLimitRule> =
        appLimitPackages()
            .map { packageName ->
                val usageState = getDailyAppUsageState(packageName, nowMillis)
                AppLimitRule(
                    packageName = packageName,
                    appName = preferences.getString(appNameKey(packageName), null)
                        ?.takeIf { it.isNotBlank() }
                        ?: packageName,
                    limitSeconds = appLimitSeconds(packageName),
                    usageMillis = usageState.usedMillis,
                    enabled = preferences.getBoolean(appEnabledKey(packageName), true)
                )
            }
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

    fun getDailyReelsUsageState(nowMillis: Long = System.currentTimeMillis()): DailyUsageState {
        val currentState = dailyUsageClock.stateFor(storedDailyUsageState(), nowMillis)
        saveDailyUsageState(currentState)
        return currentState
    }

    fun recordDailyReelsUsage(startedAtMillis: Long?, endedAtMillis: Long): DailyUsageState {
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
        val currentState = dailyUsageClock.stateFor(
            storedDailyAppUsageState(packageName),
            nowMillis
        )
        saveDailyAppUsageState(packageName, currentState)
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
            dayKey = preferences.getString(KEY_DAILY_REELS_USAGE_DAY, null).orEmpty(),
            usedMillis = preferences.getLong(KEY_DAILY_REELS_USAGE_MILLIS, 0L)
        )

    private fun saveDailyUsageState(state: DailyUsageState) {
        preferences.edit {
            putString(KEY_DAILY_REELS_USAGE_DAY, state.dayKey)
            putLong(KEY_DAILY_REELS_USAGE_MILLIS, state.usedMillis)
        }
    }

    private fun appLimitPackages(): Set<String> =
        preferences.getStringSet(KEY_APP_LIMIT_PACKAGES, emptySet())
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun appLimitSeconds(packageName: String): Int {
        if (preferences.contains(appLimitSecondsKey(packageName))) {
            return sanitizedLimitSeconds(
                preferences.getInt(
                    appLimitSecondsKey(packageName),
                    DEFAULT_DAILY_REELS_LIMIT_SECONDS
                )
            )
        }

        val legacyMinutes = preferences.getInt(
            appLimitMinutesKey(packageName),
            DEFAULT_DAILY_REELS_LIMIT_MINUTES
        )
        return sanitizedLimitSeconds(legacyMinutes * SECONDS_PER_MINUTE)
    }

    private fun sanitizedLimitSeconds(limitSeconds: Int): Int =
        limitSeconds.takeIf { it in DAILY_REELS_LIMIT_SECOND_OPTIONS }
            ?: DEFAULT_DAILY_REELS_LIMIT_SECONDS

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
        PauseExtensionTarget.InstagramReels -> "instagram_reels"
        is PauseExtensionTarget.App -> "app_$packageName"
    }

    companion object {
        val DAILY_REELS_LIMIT_SECOND_OPTIONS = listOf(
            30,
            5 * 60,
            10 * 60,
            15 * 60,
            30 * 60,
            60 * 60
        )
        const val DEFAULT_DAILY_REELS_LIMIT_SECONDS = 15 * 60
        private const val DEFAULT_DAILY_REELS_LIMIT_MINUTES = 15
        private const val SECONDS_PER_MINUTE = 60

        private const val PREFERENCES_NAME = "more_focus_preferences"
        private const val KEY_FOCUS_ENABLED = "focus_enabled"
        private const val KEY_INSTAGRAM_REELS_ENABLED = "instagram_reels_enabled"
        private const val KEY_EXPONENTIAL_PAUSE_LOCK_ENABLED =
            "exponential_pause_lock_enabled"
        private const val KEY_PAUSE_UNTIL_MILLIS = "pause_until_millis"
        private const val KEY_DAILY_REELS_LIMIT_SECONDS = "daily_reels_limit_seconds"
        private const val KEY_DAILY_REELS_LIMIT_MINUTES = "daily_reels_limit_minutes"
        private const val KEY_DAILY_REELS_USAGE_DAY = "daily_reels_usage_day"
        private const val KEY_DAILY_REELS_USAGE_MILLIS = "daily_reels_usage_millis"
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
