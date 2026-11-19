package br.com.morefocus.blocking

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusScheduleTest {
    private val zone = TimeZone.getTimeZone("America/Sao_Paulo")
    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, hour, minute)
    }.timeInMillis

    @Test
    fun weekdaysIncludeFridayFromNineToNoonAndSkipSaturday() {
        val schedule = FocusSchedule(enabled = true)
        val friday = at(20, 9)
        val noon = at(20, 12)
        val period = FocusPeriod(friday, noon)
        assertEquals(period, schedule.currentOrNext(friday - 1, zone))
        assertEquals(period, schedule.currentOrNext(friday, zone))
        assertTrue(noon - 1 in period)
        assertFalse(noon in period)
        val monday = FocusPeriod(at(23, 9), at(23, 12))
        assertEquals(monday, schedule.currentOrNext(noon, zone))
        assertEquals(monday, schedule.currentOrNext(at(21, 10), zone))
        assertEquals(null, schedule.copy(enabled = false).currentOrNext(friday, zone))
    }

    @Test
    fun overnightScheduleBelongsToTheDayItStarts() {
        val schedule = FocusSchedule(true, FocusSchedule.dayBit(Calendar.FRIDAY), 22 * 60, 2 * 60)
        val period = FocusPeriod(at(20, 22), at(21, 2))
        assertEquals(period, schedule.currentOrNext(at(21, 1), zone))
        assertEquals(at(27, 22), schedule.currentOrNext(at(21, 2), zone)?.start)
    }
}
