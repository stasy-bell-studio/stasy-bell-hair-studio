package com.stasybell.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServicePricingTest {
    @Test fun validatesRubleValue() {
        assertEquals(620000, rublesToKopecks("6200"))
        assertNull(rublesToKopecks(""))
        assertNull(rublesToKopecks("-10"))
        assertNull(rublesToKopecks("999999999"))
    }
}
