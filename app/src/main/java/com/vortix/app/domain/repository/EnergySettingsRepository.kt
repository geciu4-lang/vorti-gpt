package com.vortix.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.vortix.app.domain.model.EnergyChangeMarker

interface EnergySettingsRepository {
    val monitoringEnabled: Flow<Boolean>
    val liveTelemetryEnabled: Flow<Boolean> get() = flowOf(false)
    val chargeWebhookEnabled: Flow<Boolean> get() = flowOf(false)
    val chargeWebhookUrl: Flow<String> get() = flowOf("")
    val chargeWebhookMethod: Flow<String> get() = flowOf("POST")
    val chargeWebhookTriggered: Flow<Boolean> get() = flowOf(false)
    val chargeWebhookLastResult: Flow<String> get() = flowOf("")
    val chargeLimitEnabled: Flow<Boolean>
    val chargeTargetPercent: Flow<Int>
    val chargeTargetAlertSent: Flow<Boolean>
    val lowBatteryAlertEnabled: Flow<Boolean>
    val lowBatteryThresholdPercent: Flow<Int>
    val lowBatteryAlertSent: Flow<Boolean>
    val thermalAlertEnabled: Flow<Boolean>
    val thermalThresholdCelsius: Flow<Int>
    val thermalAlertSent: Flow<Boolean>
    val ecoGuardEnabled: Flow<Boolean>
    val designCapacityMah: Flow<Int>
    val energyChangeMarkers: Flow<List<EnergyChangeMarker>>
    val batteryBaselineAtEpochMillis: Flow<Long>
    suspend fun setMonitoringEnabled(enabled: Boolean)
    suspend fun setLiveTelemetryEnabled(enabled: Boolean) = Unit
    suspend fun setChargeWebhookEnabled(enabled: Boolean) = Unit
    suspend fun setChargeWebhookUrl(url: String) = Unit
    suspend fun setChargeWebhookMethod(method: String) = Unit
    suspend fun setChargeWebhookTriggered(triggered: Boolean) = Unit
    suspend fun setChargeWebhookLastResult(result: String) = Unit
    suspend fun setChargeLimitEnabled(enabled: Boolean)
    suspend fun setChargeTargetPercent(percent: Int)
    suspend fun setChargeTargetAlertSent(sent: Boolean)
    suspend fun setLowBatteryAlertEnabled(enabled: Boolean)
    suspend fun setLowBatteryThresholdPercent(percent: Int)
    suspend fun setLowBatteryAlertSent(sent: Boolean)
    suspend fun setThermalAlertEnabled(enabled: Boolean)
    suspend fun setThermalThresholdCelsius(celsius: Int)
    suspend fun setThermalAlertSent(sent: Boolean)
    suspend fun setEcoGuardEnabled(enabled: Boolean)
    suspend fun setDesignCapacityMah(capacityMah: Int)
    suspend fun addEnergyChangeMarker(marker: EnergyChangeMarker)
    suspend fun setBatteryBaselineAtEpochMillis(epochMillis: Long)
}
