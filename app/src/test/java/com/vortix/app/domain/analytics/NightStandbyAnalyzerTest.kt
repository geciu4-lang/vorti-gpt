package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightStandbyAnalyzerTest {
    @Test fun detectsFourPlusHourScreenOffSessionAndCalculatesMahFromChargeCounter() {
        val samples = (0..20).map { index ->
            sample(
                at = index * 15L * 60L * 1_000L,
                level = 90 - index / 4,
                chargeCounter = 4_500_000L - index * 12_500L
            )
        }
        val sessions = NightStandbyAnalyzer.analyze(samples)
        assertEquals(1, sessions.size)
        val session = sessions.single()
        assertEquals(5L * 60L * 60L * 1_000L, session.durationMillis)
        assertEquals(1.0, session.lossPercentPerHour, 0.0001)
        assertEquals(250.0, session.consumedMah!!, 0.0001)
        assertFalse(session.consumedMahIsEstimated)
    }

    @Test fun usesDesignCapacityOnlyAsAnExplicitEstimateWhenCounterIsUnavailable() {
        val samples = (0..16).map { index ->
            sample(
                at = index * 15L * 60L * 1_000L,
                level = 80 - index / 3,
                chargeCounter = null
            )
        }
        val session = NightStandbyAnalyzer.analyze(samples, designCapacityMah = 5_000).single()
        assertEquals(250.0, session.consumedMah!!, 0.0001)
        assertTrue(session.consumedMahIsEstimated)
    }

    @Test fun excludesScreenOnSessionsAndLargeSamplingGaps() {
        val screenOn = (0..20).map { index -> sample(index * 15L * 60L * 1_000L, 90 - index / 5).copy(screenInteractive = true) }
        val gap = listOf(
            sample(0L, 90),
            sample(2L * 60L * 60L * 1_000L, 88),
            sample(6L * 60L * 60L * 1_000L, 84)
        )
        assertTrue(NightStandbyAnalyzer.analyze(screenOn).isEmpty())
        assertTrue(NightStandbyAnalyzer.analyze(gap).isEmpty())
    }

    private fun sample(at: Long, level: Int, chargeCounter: Long? = 4_500_000L) = BatterySnapshot(
        capturedAtEpochMillis = at, levelPercent = level,
        status = BatteryStatus.DISCHARGING, pluggedSource = PluggedSource.NONE,
        temperatureCelsius = 28.0, voltageMillivolts = 4_000,
        chargeCounterMicroAh = chargeCounter, currentNowMicroAmps = -100_000,
        cycleCount = null, health = BatteryHealth.GOOD, screenInteractive = false
    )
}
