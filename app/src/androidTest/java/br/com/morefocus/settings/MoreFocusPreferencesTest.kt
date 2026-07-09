package br.com.morefocus.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.morefocus.blocking.PauseExtensionTarget
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
    fun exponentialPauseLockIsEnabledByDefaultAndCanBeChanged() {
        assertTrue(preferences.isExponentialPauseLockEnabled())

        preferences.setExponentialPauseLockEnabled(false)

        assertFalse(preferences.isExponentialPauseLockEnabled())
    }

    @Test
    fun thirtySecondLimitCanBePersistedForReelsAndApps() {
        preferences.setDailyReelsLimitSeconds(30)
        preferences.setAppLimit("com.example.video", "Video", 30)

        assertEquals(30, preferences.dailyReelsLimitSeconds())
        assertEquals(30, preferences.appLimitRules().single().limitSeconds)
    }

    @Test
    fun legacyMinuteValuesAreConvertedToSeconds() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("daily_reels_limit_minutes", 5)
            .putStringSet("app_limit_packages", setOf("com.example.video"))
            .putInt("app_limit_minutes_com.example.video", 10)
            .commit()

        assertEquals(5 * 60, preferences.dailyReelsLimitSeconds())
        assertEquals(10 * 60, preferences.appLimitRules().single().limitSeconds)
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
                PauseExtensionTarget.InstagramReels,
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
        val target = PauseExtensionTarget.InstagramReels
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
