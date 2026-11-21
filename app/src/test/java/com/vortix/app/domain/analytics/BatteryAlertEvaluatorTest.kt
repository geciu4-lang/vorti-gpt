package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryAlertEvaluatorTest {
    @Test
    fun normalSnapshotHasNoAlert() {
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(snapshot(), false, 42, false, 20))
    }

    @Test
    fun twentyFourDegreeThermalNoneWithEightyPercentTargetRemainsNormal() {
        val normal = snapshot(level = 80, status = BatteryStatus.CHARGING, source = PluggedSource.AC, temperature = 24.0)
            .copy(thermalStatus = SystemThermalStatus.NONE)
        assertTrue(BatteryAlertEvaluator.activeAlertReasons(normal, true, 42, true, 15).isEmpty())
    }

    @Test
    fun chargeTargetCompletionIsNotAnAnomaly() {
        val atTarget = snapshot(level = 80, status = BatteryStatus.CHARGING, source = PluggedSource.AC, temperature = 33.6)
            .copy(thermalStatus = SystemThermalStatus.NONE)
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(atTarget, true, 42, true, 15))
    }

    @Test
    fun staleOemOverheatHealthFlagDoesNotOverrideNormalMeasuredTemperature() {
        val normal = snapshot(temperature = 24.0).copy(
            thermalStatus = SystemThermalStatus.NONE,
            health = BatteryHealth.OVERHEAT
        )
        assertTrue(BatteryAlertEvaluator.activeAlertReasons(normal, true, 42, false, 20).isEmpty())
    }

    @Test
    fun moderateThermalStatusTriggersWarning() {
        val reasons = BatteryAlertEvaluator.activeAlertReasons(
            snapshot(temperature = 24.0).copy(thermalStatus = SystemThermalStatus.MODERATE),
            false, 42, false, 20
        )
        assertEquals(1, reasons.size)
        assertTrue(reasons.single().contains("MODERATE"))
    }

    @Test
    fun lightThermalStatusDoesNotTriggerWarning() {
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(
            snapshot(temperature = 24.0).copy(thermalStatus = SystemThermalStatus.LIGHT),
            false, 42, false, 20
        ))
    }

    @Test
    fun unknownOutOfRangeThermalStatusDoesNotTriggerWarning() {
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(
            snapshot(temperature = 24.0).copy(thermalStatus = 99),
            false, 42, false, 20
        ))
    }

    @Test
    fun severeThermalStatusTriggersWarningEvenWhenTemperatureIsLow() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(
            snapshot(temperature = 25.0).copy(thermalStatus = SystemThermalStatus.SEVERE),
            false, 42, false, 20
        ))
    }

    @Test
    fun highTemperatureTriggersWhenThermalAlertIsEnabled() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(snapshot(temperature = 43.0), true, 42, false, 20))
    }

    @Test
    fun highTemperatureDoesNotTriggerWhenThermalAlertIsDisabledUnlessSystemThrottlingIsModerate() {
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(snapshot(temperature = 43.0), false, 42, false, 20))
    }

    @Test
    fun lowBatteryBelowThresholdTriggersOnlyWhileDischargingAndEnabled() {
        assertTrue(BatteryAlertEvaluator.hasActiveAlert(snapshot(level = 14), false, 42, true, 15))
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(snapshot(level = 14), false, 42, false, 15))
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(
            snapshot(level = 14, status = BatteryStatus.CHARGING, source = PluggedSource.AC),
            false, 42, true, 15
        ))
    }

    @Test
    fun lowBatteryExactlyAtThresholdIsNotBelowThreshold() {
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(snapshot(level = 15), false, 42, true, 15))
    }

    @Test
    fun lowBatteryAndThermalAnomaliesCanBeReportedTogether() {
        val reasons = BatteryAlertEvaluator.activeAlertReasons(
            snapshot(level = 10, temperature = 44.0).copy(thermalStatus = SystemThermalStatus.MODERATE),
            true, 42, true, 15
        )
        assertEquals(3, reasons.size)
    }

    @Test
    fun staleChargeTargetValueCannotBecomeLowBatteryThreshold() {
        val normal = snapshot(
            level = 80,
            status = BatteryStatus.CHARGING,
            source = PluggedSource.AC,
            temperature = 24.0
        ).copy(thermalStatus = SystemThermalStatus.NONE)
        assertTrue(BatteryAlertEvaluator.activeAlertReasons(normal, true, 0, true, 80).isEmpty())
    }

    @Test
    fun normalTemperatureDoesNotTriggerWhenPersistedThermalThresholdIsInvalid() {
        val normal = snapshot(temperature = 24.0).copy(thermalStatus = SystemThermalStatus.NONE)
        assertFalse(BatteryAlertEvaluator.hasActiveAlert(normal, true, 0, false, 20))
    }

    private fun snapshot(
        level: Int = 65,
        status: BatteryStatus = BatteryStatus.DISCHARGING,
        source: PluggedSource = PluggedSource.NONE,
        temperature: Double = 28.0
    ) = BatterySnapshot(
        capturedAtEpochMillis = 1L,
        levelPercent = level,
        status = status,
        pluggedSource = source,
        temperatureCelsius = temperature,
        voltageMillivolts = 4_000,
        chargeCounterMicroAh = 2_600_000,
        currentNowMicroAmps = -200_000,
        cycleCount = null,
        health = BatteryHealth.GOOD,
        screenInteractive = true
    )
}
