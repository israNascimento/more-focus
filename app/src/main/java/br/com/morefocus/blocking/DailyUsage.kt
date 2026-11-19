package br.com.morefocus.blocking

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

data class DailyUsageState(
    val dayKey: String,
    val usedMillis: Long
)

data class VideoUsageDay(val dateMillis: Long, val usedMillis: Long?, val extensions: Int)

class DailyUsageClock(
    private val timeZone: TimeZone? = null
) {
    fun dayKey(nowMillis: Long): String {
        val calendar = calendarAt(nowMillis)
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun daysBefore(nowMillis: Long, days: Int): Long = calendarAt(nowMillis).apply {
        add(Calendar.DAY_OF_MONTH, -days)
    }.timeInMillis

    fun stateFor(state: DailyUsageState, nowMillis: Long): DailyUsageState {
        val currentDayKey = dayKey(nowMillis)
        return if (state.dayKey == currentDayKey) {
            state.copy(usedMillis = max(0L, state.usedMillis))
        } else {
            DailyUsageState(dayKey = currentDayKey, usedMillis = 0L)
        }
    }

    fun addVisibleTime(
        state: DailyUsageState,
        startedAtMillis: Long?,
        endedAtMillis: Long,
        onSegment: ((day: String, durationMillis: Long) -> Unit)? = null
    ): DailyUsageState {
        val startedAt = startedAtMillis ?: return stateFor(state, endedAtMillis)
        if (endedAtMillis <= startedAt) return stateFor(state, endedAtMillis)

        var updatedState = state
        var segmentStart = startedAt

        while (segmentStart < endedAtMillis) {
            val segmentDayKey = dayKey(segmentStart)
            val segmentEnd = min(endedAtMillis, nextDayStartMillis(segmentStart))
            val currentUsedMillis = if (updatedState.dayKey == segmentDayKey) {
                max(0L, updatedState.usedMillis)
            } else {
                0L
            }

            updatedState = DailyUsageState(
                dayKey = segmentDayKey,
                usedMillis = currentUsedMillis + max(0L, segmentEnd - segmentStart)
            )
            onSegment?.invoke(segmentDayKey, segmentEnd - segmentStart)
            segmentStart = segmentEnd
        }

        return stateFor(updatedState, endedAtMillis)
    }

    fun millisUntilNextDay(nowMillis: Long): Long =
        max(0L, nextDayStartMillis(nowMillis) - nowMillis)

    private fun nextDayStartMillis(nowMillis: Long): Long =
        calendarAt(nowMillis).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun calendarAt(nowMillis: Long): Calendar =
        Calendar.getInstance(timeZone ?: TimeZone.getDefault()).apply {
            timeInMillis = nowMillis
        }
}
