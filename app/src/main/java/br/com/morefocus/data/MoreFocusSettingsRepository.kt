package br.com.morefocus.data

import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.settings.MoreFocusPreferences

data class MoreFocusSettings(
    val focusEnabled: Boolean,
    val instagramReelsEnabled: Boolean,
    val dailyReelsLimitMinutes: Int
)

interface MoreFocusSettingsRepository {
    val dailyReelsLimitMinuteOptions: List<Int>

    fun getSettings(): MoreFocusSettings

    fun getBlockSettings(nowMillis: Long = System.currentTimeMillis()): BlockSettings

    fun setFocusEnabled(enabled: Boolean)

    fun setInstagramReelsEnabled(enabled: Boolean)

    fun setDailyReelsLimitMinutes(limitMinutes: Int)

    fun pauseFor(durationMillis: Long, nowMillis: Long = System.currentTimeMillis())

    fun recordDailyReelsUsage(startedAtMillis: Long?, endedAtMillis: Long): DailyUsageState
}

class SharedPreferencesMoreFocusSettingsRepository(
    private val preferences: MoreFocusPreferences
) : MoreFocusSettingsRepository {
    override val dailyReelsLimitMinuteOptions: List<Int> =
        MoreFocusPreferences.DAILY_REELS_LIMIT_MINUTE_OPTIONS

    override fun getSettings(): MoreFocusSettings =
        MoreFocusSettings(
            focusEnabled = preferences.isFocusEnabled(),
            instagramReelsEnabled = preferences.isInstagramReelsEnabled(),
            dailyReelsLimitMinutes = preferences.dailyReelsLimitMinutes()
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

    override fun pauseFor(durationMillis: Long, nowMillis: Long) {
        preferences.pauseFor(durationMillis, nowMillis)
    }

    override fun recordDailyReelsUsage(
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState =
        preferences.recordDailyReelsUsage(startedAtMillis, endedAtMillis)
}
