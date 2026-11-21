package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Rates a 30-second battery-side observation window. This is not USB input power,
 * USB-PD negotiation, or a direct electrical cable-integrity measurement.
 */
object ChargingBenchmarkAnalyzer {
    enum class Grade(val title: String) {
        A_PLUS("A+ Süper Hızlı"),
        A("A Dengeli"),
        B("B Standart"),
        C("C Zayıf Kablo-Adaptör")
    }

    data class Result(
        val sampleCount: Int,
        val peakPowerWatts: Double,
        val averageCurrentMilliamps: Double,
        val voltageVariationPercent: Double?,
        val temperatureIncreaseCelsius: Double?,
        val score: Int,
        val grade: Grade,
        val summary: String
    )

    fun analyze(samples: List<BatterySnapshot>): Result? {
        val chargingSamples = samples.filter { sample ->
            sample.status == BatteryStatus.CHARGING &&
                sample.pluggedSource != PluggedSource.NONE &&
                sample.pluggedSource != PluggedSource.UNKNOWN
        }
        if (chargingSamples.size < 2) return null

        val electricalSamples = chargingSamples.mapNotNull { sample ->
            val currentUa = sample.currentNowMicroAmps ?: return@mapNotNull null
            val voltageMv = sample.voltageMillivolts?.takeIf { it > 0 } ?: return@mapNotNull null
            val currentMa = abs(currentUa.toDouble()) / 1_000.0
            val powerW = abs(currentUa.toDouble() * voltageMv.toDouble()) / 1_000_000_000.0
            if (!currentMa.isFinite() || !powerW.isFinite()) null else currentMa to powerW
        }
        if (electricalSamples.size < 2) return null

        val currents = electricalSamples.map { it.first }
        val powers = electricalSamples.map { it.second }
        val voltages = chargingSamples.mapNotNull { it.voltageMillivolts?.takeIf { v -> v > 0 }?.toDouble() }
        val averageCurrent = currents.average()
        val peakPower = powers.maxOrNull() ?: return null
        val voltageVariation = if (voltages.size >= 2) {
            val mean = voltages.average()
            if (mean > 0.0) sqrt(voltages.sumOf { (it - mean) * (it - mean) } / voltages.size) / mean * 100.0 else null
        } else null
        val firstTemperature = chargingSamples.firstNotNullOfOrNull { it.temperatureCelsius }
        val lastTemperature = chargingSamples.asReversed().firstNotNullOfOrNull { it.temperatureCelsius }
        val temperatureDelta = if (firstTemperature != null && lastTemperature != null) {
            lastTemperature - firstTemperature
        } else null

        val stabilityScore = (1.0 - ((voltageVariation ?: 8.0) / 12.0)).coerceIn(0.0, 1.0)
        val thermalScore = (1.0 - (maxOf(temperatureDelta ?: 0.0, 0.0) / 8.0)).coerceIn(0.0, 1.0)
        val score = (
            (peakPower / 25.0).coerceIn(0.0, 1.0) * 40.0 +
                (averageCurrent / 4_000.0).coerceIn(0.0, 1.0) * 25.0 +
                stabilityScore * 20.0 +
                thermalScore * 15.0
            ).toInt().coerceIn(0, 100)

        val grade = when {
            score >= 85 -> Grade.A_PLUS
            score >= 70 -> Grade.A
            score >= 50 -> Grade.B
            else -> Grade.C
        }
        val summary = when (grade) {
            Grade.A_PLUS -> "Güç aktarımı güçlü, voltaj kararlı ve sıcaklık artışı sınırlı."
            Grade.A -> "Şarj performansı dengeli; ölçümlerde belirgin bir kararsızlık görünmüyor."
            Grade.B -> "Standart seviyede performans. Daha yüksek hız için cihazın ve adaptörün desteklediği değerleri kontrol edin."
            Grade.C -> "Ölçülen batarya tarafı performansı düşük veya kararsız. Farklı adaptör/kabloyla tekrar karşılaştırın."
        }

        return Result(
            sampleCount = electricalSamples.size,
            peakPowerWatts = peakPower,
            averageCurrentMilliamps = averageCurrent,
            voltageVariationPercent = voltageVariation,
            temperatureIncreaseCelsius = temperatureDelta,
            score = score,
            grade = grade,
            summary = summary
        )
    }
}
