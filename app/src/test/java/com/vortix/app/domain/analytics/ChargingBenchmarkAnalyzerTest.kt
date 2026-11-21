package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingBenchmarkAnalyzerTest {
    @Test fun analyzesChargingSamplesAndReturnsElectricalMetrics() {
        val samples = (0..30).map { index ->
            sample(index, currentUa = 4_250_000L, voltageMv = 4_000, temperature = 30.0 + index * 0.03)
        }
        val result = ChargingBenchmarkAnalyzer.analyze(samples)
        assertNotNull(result)
        assertEquals(17.0, result!!.peakPowerWatts, 0.0001)
        assertEquals(4_250.0, result.averageCurrentMilliamps, 0.0001)
        assertEquals(0.9, result.temperatureIncreaseCelsius!!, 0.0001)
        assertTrue(result.score in 0..100)
        assertEquals(ChargingBenchmarkAnalyzer.Grade.A_PLUS, result.grade)
    }

    @Test fun refusesSamplesWhenDeviceIsNotCharging() {
        val samples = (0..5).map { index -> sample(index, status = BatteryStatus.DISCHARGING, source = PluggedSource.NONE) }
        assertNull(ChargingBenchmarkAnalyzer.analyze(samples))
    }

    private fun sample(
        index: Int,
        currentUa: Long = 4_000_000L,
        voltageMv: Int = 4_000,
        temperature: Double = 30.0,
        status: BatteryStatus = BatteryStatus.CHARGING,
        source: PluggedSource = PluggedSource.AC
    ) = BatterySnapshot(
        capturedAtEpochMillis = index * 1_000L, levelPercent = 60, status = status,
        pluggedSource = source, temperatureCelsius = temperature, voltageMillivolts = voltageMv,
        chargeCounterMicroAh = 2_400_000L, currentNowMicroAmps = currentUa,
        cycleCount = null, health = BatteryHealth.GOOD, screenInteractive = true
    )
}
