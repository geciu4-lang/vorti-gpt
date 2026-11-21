package com.vortix.app.domain.analytics

import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource

/** Detects long, screen-off, unplugged discharge intervals from observed samples only. */
object NightStandbyAnalyzer {
    const val DEFAULT_MINIMUM_DURATION_MILLIS = 4L * 60L * 60L * 1_000L
    const val DEFAULT_MAX_SAMPLE_GAP_MILLIS = 45L * 60L * 1_000L

    data class Session(
        val startEpochMillis: Long,
        val endEpochMillis: Long,
        val startLevelPercent: Int,
        val endLevelPercent: Int,
        val batteryLossPercent: Int,
        val lossPercentPerHour: Double,
        val consumedMah: Double?,
        val consumedMahIsEstimated: Boolean,
        val sampleCount: Int,
        val mostActiveAppName: String? = null,
        val mostActiveAppForegroundMillis: Long? = null
    ) {
        val durationMillis: Long get() = endEpochMillis - startEpochMillis
    }

    fun analyze(
        samples: List<BatterySnapshot>,
        designCapacityMah: Int? = null,
        minimumDurationMillis: Long = DEFAULT_MINIMUM_DURATION_MILLIS,
        maximumSampleGapMillis: Long = DEFAULT_MAX_SAMPLE_GAP_MILLIS
    ): List<Session> {
        if (samples.size < 2) return emptyList()
        val ordered = samples.sortedBy { it.capturedAtEpochMillis }
        val sessions = mutableListOf<Session>()
        var current = mutableListOf<BatterySnapshot>()

        fun flush() {
            if (current.size >= 2) {
                val first = current.first()
                val last = current.last()
                val duration = last.capturedAtEpochMillis - first.capturedAtEpochMillis
                val startLevel = first.levelPercent
                val endLevel = last.levelPercent
                if (duration >= minimumDurationMillis && startLevel != null && endLevel != null) {
                    val loss = (startLevel - endLevel).coerceAtLeast(0)
                    val lossRate = loss.toDouble() / (duration.toDouble() / 3_600_000.0)
                    val counterMah = if (first.chargeCounterMicroAh != null && last.chargeCounterMicroAh != null) {
                        ((first.chargeCounterMicroAh - last.chargeCounterMicroAh).toDouble() / 1_000.0)
                            .takeIf { it >= 0.0 }
                    } else null
                    val estimatedMah = if (counterMah == null && designCapacityMah != null && designCapacityMah > 0) {
                        designCapacityMah * loss / 100.0
                    } else null
                    sessions += Session(
                        startEpochMillis = first.capturedAtEpochMillis,
                        endEpochMillis = last.capturedAtEpochMillis,
                        startLevelPercent = startLevel,
                        endLevelPercent = endLevel,
                        batteryLossPercent = loss,
                        lossPercentPerHour = lossRate,
                        consumedMah = counterMah ?: estimatedMah,
                        consumedMahIsEstimated = counterMah == null && estimatedMah != null,
                        sampleCount = current.size
                    )
                }
            }
            current = mutableListOf()
        }

        ordered.forEach { sample ->
            val eligible = sample.screenInteractive == false &&
                sample.status == BatteryStatus.DISCHARGING &&
                sample.pluggedSource == PluggedSource.NONE &&
                sample.levelPercent != null
            if (!eligible) {
                flush()
                return@forEach
            }
            val previous = current.lastOrNull()
            if (previous != null && sample.capturedAtEpochMillis - previous.capturedAtEpochMillis > maximumSampleGapMillis) {
                flush()
            }
            current += sample
        }
        flush()
        return sessions
    }
}
