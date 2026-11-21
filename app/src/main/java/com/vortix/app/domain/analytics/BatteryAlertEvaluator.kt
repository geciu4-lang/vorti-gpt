package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource

/** Pure alert rules shared by the dashboard and its unit tests. */
object BatteryAlertEvaluator {
    fun hasActiveAlert(
        snapshot: BatterySnapshot?,
        thermalAlertEnabled: Boolean,
        thermalThresholdCelsius: Int,
        lowBatteryAlertEnabled: Boolean,
        lowBatteryThresholdPercent: Int,
        chargeLimitEnabled: Boolean,
        chargeTargetPercent: Int
    ): Boolean {
        snapshot ?: return false
        val temperature = snapshot.temperatureCelsius
        val level = snapshot.levelPercent
        val charging = snapshot.status == BatteryStatus.CHARGING || snapshot.status == BatteryStatus.FULL
        val plugged = snapshot.pluggedSource != PluggedSource.NONE &&
            snapshot.pluggedSource != PluggedSource.UNKNOWN

        return snapshot.health == BatteryHealth.OVERHEAT ||
            SystemThermalStatus.isWarning(snapshot.thermalStatus) ||
            (thermalAlertEnabled && temperature != null && temperature >= thermalThresholdCelsius) ||
            (lowBatteryAlertEnabled && snapshot.status == BatteryStatus.DISCHARGING &&
                level != null && level <= lowBatteryThresholdPercent) ||
            (chargeLimitEnabled && charging && plugged && level != null && level >= chargeTargetPercent)
    }
}
