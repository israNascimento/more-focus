package br.com.morefocus.settings

import android.content.Context
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.DailyUsageState
import androidx.core.content.edit

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
            dailyUsageMillis = dailyUsageState.usedMillis
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
        val sanitizedLimit = limitMinutes.takeIf { it in DAILY_REELS_LIMIT_MINUTE_OPTIONS }
            ?: DEFAULT_DAILY_REELS_LIMIT_MINUTES
        preferences.edit { putInt(KEY_DAILY_REELS_LIMIT_MINUTES, sanitizedLimit) }
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
    }
}
