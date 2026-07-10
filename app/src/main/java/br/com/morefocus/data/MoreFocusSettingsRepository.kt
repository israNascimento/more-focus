package br.com.morefocus.data

import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.blocking.PauseExtensionTarget
import br.com.morefocus.settings.MoreFocusPreferences

data class MoreFocusSettings(
    val focusEnabled: Boolean,
    val instagramReelsEnabled: Boolean,
    val youtubeShortsEnabled: Boolean = true,
    val exponentialPauseLockEnabled: Boolean = true,
    val dailyVerticalVideosLimitSeconds: Int,
    val appLimitRules: List<AppLimitRule> = emptyList()
)

interface MoreFocusSettingsRepository {
    val dailyVerticalVideosLimitSecondOptions: List<Int>

    val appLimitSecondOptions: List<Int>

    fun getSettings(): MoreFocusSettings

    fun getBlockSettings(nowMillis: Long = System.currentTimeMillis()): BlockSettings

    fun setFocusEnabled(enabled: Boolean)

    fun setInstagramReelsEnabled(enabled: Boolean)

    fun setYouTubeShortsEnabled(enabled: Boolean)

    fun setExponentialPauseLockEnabled(enabled: Boolean)

    fun setDailyVerticalVideosLimitSeconds(limitSeconds: Int)

    fun setAppLimit(packageName: String, appName: String, limitSeconds: Int)

    fun setAppLimitSeconds(packageName: String, limitSeconds: Int)

    fun setAppLimitEnabled(packageName: String, enabled: Boolean)

    fun removeAppLimit(packageName: String)

    fun pauseExtensionLockSeconds(
        target: PauseExtensionTarget,
        nowMillis: Long = System.currentTimeMillis()
    ): Int

    fun pauseFor(
        durationMillis: Long,
        target: PauseExtensionTarget,
        nowMillis: Long = System.currentTimeMillis()
    )

    fun recordDailyVerticalVideosUsage(
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState

    fun recordDailyAppUsage(
        packageName: String,
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState
}

class SharedPreferencesMoreFocusSettingsRepository(
    private val preferences: MoreFocusPreferences
) : MoreFocusSettingsRepository {
    override val dailyVerticalVideosLimitSecondOptions: List<Int> =
        MoreFocusPreferences.DAILY_VERTICAL_VIDEOS_LIMIT_SECOND_OPTIONS

    override val appLimitSecondOptions: List<Int> =
        MoreFocusPreferences.DAILY_VERTICAL_VIDEOS_LIMIT_SECOND_OPTIONS

    override fun getSettings(): MoreFocusSettings =
        MoreFocusSettings(
            focusEnabled = preferences.isFocusEnabled(),
            instagramReelsEnabled = preferences.isInstagramReelsEnabled(),
            youtubeShortsEnabled = preferences.isYouTubeShortsEnabled(),
            exponentialPauseLockEnabled = preferences.isExponentialPauseLockEnabled(),
            dailyVerticalVideosLimitSeconds = preferences.dailyVerticalVideosLimitSeconds(),
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

    override fun setYouTubeShortsEnabled(enabled: Boolean) {
        preferences.setYouTubeShortsEnabled(enabled)
    }

    override fun setExponentialPauseLockEnabled(enabled: Boolean) {
        preferences.setExponentialPauseLockEnabled(enabled)
    }

    override fun setDailyVerticalVideosLimitSeconds(limitSeconds: Int) {
        preferences.setDailyVerticalVideosLimitSeconds(limitSeconds)
    }

    override fun setAppLimit(packageName: String, appName: String, limitSeconds: Int) {
        preferences.setAppLimit(packageName, appName, limitSeconds)
    }

    override fun setAppLimitSeconds(packageName: String, limitSeconds: Int) {
        preferences.setAppLimitSeconds(packageName, limitSeconds)
    }

    override fun setAppLimitEnabled(packageName: String, enabled: Boolean) {
        preferences.setAppLimitEnabled(packageName, enabled)
    }

    override fun removeAppLimit(packageName: String) {
        preferences.removeAppLimit(packageName)
    }

    override fun pauseExtensionLockSeconds(
        target: PauseExtensionTarget,
        nowMillis: Long
    ): Int = preferences.pauseExtensionLockSeconds(target, nowMillis)

    override fun pauseFor(
        durationMillis: Long,
        target: PauseExtensionTarget,
        nowMillis: Long
    ) {
        preferences.pauseFor(durationMillis, target, nowMillis)
    }

    override fun recordDailyVerticalVideosUsage(
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState =
        preferences.recordDailyVerticalVideosUsage(startedAtMillis, endedAtMillis)

    override fun recordDailyAppUsage(
        packageName: String,
        startedAtMillis: Long?,
        endedAtMillis: Long
    ): DailyUsageState =
        preferences.recordDailyAppUsage(packageName, startedAtMillis, endedAtMillis)
}
