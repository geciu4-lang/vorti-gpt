package com.vortix.app.data.work

import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.EnergyChangeMarker
import com.vortix.app.domain.model.PluggedSource
import com.vortix.app.domain.repository.EnergySettingsRepository
import com.vortix.app.platform.webhook.ChargeTargetWebhookManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeWebhookTriggerCoordinatorTest {
    @Test
    fun doesNotDispatchWhenWebhookDisabled() = runTest {
        val settings = FakeEnergySettings(webhookEnabled = false)
        val coordinator = ChargeWebhookTriggerCoordinator(settings, ChargeTargetWebhookManager())

        assertNull(coordinator.onBatterySnapshot(chargingSnapshot(level = 85)))
        assertFalse(settings.triggered.value)
    }

    @Test
    fun dispatchesOnlyOnceUntilCableIsRemoved() = runTest {
        val settings = FakeEnergySettings(webhookEnabled = true, url = "http://not-https.invalid/hook")
        val coordinator = ChargeWebhookTriggerCoordinator(settings, ChargeTargetWebhookManager())

        val first = coordinator.onBatterySnapshot(chargingSnapshot(level = 85))
        assertNotNull(first)
        assertFalse(first!!.success) // HTTP is rejected before any network request.
        assertTrue(settings.triggered.value)
        assertEquals(first.message, settings.lastResult.value)

        assertNull(coordinator.onBatterySnapshot(chargingSnapshot(level = 90)))

        coordinator.onBatterySnapshot(chargingSnapshot(level = 79))
        assertFalse(settings.triggered.value)

        val nextCycle = coordinator.onBatterySnapshot(chargingSnapshot(level = 85))
        assertNotNull(nextCycle)
        assertFalse(nextCycle!!.success)
    }

    @Test
    fun doesNotDispatchWhenNotChargingOrBelowTarget() = runTest {
        val settings = FakeEnergySettings(webhookEnabled = true, url = "https://example.invalid/hook")
        val coordinator = ChargeWebhookTriggerCoordinator(settings, ChargeTargetWebhookManager())

        assertNull(coordinator.onBatterySnapshot(chargingSnapshot(level = 79)))
        assertNull(coordinator.onBatterySnapshot(chargingSnapshot(level = 90, status = BatteryStatus.DISCHARGING)))
        assertFalse(settings.triggered.value)
    }

    private fun chargingSnapshot(
        level: Int,
        status: BatteryStatus = BatteryStatus.CHARGING,
        plugged: PluggedSource = PluggedSource.AC
    ) = BatterySnapshot(
        capturedAtEpochMillis = 1_000L,
        levelPercent = level,
        status = status,
        pluggedSource = plugged,
        temperatureCelsius = 30.0,
        voltageMillivolts = 4_000,
        chargeCounterMicroAh = null,
        currentNowMicroAmps = 1_000_000,
        cycleCount = null,
        health = null
    )

    private class FakeEnergySettings(
        webhookEnabled: Boolean,
        url: String = ""
    ) : EnergySettingsRepository {
        val monitoring = MutableStateFlow(false)
        val webhookOn = MutableStateFlow(webhookEnabled)
        val webhookUrl = MutableStateFlow(url)
        val webhookMethod = MutableStateFlow("POST")
        val triggered = MutableStateFlow(false)
        val lastResult = MutableStateFlow("")
        private val chargeTarget = MutableStateFlow(80)

        override val monitoringEnabled: Flow<Boolean> = monitoring
        override val chargeWebhookEnabled: Flow<Boolean> = webhookOn
        override val chargeWebhookUrl: Flow<String> = webhookUrl
        override val chargeWebhookMethod: Flow<String> = webhookMethod
        override val chargeWebhookTriggered: Flow<Boolean> = triggered
        override val chargeWebhookLastResult: Flow<String> = lastResult
        override val liveTelemetryEnabled: Flow<Boolean> = flowOf(false)
        override val chargeLimitEnabled: Flow<Boolean> = flowOf(true)
        override val chargeTargetPercent: Flow<Int> = chargeTarget
        override val chargeTargetAlertSent: Flow<Boolean> = flowOf(false)
        override val lowBatteryAlertEnabled: Flow<Boolean> = flowOf(false)
        override val lowBatteryThresholdPercent: Flow<Int> = flowOf(20)
        override val lowBatteryAlertSent: Flow<Boolean> = flowOf(false)
        override val thermalAlertEnabled: Flow<Boolean> = flowOf(false)
        override val thermalThresholdCelsius: Flow<Int> = flowOf(42)
        override val thermalAlertSent: Flow<Boolean> = flowOf(false)
        override val ecoGuardEnabled: Flow<Boolean> = flowOf(false)
        override val designCapacityMah: Flow<Int> = flowOf(0)
        override val energyChangeMarkers: Flow<List<EnergyChangeMarker>> = flowOf(emptyList())
        override val batteryBaselineAtEpochMillis: Flow<Long> = flowOf(0L)

        override suspend fun setMonitoringEnabled(enabled: Boolean) { monitoring.value = enabled }
        override suspend fun setChargeWebhookEnabled(enabled: Boolean) { webhookOn.value = enabled }
        override suspend fun setChargeWebhookUrl(url: String) { webhookUrl.value = url }
        override suspend fun setChargeWebhookMethod(method: String) { webhookMethod.value = method }
        override suspend fun setChargeWebhookTriggered(value: Boolean) { triggered.value = value }
        override suspend fun setChargeWebhookLastResult(result: String) { lastResult.value = result }
        override suspend fun setChargeLimitEnabled(enabled: Boolean) = Unit
        override suspend fun setChargeTargetPercent(percent: Int) { chargeTarget.value = percent }
        override suspend fun setChargeTargetAlertSent(sent: Boolean) = Unit
        override suspend fun setLowBatteryAlertEnabled(enabled: Boolean) = Unit
        override suspend fun setLowBatteryThresholdPercent(percent: Int) = Unit
        override suspend fun setLowBatteryAlertSent(sent: Boolean) = Unit
        override suspend fun setThermalAlertEnabled(enabled: Boolean) = Unit
        override suspend fun setThermalThresholdCelsius(celsius: Int) = Unit
        override suspend fun setThermalAlertSent(sent: Boolean) = Unit
        override suspend fun setEcoGuardEnabled(enabled: Boolean) = Unit
        override suspend fun setDesignCapacityMah(capacityMah: Int) = Unit
        override suspend fun addEnergyChangeMarker(marker: EnergyChangeMarker) = Unit
        override suspend fun setBatteryBaselineAtEpochMillis(epochMillis: Long) = Unit
    }
}
