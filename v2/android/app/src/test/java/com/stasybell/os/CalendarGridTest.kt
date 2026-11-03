package com.stasybell.os

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarGridTest {
    @Test fun weekStartsMondayAndContainsSevenDays() {
        val dates = calendarWeekDates(LocalDate.of(2026, 11, 4))

        assertEquals(LocalDate.of(2026, 11, 2), dates.first())
        assertEquals(LocalDate.of(2026, 11, 8), dates.last())
        assertEquals(7, dates.size)
    }

    @Test fun monthGridHasSixCompleteMondayFirstWeeks() {
        val dates = calendarMonthDates(LocalDate.of(2026, 11, 4))

        assertEquals(42, dates.size)
        assertEquals(LocalDate.of(2026, 10, 26), dates.first())
        assertEquals(LocalDate.of(2026, 12, 6), dates.last())
        assertTrue(dates.contains(LocalDate.of(2026, 11, 1)))
        assertTrue(dates.contains(LocalDate.of(2026, 11, 30)))
    }
}
