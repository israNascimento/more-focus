package br.com.morefocus.settings

import android.content.Context
import br.com.morefocus.blocking.BlockSettings

class MoreFocusPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getBlockSettings(): BlockSettings =
        BlockSettings(
            focusEnabled = preferences.getBoolean(KEY_FOCUS_ENABLED, true),
            instagramReelsEnabled = preferences.getBoolean(KEY_INSTAGRAM_REELS_ENABLED, true),
            pauseUntilMillis = preferences.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L)
        )

    fun isFocusEnabled(): Boolean =
        preferences.getBoolean(KEY_FOCUS_ENABLED, true)

    fun setFocusEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FOCUS_ENABLED, enabled).apply()
    }

    fun isInstagramReelsEnabled(): Boolean =
        preferences.getBoolean(KEY_INSTAGRAM_REELS_ENABLED, true)

    fun setInstagramReelsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_INSTAGRAM_REELS_ENABLED, enabled).apply()
    }

    fun pauseFor(durationMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(KEY_PAUSE_UNTIL_MILLIS, nowMillis + durationMillis)
            .apply()
    }

    fun pauseUntilMillis(): Long =
        preferences.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L)

    companion object {
        private const val PREFERENCES_NAME = "more_focus_preferences"
        private const val KEY_FOCUS_ENABLED = "focus_enabled"
        private const val KEY_INSTAGRAM_REELS_ENABLED = "instagram_reels_enabled"
        private const val KEY_PAUSE_UNTIL_MILLIS = "pause_until_millis"
    }
}
