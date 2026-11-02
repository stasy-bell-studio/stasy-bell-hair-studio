package com.stasybell.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MasterEditorValidationTest {
    @Test fun twoValidScheduleTimes() {
        assertEquals(WorkingTime("09:00", "13:00"), parseWindow("09:00", "13:00"))
        assertNull(parseWindow("13:00", "09:00"))
        assertNull(parseWindow("25:00", "28:00"))
    }
    @Test fun materialsUseExactThousandths() {
        assertEquals(25500L, parseMilliunits("25,5"))
        assertEquals(1L, parseMilliunits("0.001"))
        assertNull(parseMilliunits("0.0001"))
        assertNull(parseMilliunits("-2"))
        assertNull(parseMilliunits("qwerty"))
    }
}
