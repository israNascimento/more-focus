package br.com.morefocus.blocking

import java.util.Calendar
import java.util.TimeZone

data class FocusSchedule(
    val enabled: Boolean = false,
    val days: Int = WEEKDAYS,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 12 * 60
) {
    fun includes(dayOfWeek: Int): Boolean = days and dayBit(dayOfWeek) != 0

    // A period belongs to the day it starts, even when it ends the following morning.
    fun currentOrNext(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): FocusPeriod? {
        if (!enabled || days == 0 || startMinute == endMinute) return null
        val day = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (endMinute < startMinute) add(Calendar.DAY_OF_MONTH, -1)
        }
        // Yesterday can still be active. The next start can be a full week away.
        repeat(9) {
            if (includes(day.get(Calendar.DAY_OF_WEEK))) {
                val start = day.atMinute(startMinute)
                val endDay = day.clone() as Calendar
                if (endMinute < startMinute) endDay.add(Calendar.DAY_OF_MONTH, 1)
                val end = endDay.atMinute(endMinute)
                if (end > nowMillis && end > start) return FocusPeriod(start, end)
            }
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    private fun Calendar.atMinute(minute: Int): Long = (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }.timeInMillis

    companion object {
        fun dayBit(dayOfWeek: Int): Int = 1 shl (dayOfWeek - Calendar.SUNDAY)
        private val WEEKDAYS = (Calendar.MONDAY..Calendar.FRIDAY).sumOf(::dayBit)
    }
}

data class FocusPeriod(val start: Long, val end: Long) {
    operator fun contains(time: Long): Boolean = time >= start && time < end
}
