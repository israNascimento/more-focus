package br.com.morefocus.blocking

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyAppUsageTest {
    private val timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val clock = DailyUsageClock(timeZone)

    @Test
    fun addsVisibleAppSessionTime() {
        val startedAt = millis(year = 2026, month = 7, day = 8, hour = 10, minute = 0)
        val endedAt = startedAt + 10 * 60 * 1000L
        val state = DailyUsageState(dayKey = clock.dayKey(startedAt), usedMillis = 5 * 60 * 1000L)

        val updatedState = clock.addVisibleTime(
            state = state,
            startedAtMillis = startedAt,
            endedAtMillis = endedAt
        )

        assertEquals(clock.dayKey(startedAt), updatedState.dayKey)
        assertEquals(15 * 60 * 1000L, updatedState.usedMillis)
    }

    @Test
    fun resetsAppUsageWhenLocalDayChanges() {
        val yesterday = millis(year = 2026, month = 7, day = 8, hour = 23, minute = 30)
        val today = millis(year = 2026, month = 7, day = 9, hour = 0, minute = 1)
        val state = DailyUsageState(dayKey = clock.dayKey(yesterday), usedMillis = 30 * 60 * 1000L)

        val updatedState = clock.stateFor(state, today)

        assertEquals(clock.dayKey(today), updatedState.dayKey)
        assertEquals(0L, updatedState.usedMillis)
    }

    @Test
    fun carriesOnlyNewDayAppUsageWhenSessionCrossesMidnight() {
        val startedAt = millis(year = 2026, month = 7, day = 8, hour = 23, minute = 55)
        val endedAt = millis(year = 2026, month = 7, day = 9, hour = 0, minute = 5)
        val state = DailyUsageState(dayKey = clock.dayKey(startedAt), usedMillis = 0L)

        val updatedState = clock.addVisibleTime(
            state = state,
            startedAtMillis = startedAt,
            endedAtMillis = endedAt
        )

        assertEquals(clock.dayKey(endedAt), updatedState.dayKey)
        assertEquals(5 * 60 * 1000L, updatedState.usedMillis)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis
}
