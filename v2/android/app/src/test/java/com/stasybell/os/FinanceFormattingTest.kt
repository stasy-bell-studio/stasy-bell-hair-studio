package com.stasybell.os

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceFormattingTest {
    @Test fun cashWithKopecks() {
        assertEquals("1234,50 ₽", formatCash(123450))
        assertEquals("-100,00 ₽", formatCash(-10000))
        assertEquals("0,00 ₽", formatCash(0))
    }
}
