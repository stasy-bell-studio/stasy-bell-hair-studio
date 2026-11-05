package com.stasybell.os

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DayTimelineTest {
    private fun visit(id: String, start: String, end: String, status: String) = VisitItem(
        id = id, clientId = null, serviceId = null,
        startsAt = "2026-11-04T${start}:00+03:00", occupiedUntil = "2026-11-04T${end}:00+03:00",
        localTime = "04.11.2026 $start", localEnd = "04.11.2026 $end",
        status = status, source = "phone", kind = "appointment",
        durationMinutes = 60, quotedPriceKopecks = null,
    )

    @Test fun separatesWindowsAndAccountsForOccupiedBuffers() {
        val windows = listOf(WorkingTime("09:00", "13:00"), WorkingTime("14:00", "19:00"))
        val visits = listOf(
            visit("active", "10:00", "11:15", "confirmed"),
            visit("cancelled", "12:00", "12:30", "cancelled"),
            visit("block", "14:30", "15:00", "blocked"),
        )
        val slices = dayTimeline(LocalDate.of(2026, 11, 4), windows, visits)
        assertEquals(listOf(
            "09:00–10:00 empty", "10:00–11:15 active", "11:15–13:00 empty",
            "14:00–14:30 empty", "14:30–15:00 block", "15:00–19:00 empty",
        ), slices.map { "${it.start}–${it.end} ${it.visit?.id ?: "empty"}" })
    }
}
