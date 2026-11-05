package com.stasybell.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

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

    @Test fun chartLabelsGroupRublesAndKeepNonzeroKopecks() {
        assertEquals("10 200 ₽", formatCashLabel(1_020_000))
        assertEquals("2 099,75 ₽", formatCashLabel(209_975))
        assertEquals("-100 ₽", formatCashLabel(-10_000))
        assertEquals("0 ₽", formatCashLabel(0))
    }

    @Test fun weeklyTotalsUseCalendarRangesAndMoscowDates() {
        val weeks = summarizeCashByWeek(
            listOf(
                entry("income-1", "income", 1_200_000, "2026-01-07T20:59:00Z"),
                entry("expense-2", "expense", 180_000, "2026-01-07T21:00:00Z"),
                entry("income-3", "income", 850_000, "2026-01-15T09:00:00+03:00"),
                entry("expense-4", "expense", 300_000, "2026-01-22T09:00:00+03:00"),
                entry("income-5", "income", 600_000, "2026-01-29T09:00:00+03:00"),
                entry("outside", "expense", 90_000, "2026-01-31T21:00:00Z"),
            ),
            YearMonth.of(2026, 1),
        )

        assertEquals(listOf("1–7", "8–14", "15–21", "22–28", "29–31"), weeks.map { it.label })
        assertEquals(1_200_000L, weeks[0].incomeKopecks)
        assertEquals(180_000L, weeks[1].expenseKopecks)
        assertEquals(850_000L, weeks[2].incomeKopecks)
        assertEquals(300_000L, weeks[3].expenseKopecks)
        assertEquals(600_000L, weeks[4].incomeKopecks)
    }

    @Test fun reversalsReduceTheirOriginalIncomeOrExpenseWeek() {
        val weeks = summarizeCashByWeek(
            listOf(
                entry("income", "income", 1_200_000, "2026-02-04T12:00:00+03:00"),
                entry("income-reversal", "income", 1_200_000, "2026-02-04T12:00:00+03:00", "income"),
                entry("remaining-income", "income", 50_000, "2026-02-05T12:00:00+03:00"),
                entry("expense", "expense", 180_000, "2026-02-04T12:00:00+03:00"),
                entry("expense-reversal", "expense", 180_000, "2026-02-04T12:00:00+03:00", "expense"),
            ),
            YearMonth.of(2026, 2),
        )

        assertEquals(4, weeks.size)
        assertEquals("22–28", weeks.last().label)
        assertEquals(50_000L, weeks[0].incomeKopecks)
        assertEquals(0L, weeks[0].expenseKopecks)
        assertEquals(0L, weeks[1].incomeKopecks)
        assertEquals(0L, weeks[1].expenseKopecks)
    }

    private fun entry(
        id: String,
        kind: String,
        amountKopecks: Long,
        occurredAt: String,
        correctionOfId: String? = null,
    ) = CashEntry(id, kind, "other", amountKopecks, occurredAt, correctionOfId, null)
}
