package com.vortix.app.platform.telemetry

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTelemetryFormatterTest {
    @Test fun includesElectricalValuesAndRemainingChargeTarget() {
        val content = LiveTelemetryFormatter.format(snapshot(), chargeLimitEnabled = true, chargeTargetPercent = 80)
        assertTrue(content.title.contains("TELEMETRİ"))
        assertTrue(content.compactText.contains("W"))
        assertTrue(content.compactText.contains("mA"))
        assertTrue(content.compactText.contains("°C"))
        assertTrue(content.expandedText.contains("hedefe %15 kaldı"))
    }

    @Test fun reportsUnpluggedStateWithoutClaimingCharging() {
        val content = LiveTelemetryFormatter.format(
            snapshot().copy(status = BatteryStatus.DISCHARGING, pluggedSource = PluggedSource.NONE),
            chargeLimitEnabled = false,
            chargeTargetPercent = 80
        )
        assertTrue(content.expandedText.contains("Fişe takılı değil"))
    }

    private fun snapshot() = BatterySnapshot(
        capturedAtEpochMillis = 1L, levelPercent = 65, status = BatteryStatus.CHARGING,
        pluggedSource = PluggedSource.AC, temperatureCelsius = 32.5,
        voltageMillivolts = 4_200, chargeCounterMicroAh = 2_600_000,
        currentNowMicroAmps = 1_500_000, cycleCount = null, health = BatteryHealth.GOOD
    )
}
