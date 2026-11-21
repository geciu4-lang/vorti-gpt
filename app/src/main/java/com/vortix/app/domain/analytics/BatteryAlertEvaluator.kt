package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource

/** Pure alert rules shared by the dashboard and its unit tests. */
object BatteryAlertEvaluator {
    /** Returns user-readable causes so a warning is never an unexplained permanent badge. */
    fun activeAlertReasons(
        snapshot: BatterySnapshot?,
        thermalAlertEnabled: Boolean,
        thermalThresholdCelsius: Int,
        lowBatteryAlertEnabled: Boolean,
        lowBatteryThresholdPercent: Int,
        chargeLimitEnabled: Boolean,
        chargeTargetPercent: Int
    ): List<String> {
        snapshot ?: return emptyList()
        val temperature = snapshot.temperatureCelsius
        val level = snapshot.levelPercent
        val charging = snapshot.status == BatteryStatus.CHARGING || snapshot.status == BatteryStatus.FULL
        val plugged = snapshot.pluggedSource != PluggedSource.NONE &&
            snapshot.pluggedSource != PluggedSource.UNKNOWN
        return buildList {
            if (snapshot.health == BatteryHealth.OVERHEAT) add("Android pil sağlık durumu aşırı sıcak bildiriyor")
            if (SystemThermalStatus.isWarning(snapshot.thermalStatus)) {
                add("Sistem termal durumu ${SystemThermalStatus.label(snapshot.thermalStatus)}")
            }
            if (thermalAlertEnabled && temperature != null && temperature >= thermalThresholdCelsius) {
                add("Pil sıcaklığı ${temperature.toInt()}°C (eşik ${thermalThresholdCelsius}°C)")
            }
            if (lowBatteryAlertEnabled && snapshot.status == BatteryStatus.DISCHARGING &&
                level != null && level <= lowBatteryThresholdPercent
            ) {
                add("Pil seviyesi %$level (eşik %$lowBatteryThresholdPercent)")
            }
            if (chargeLimitEnabled && charging && plugged && level != null && level >= chargeTargetPercent) {
                add("Şarj hedefi %$chargeTargetPercent seviyesine ulaştı")
            }
        }
    }

    fun hasActiveAlert(
        snapshot: BatterySnapshot?,
        thermalAlertEnabled: Boolean,
        thermalThresholdCelsius: Int,
        lowBatteryAlertEnabled: Boolean,
        lowBatteryThresholdPercent: Int,
        chargeLimitEnabled: Boolean,
        chargeTargetPercent: Int
    ): Boolean = activeAlertReasons(
        snapshot,
        thermalAlertEnabled,
        thermalThresholdCelsius,
        lowBatteryAlertEnabled,
        lowBatteryThresholdPercent,
        chargeLimitEnabled,
        chargeTargetPercent
    ).isNotEmpty()
}
