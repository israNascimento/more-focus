package br.com.morefocus.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.blocking.VideoUsageDay

internal class WeeklyVideoHistory(private val prefs: SharedPreferences, private val clock: DailyUsageClock) {
    // Migrate just the known daily usage. The old extension counter was capped and optional.
    fun prepare(now: Long, legacy: DailyUsageState, firstSample: Long = now) {
        val today = clock.dayKey(now)
        val first = if (firstSample < now) clock.dayKey(firstSample) else today
        val previousFirst = prefs.getString(FIRST_DAY, null)
        val startsEarlier = previousFirst == null || first < previousFirst
        if (!startsEarlier && prefs.getString(LAST_DAY, null) == today) return
        val oldest = clock.dayKey(clock.daysBefore(now, 6))
        prefs.edit {
            if (startsEarlier) putString(FIRST_DAY, first)
            if (previousFirst == null && legacy.dayKey in maxOf(first, oldest)..today) {
                putLong(USAGE + legacy.dayKey, legacy.usedMillis)
            }
            for (key in prefs.all.keys) {
                if ((key.startsWith(USAGE) || key.startsWith(EXTENSIONS)) && key.takeLast(10) < oldest) remove(key)
            }
            putString(LAST_DAY, today)
        }
    }

    fun addUsage(editor: SharedPreferences.Editor, deltas: Map<String, Long>, now: Long) {
        val oldest = clock.dayKey(clock.daysBefore(now, 6))
        for ((day, millis) in deltas) {
            if (day >= oldest) editor.putLong(USAGE + day, prefs.getLong(USAGE + day, 0L) + millis)
        }
    }

    fun addExtension(editor: SharedPreferences.Editor, now: Long) {
        val key = EXTENSIONS + clock.dayKey(now)
        editor.putInt(key, prefs.getInt(key, 0) + 1)
    }

    fun read(now: Long): List<VideoUsageDay> {
        val first = checkNotNull(prefs.getString(FIRST_DAY, null))
        return (0..6).map { offset ->
            val date = clock.daysBefore(now, offset)
            val day = clock.dayKey(date)
            val usage = if (day >= first) prefs.getLong(USAGE + day, 0L) else null
            VideoUsageDay(date, usage, prefs.getInt(EXTENSIONS + day, 0))
        }
    }

    private companion object {
        const val FIRST_DAY = "weekly_first_day"
        const val LAST_DAY = "weekly_last_day"
        const val USAGE = "weekly_video_ms_"
        const val EXTENSIONS = "weekly_extensions_"
    }
}
