package com.vortix.app.platform.telemetry

import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import java.util.Locale
import kotlin.math.abs

/** Platform-independent text formatter for live notification content. */
object LiveTelemetryFormatter {
    data class Content(val title: String, val compactText: String, val expandedText: String)

    fun format(snapshot: BatterySnapshot, chargeLimitEnabled: Boolean, chargeTargetPercent: Int): Content {
        val power = snapshot.batteryPowerMilliWatts?.let { String.format(Locale.US, "%.1f W", abs(it) / 1_000.0) } ?: "— W"
        val current = snapshot.currentNowMicroAmps?.let { "%.0f mA".format(Locale.US, abs(it.toDouble()) / 1_000.0) } ?: "— mA"
        val temperature = snapshot.temperatureCelsius?.let { String.format(Locale.US, "%.1f °C", it) } ?: "— °C"
        val level = snapshot.levelPercent?.let { "%$it" } ?: "Pil seviyesi bilinmiyor"
        val plugged = snapshot.pluggedSource != PluggedSource.NONE && snapshot.pluggedSource != PluggedSource.UNKNOWN
        val charging = snapshot.status == BatteryStatus.CHARGING || snapshot.status == BatteryStatus.FULL
        val status = when {
            !plugged -> "Fişe takılı değil · $level"
            snapshot.status == BatteryStatus.FULL -> "Pil dolu · $level"
            charging && chargeLimitEnabled && snapshot.levelPercent != null -> {
                val remaining = (chargeTargetPercent - snapshot.levelPercent).coerceAtLeast(0)
                "Şarj oluyor · hedefe %$remaining kaldı"
            }
            charging -> "Şarj oluyor · $level"
            else -> "Güç bağlı · $level"
        }
        return Content(
            title = "VORTIX · CANLI TELEMETRİ",
            compactText = "$power  ·  $current  ·  $temperature",
            expandedText = "Güç: $power\nAkım: $current\nSıcaklık: $temperature\nDurum: $status"
        )
    }
}
