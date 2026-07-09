package br.com.morefocus.data

import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.settings.MoreFocusPreferences

data class MoreFocusSettings(
    val focusEnabled: Boolean,
    val instagramReelsEnabled: Boolean,
    val dailyReelsLimitMinutes: Int,
    val appLimitRules: List<AppLimitRule> = emptyList()
)

interface MoreFocusSettingsRepository {
    val dailyReelsLimitMinuteOptions: List<Int>

    val appLimitMinuteOptions: List<Int>

    fun getSettings(): MoreFocusSettings

    fun getBlockSettings(nowMillis: Long = System.currentTimeMillis()): BlockSettings

    fun setFocusEnabled(enabled: Boolean)

    fun setInstagramReelsEnabled(enabled: Boolean)

    fun setDailyReelsLimitMinutes(limitMinutes: Int)

    fun setAppLimit(packageName: String, appName: String, limitMinutes: Int)

    fun setAppLimitMinutes(packageName: String, limitMinutes: Int)

    fun setAppLimitEnabled(packageName: String, enabled: Boolean)

    fun removeAppLimit(packageName: String)

    fun pauseFor(durationMillis: Long, nowMillis: Long = System.currentTimeMillis())

    fun recordDailyReelsUsage(startedAtMillis: Long?, endedAtMillis: Long): DailyUsageState

    fun recordDailyAppUsage(
        packageName: String,
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState
}

class SharedPreferencesMoreFocusSettingsRepository(
    private val preferences: MoreFocusPreferences
) : MoreFocusSettingsRepository {
    override val dailyReelsLimitMinuteOptions: List<Int> =
        MoreFocusPreferences.DAILY_REELS_LIMIT_MINUTE_OPTIONS

    override val appLimitMinuteOptions: List<Int> =
        MoreFocusPreferences.DAILY_REELS_LIMIT_MINUTE_OPTIONS

    override fun getSettings(): MoreFocusSettings =
        MoreFocusSettings(
            focusEnabled = preferences.isFocusEnabled(),
            instagramReelsEnabled = preferences.isInstagramReelsEnabled(),
            dailyReelsLimitMinutes = preferences.dailyReelsLimitMinutes(),
            appLimitRules = preferences.appLimitRules()
        )

    override fun getBlockSettings(nowMillis: Long): BlockSettings =
        preferences.getBlockSettings(nowMillis)

    override fun setFocusEnabled(enabled: Boolean) {
        preferences.setFocusEnabled(enabled)
    }

    override fun setInstagramReelsEnabled(enabled: Boolean) {
        preferences.setInstagramReelsEnabled(enabled)
    }

    override fun setDailyReelsLimitMinutes(limitMinutes: Int) {
        preferences.setDailyReelsLimitMinutes(limitMinutes)
    }

    override fun setAppLimit(packageName: String, appName: String, limitMinutes: Int) {
        preferences.setAppLimit(packageName, appName, limitMinutes)
    }

    override fun setAppLimitMinutes(packageName: String, limitMinutes: Int) {
        preferences.setAppLimitMinutes(packageName, limitMinutes)
    }

    override fun setAppLimitEnabled(packageName: String, enabled: Boolean) {
        preferences.setAppLimitEnabled(packageName, enabled)
    }

    override fun removeAppLimit(packageName: String) {
        preferences.removeAppLimit(packageName)
    }

    override fun pauseFor(durationMillis: Long, nowMillis: Long) {
        preferences.pauseFor(durationMillis, nowMillis)
    }

    override fun recordDailyReelsUsage(
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState =
        preferences.recordDailyReelsUsage(startedAtMillis, endedAtMillis)

    override fun recordDailyAppUsage(
        packageName: String,
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState =
        preferences.recordDailyAppUsage(packageName, startedAtMillis, endedAtMillis)
}
