package com.vortix.app.domain.analytics

/** Compares heat rise to battery-side average power; a heuristic for same-device comparisons. */
object ThermalEfficiencyScorer {
    fun grade(averagePowerWatts: Double?, temperatureDeltaCelsius: Double?): String? {
        val power = averagePowerWatts?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val delta = temperatureDeltaCelsius?.takeIf { it.isFinite() } ?: return null
        val heatPerWatt = delta.coerceAtLeast(0.0) / power
        return when {
            heatPerWatt <= 0.05 -> "A+"
            heatPerWatt <= 0.12 -> "A"
            heatPerWatt <= 0.25 -> "B"
            else -> "C"
        }
    }
}
