package com.vortix.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryThermalWearAnalyzerTest {
    @Test fun belowThirtyIsNormal() {
        val result = BatteryThermalWearAnalyzer.assess(29.9)!!
        assertEquals(1.0, result.multiplier, 0.0)
        assertEquals("Normal", result.label)
    }

    @Test fun thirtyToThirtyFiveIsMildIncrease() {
        assertEquals(1.2, BatteryThermalWearAnalyzer.assess(32.0)!!.multiplier, 0.0)
    }

    @Test fun thirtyFiveToFortyIsAccelerated() {
        assertEquals(1.5, BatteryThermalWearAnalyzer.assess(37.0)!!.multiplier, 0.0)
    }

    @Test fun fortyAndAboveIsHighWear() {
        assertEquals(2.4, BatteryThermalWearAnalyzer.assess(40.0)!!.multiplier, 0.0)
    }

    @Test fun invalidTemperatureIsUnavailable() {
        assertNull(BatteryThermalWearAnalyzer.assess(Double.NaN))
    }

    @Test fun moderateAndAboveAreWarnings() {
        assertFalse(SystemThermalStatus.isWarning(SystemThermalStatus.LIGHT))
        assertTrue(SystemThermalStatus.isWarning(SystemThermalStatus.MODERATE))
        assertTrue(SystemThermalStatus.isWarning(SystemThermalStatus.CRITICAL))
    }
}
