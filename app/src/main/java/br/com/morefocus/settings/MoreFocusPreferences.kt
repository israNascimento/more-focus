package br.com.morefocus.settings

import android.content.Context
import androidx.core.content.edit
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.DailyUsageState

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
            dailyLimitMinutes = dailyReelsLimitMinutes(),
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

    fun dailyReelsLimitMinutes(): Int {
        val storedLimit = preferences.getInt(
            KEY_DAILY_REELS_LIMIT_MINUTES,
            DEFAULT_DAILY_REELS_LIMIT_MINUTES
        )
        return storedLimit.takeIf { it in DAILY_REELS_LIMIT_MINUTE_OPTIONS }
            ?: DEFAULT_DAILY_REELS_LIMIT_MINUTES
    }

    fun setDailyReelsLimitMinutes(limitMinutes: Int) {
        val sanitizedLimit = sanitizedLimitMinutes(limitMinutes)
        preferences.edit { putInt(KEY_DAILY_REELS_LIMIT_MINUTES, sanitizedLimit) }
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
                    limitMinutes = appLimitMinutes(packageName),
                    usageMillis = usageState.usedMillis,
                    enabled = preferences.getBoolean(appEnabledKey(packageName), true)
                )
            }
            .sortedWith(
                compareBy<AppLimitRule> { it.appName.lowercase() }
                    .thenBy { it.packageName }
            )

    fun setAppLimit(packageName: String, appName: String, limitMinutes: Int) {
        val sanitizedPackageName = packageName.trim().takeIf { it.isNotBlank() } ?: return
        val sanitizedAppName = appName.trim().takeIf { it.isNotBlank() } ?: sanitizedPackageName
        val sanitizedLimit = sanitizedLimitMinutes(limitMinutes)
        val packages = appLimitPackages() + sanitizedPackageName

        preferences.edit {
            putStringSet(KEY_APP_LIMIT_PACKAGES, packages)
            putString(appNameKey(sanitizedPackageName), sanitizedAppName)
            putInt(appLimitMinutesKey(sanitizedPackageName), sanitizedLimit)
            putBoolean(appEnabledKey(sanitizedPackageName), true)
        }
    }

    fun setAppLimitMinutes(packageName: String, limitMinutes: Int) {
        val sanitizedPackageName = packageName.trim().takeIf { it.isNotBlank() } ?: return
        if (sanitizedPackageName !in appLimitPackages()) return

        preferences.edit {
            putInt(appLimitMinutesKey(sanitizedPackageName), sanitizedLimitMinutes(limitMinutes))
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
            remove(appEnabledKey(sanitizedPackageName))
            remove(appUsageDayKey(sanitizedPackageName))
            remove(appUsageMillisKey(sanitizedPackageName))
        }
    }

    fun pauseFor(durationMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit {
            putLong(KEY_PAUSE_UNTIL_MILLIS, nowMillis + durationMillis)
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

    private fun appLimitMinutes(packageName: String): Int {
        val storedLimit = preferences.getInt(
            appLimitMinutesKey(packageName),
            DEFAULT_DAILY_REELS_LIMIT_MINUTES
        )
        return sanitizedLimitMinutes(storedLimit)
    }

    private fun sanitizedLimitMinutes(limitMinutes: Int): Int =
        limitMinutes.takeIf { it in DAILY_REELS_LIMIT_MINUTE_OPTIONS }
            ?: DEFAULT_DAILY_REELS_LIMIT_MINUTES

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

    private fun appNameKey(packageName: String): String =
        "$KEY_APP_LIMIT_APP_NAME_PREFIX$packageName"

    private fun appLimitMinutesKey(packageName: String): String =
        "$KEY_APP_LIMIT_MINUTES_PREFIX$packageName"

    private fun appEnabledKey(packageName: String): String =
        "$KEY_APP_LIMIT_ENABLED_PREFIX$packageName"

    private fun appUsageDayKey(packageName: String): String =
        "$KEY_APP_LIMIT_USAGE_DAY_PREFIX$packageName"

    private fun appUsageMillisKey(packageName: String): String =
        "$KEY_APP_LIMIT_USAGE_MILLIS_PREFIX$packageName"

    companion object {
        val DAILY_REELS_LIMIT_MINUTE_OPTIONS = listOf(5, 10, 15, 30, 60)
        const val DEFAULT_DAILY_REELS_LIMIT_MINUTES = 15

        private const val PREFERENCES_NAME = "more_focus_preferences"
        private const val KEY_FOCUS_ENABLED = "focus_enabled"
        private const val KEY_INSTAGRAM_REELS_ENABLED = "instagram_reels_enabled"
        private const val KEY_PAUSE_UNTIL_MILLIS = "pause_until_millis"
        private const val KEY_DAILY_REELS_LIMIT_MINUTES = "daily_reels_limit_minutes"
        private const val KEY_DAILY_REELS_USAGE_DAY = "daily_reels_usage_day"
        private const val KEY_DAILY_REELS_USAGE_MILLIS = "daily_reels_usage_millis"
        private const val KEY_APP_LIMIT_PACKAGES = "app_limit_packages"
        private const val KEY_APP_LIMIT_APP_NAME_PREFIX = "app_limit_app_name_"
        private const val KEY_APP_LIMIT_MINUTES_PREFIX = "app_limit_minutes_"
        private const val KEY_APP_LIMIT_ENABLED_PREFIX = "app_limit_enabled_"
        private const val KEY_APP_LIMIT_USAGE_DAY_PREFIX = "app_limit_usage_day_"
        private const val KEY_APP_LIMIT_USAGE_MILLIS_PREFIX = "app_limit_usage_millis_"
    }
}
