package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryAlertEvaluatorTest {
    @Test fun normalSnapshotHasNoAlert() {
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(snapshot(), false, 42, false, 20, false, 80))
    }

    @Test fun normal25DegreeSnapshotWithNoneThermalStatusHasNoAlertReasons() {
        val normal = snapshot(temperature = 25.0).copy(thermalStatus = SystemThermalStatus.NONE)
        assertTrue(BatteryAlertEvaluator.activeAlertReasons(
            normal, false, 42, false, 20, false, 80
        ).isEmpty())
    }

    @Test fun alertBadgeReasonExplainsReachedChargeTarget() {
        val reasons = BatteryAlertEvaluator.activeAlertReasons(
            snapshot(level = 80, status = BatteryStatus.CHARGING, source = PluggedSource.AC),
            false, 42, false, 20, true, 80
        )
        assertTrue(reasons.any { it.contains("Şarj hedefi") })
    }

    @Test fun moderateSystemThermalStatusTriggersAlertWithoutUserThreshold() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(
            snapshot().copy(thermalStatus = SystemThermalStatus.MODERATE), false, 42, false, 20, false, 80
        ))
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(
            snapshot().copy(thermalStatus = SystemThermalStatus.LIGHT), false, 42, false, 20, false, 80
        ))
    }

    @Test fun highTemperatureTriggersEnabledThermalAlert() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(snapshot(temperature = 43.0), true, 42, false, 20, false, 80))
    }

    @Test fun lowBatteryTriggersOnlyWhenEnabledAndDischarging() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(
            snapshot(level = 15), false, 42, true, 20, false, 80
        ))
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(
            snapshot(level = 15), false, 42, false, 20, false, 80
        ))
    }

    @Test fun androidOverheatHealthTriggersAlertEvenWithoutCustomThreshold() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(
            snapshot().copy(health = BatteryHealth.OVERHEAT), false, 42, false, 20, false, 80
        ))
    }

    @Test fun reachedChargingTargetTriggersAlert() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(
            snapshot(level = 85, status = BatteryStatus.CHARGING, source = PluggedSource.AC),
            false, 42, false, 20, true, 85
        ))
    }

    @Test fun fullBatteryOnTargetAlsoTriggersAlert() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(
            snapshot(level = 100, status = BatteryStatus.FULL, source = PluggedSource.AC),
            false, 42, false, 20, true, 80
        ))
    }

    private fun snapshot(
        level: Int = 65,
        status: BatteryStatus = BatteryStatus.DISCHARGING,
        source: PluggedSource = PluggedSource.NONE,
        temperature: Double = 28.0
    ) = BatterySnapshot(
        capturedAtEpochMillis = 1L, levelPercent = level, status = status,
        pluggedSource = source, temperatureCelsius = temperature, voltageMillivolts = 4_000,
        chargeCounterMicroAh = 2_600_000, currentNowMicroAmps = -200_000,
        cycleCount = null, health = BatteryHealth.GOOD, screenInteractive = true
    )
}
