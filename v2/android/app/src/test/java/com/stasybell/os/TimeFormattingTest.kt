package com.stasybell.os

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormattingTest {
    @Test
    fun moscowTimeCarriesTimezoneOffset() {
        assertEquals("2026-11-04T10:30:00+03:00", toMoscowOffset("2026-11-04", "10:30:00"))
    }

    @Test
    fun serverUtcBecomesStudioTime() {
        assertEquals("04.11.2026 10:30", localVisitTime("2026-11-04T07:30:00+00:00"))
    }
}
