package br.com.morefocus.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.morefocus.MainActivity
import br.com.morefocus.R
import br.com.morefocus.settings.MoreFocusPreferences

class DailyUsageNotification(
    private val context: Context,
    private val preferences: MoreFocusPreferences
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = Runnable { refresh() }
    private var unsubscribe: (() -> Unit)? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }
    private val builder by lazy {
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_usage_notification)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(PendingIntent.getActivity(context, 0, openApp, PendingIntent.FLAG_IMMUTABLE))
    }

    fun start() {
        if (unsubscribe != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.usage_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
                addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        unsubscribe = preferences.observeDailyVideoUsage {
            handler.removeCallbacks(refreshTask)
            handler.post(refreshTask)
        }
        refresh()
    }

    fun refresh() {
        if (unsubscribe == null || !manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val settings = preferences.getBlockSettingsForPackage(null)
        if (!preferences.showUsageNotification() || !settings.focusEnabled ||
            !(settings.instagramReelsEnabled || settings.youtubeShortsEnabled || settings.xVideosEnabled)
        ) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val remaining = DateUtils.formatElapsedTime(
            (settings.remainingDailyVerticalVideosLimitMillis + DateUtils.SECOND_IN_MILLIS - 1) /
                DateUtils.SECOND_IN_MILLIS
        )
        val used = DateUtils.formatElapsedTime(settings.dailyVerticalVideosUsageMillis / DateUtils.SECOND_IN_MILLIS)
        val notification = builder
            .setContentTitle(context.getString(R.string.usage_notification_remaining, remaining))
            .setContentText(context.getString(R.string.usage_notification_used, used))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun stop() {
        val removeListener = unsubscribe ?: return
        removeListener()
        unsubscribe = null
        handler.removeCallbacks(refreshTask)
        context.unregisterReceiver(receiver)
        manager.cancel(NOTIFICATION_ID)
    }

    private companion object {
        const val CHANNEL_ID = "daily_video_usage"
        const val NOTIFICATION_ID = 1
    }
}
