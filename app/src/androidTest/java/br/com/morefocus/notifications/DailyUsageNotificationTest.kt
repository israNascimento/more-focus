package br.com.morefocus.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import br.com.morefocus.MainActivity
import br.com.morefocus.MoreFocusApplication
import br.com.morefocus.R
import br.com.morefocus.settings.MoreFocusPreferences
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

class DailyUsageNotificationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchingOffHidesNotificationAndSwitchingOnShowsCurrentSharedUsage() {
        val context = compose.activity.application as MoreFocusApplication
        val preferences = MoreFocusPreferences(context)
        val notification = context.appContainer.dailyUsageNotification
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val noon = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        preferences.recordDailyVerticalVideosUsage(null, noon - DateUtils.DAY_IN_MILLIS)
        preferences.setDailyVerticalVideosLimitSeconds(30)
        preferences.recordDailyVerticalVideosUsage(noon, noon + 10_000)

        fun awaitSummary(remaining: Long, used: Long) {
            val title = context.getString(
                R.string.usage_notification_remaining, DateUtils.formatElapsedTime(remaining)
            )
            val text = context.getString(
                R.string.usage_notification_used, DateUtils.formatElapsedTime(used)
            )
            compose.waitUntil(5_000) {
                val posted = manager.activeNotifications.singleOrNull()?.notification
                posted != null && posted.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
                    posted.extras.getString(Notification.EXTRA_TITLE) == title &&
                    posted.extras.getString(Notification.EXTRA_TEXT) == text
            }
        }

        try {
            compose.runOnIdle { notification.start() }
            awaitSummary(20, 10)
            compose.onNodeWithText(context.getString(R.string.preferences_tab_title)).performClick()
            val toggle = compose.onNodeWithContentDescription(
                context.getString(R.string.preference_usage_notification_title)
            )
            toggle.performScrollTo().assertIsOn().performClick()
            compose.waitUntil(5_000) { manager.activeNotifications.isEmpty() }
            assertFalse(MoreFocusPreferences(context).showUsageNotification())

            preferences.recordDailyVerticalVideosUsage(noon + 10_000, noon + 35_000)
            toggle.assertIsOff().performClick()
            awaitSummary(0, 35)
            preferences.setDailyVerticalVideosLimitSeconds(300)
            awaitSummary(265, 35)
            preferences.recordDailyVerticalVideosUsage(null, noon - DateUtils.DAY_IN_MILLIS)
            awaitSummary(300, 0)
        } finally {
            compose.runOnIdle { notification.stop() }
            preferences.setShowUsageNotification(true)
        }
    }
}
