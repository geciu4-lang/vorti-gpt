package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus

/**
 * Only genuine battery/thermal anomalies turn the dashboard status badge red.
 * Charge-target completion is an expected user-configured event, not an anomaly.
 */
object BatteryAlertEvaluator {
    fun activeAlertReasons(
        snapshot: BatterySnapshot?,
        thermalAlertEnabled: Boolean,
        thermalThresholdCelsius: Int,
        lowBatteryAlertEnabled: Boolean,
        lowBatteryThresholdPercent: Int
    ): List<String> {
        snapshot ?: return emptyList()
        val temperature = snapshot.temperatureCelsius
        val level = snapshot.levelPercent

        return buildList {
            if (SystemThermalStatus.isWarning(snapshot.thermalStatus)) {
                add("Sistem termal durumu ${SystemThermalStatus.label(snapshot.thermalStatus)}")
            }
            // Match the controls' supported ranges so corrupted/stale DataStore values
            // cannot turn a normal 24°C battery into a permanent warning.
            val safeThermalThreshold = thermalThresholdCelsius.coerceIn(36, 48)
            val safeLowBatteryThreshold = lowBatteryThresholdPercent.coerceIn(10, 40)

            if (thermalAlertEnabled && temperature != null && temperature.isFinite() &&
                temperature >= safeThermalThreshold
            ) {
                add("Pil sıcaklığı ${temperature.toInt()}°C (eşik ${safeThermalThreshold}°C)")
            }
            if (lowBatteryAlertEnabled && snapshot.status == BatteryStatus.DISCHARGING &&
                level != null && level in 0..100 && level < safeLowBatteryThreshold
            ) {
                add("Pil seviyesi %$level (eşik altı: %$safeLowBatteryThreshold)")
            }
        }
    }

    fun hasActiveAlert(
        snapshot: BatterySnapshot?,
        thermalAlertEnabled: Boolean,
        thermalThresholdCelsius: Int,
        lowBatteryAlertEnabled: Boolean,
        lowBatteryThresholdPercent: Int
    ): Boolean = activeAlertReasons(
        snapshot = snapshot,
        thermalAlertEnabled = thermalAlertEnabled,
        thermalThresholdCelsius = thermalThresholdCelsius,
        lowBatteryAlertEnabled = lowBatteryAlertEnabled,
        lowBatteryThresholdPercent = lowBatteryThresholdPercent
    ).isNotEmpty()
}
