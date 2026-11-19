package br.com.morefocus.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.blocking.VideoUsageDay

internal class VideoUsageHistoryStore(private val prefs: SharedPreferences, private val clock: DailyUsageClock) {
    // Migrate just the known daily usage. The old extension counter was capped and optional.
    fun prepare(now: Long, legacy: DailyUsageState, firstSample: Long = now) {
        val today = clock.dayKey(now)
        val first = if (firstSample < now) clock.dayKey(firstSample) else today
        val previousFirst = prefs.getString(FIRST_DAY, null)
        if (previousFirst != null && first >= previousFirst && prefs.getString(LAST_DAY, null) == today) return
        val oldest = clock.dayKey(clock.dayStartDaysAgo(now, MAX_DAYS_AGO))
        // The weekly version discarded everything before the last seven days it processed.
        val weeklyCutoff = prefs.getString(LEGACY_LAST_DAY, null)?.let {
            clock.dayKey(clock.dayStartDaysAgo(clock.parseDayKey(it), 6))
        }
        val knownFrom = listOfNotNull(previousFirst, weeklyCutoff).maxOrNull() ?: first
        prefs.edit {
            putString(FIRST_DAY, minOf(knownFrom, first))
            if (weeklyCutoff != null) remove(LEGACY_LAST_DAY)
            if (previousFirst == null && legacy.dayKey in maxOf(first, oldest)..today) {
                putLong(USAGE + legacy.dayKey, legacy.usedMillis)
            }
            for (key in prefs.all.keys) {
                val dailyKey = key.startsWith(USAGE) || key.startsWith(APP_USAGE) || key.startsWith(EXTENSIONS)
                if (dailyKey && key.takeLast(10) < oldest) remove(key)
            }
            putString(LAST_DAY, today)
        }
    }

    fun addUsage(editor: SharedPreferences.Editor, deltas: Map<String, Long>, now: Long, packageName: String?) {
        val oldest = clock.dayKey(clock.dayStartDaysAgo(now, MAX_DAYS_AGO))
        for ((day, millis) in deltas) {
            if (day < oldest) continue
            editor.putLong(USAGE + day, prefs.getLong(USAGE + day, 0L) + millis)
            if (packageName != null) {
                val key = "${APP_USAGE}${packageName}_$day"
                editor.putLong(key, prefs.getLong(key, 0L) + millis)
            }
        }
    }

    fun addExtension(editor: SharedPreferences.Editor, now: Long) {
        val key = EXTENSIONS + clock.dayKey(now)
        editor.putInt(key, prefs.getInt(key, 0) + 1)
    }

    fun read(now: Long): List<VideoUsageDay> {
        val first = checkNotNull(prefs.getString(FIRST_DAY, null))
        val appUsage = mutableMapOf<String, MutableMap<String, Long>>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(APP_USAGE) && value is Long) {
                val separator = key.lastIndexOf('_')
                val day = key.substring(separator + 1)
                val app = key.substring(APP_USAGE.length, separator)
                appUsage.getOrPut(day) { mutableMapOf() }[app] = value
            }
        }
        return (0..MAX_DAYS_AGO).map { offset ->
            val date = clock.dayStartDaysAgo(now, offset)
            val day = clock.dayKey(date)
            val usage = if (day >= first) prefs.getLong(USAGE + day, 0L) else null
            VideoUsageDay(date, usage, prefs.getInt(EXTENSIONS + day, 0), appUsage[day].orEmpty())
        }
    }

    private companion object {
        const val MAX_DAYS_AGO = 90
        const val FIRST_DAY = "weekly_first_day"
        const val LAST_DAY = "video_history_last_day"
        const val LEGACY_LAST_DAY = "weekly_last_day"
        const val USAGE = "weekly_video_ms_"
        const val APP_USAGE = "weekly_app_video_ms_"
        const val EXTENSIONS = "weekly_extensions_"
    }
}
