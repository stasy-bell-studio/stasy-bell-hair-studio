package com.stasybell.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinanceFormattingTest {
    @Test fun cashWithKopecks() {
        assertEquals("1234,50 ₽", formatCash(123450))
        assertEquals("-100,00 ₽", formatCash(-10000))
        assertEquals("0,00 ₽", formatCash(0))
    }

    @Test fun parsesActualAmountWithoutRounding() {
        assertEquals(30025L, parseCash("300,25"))
        assertEquals(150000L, parseCash("1500"))
        assertNull(parseCash("0"))
        assertNull(parseCash("1,001"))
        assertNull(parseCash("92233720368547758,08"))
    }
}
