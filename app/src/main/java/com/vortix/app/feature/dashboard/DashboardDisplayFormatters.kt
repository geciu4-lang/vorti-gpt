package com.vortix.app.feature.dashboard

import com.vortix.app.domain.model.BatterySnapshot
import kotlin.math.roundToLong

internal data class BatteryHistoryChartPoint(
    val capturedAtEpochMillis: Long,
    val levelPercent: Int
)

internal data class BatteryHistoryChartSummary(
    val points: List<BatteryHistoryChartPoint>,
    val minimumLevelPercent: Int?,
    val maximumLevelPercent: Int?
) {
    val startAtEpochMillis: Long?
        get() = points.firstOrNull()?.capturedAtEpochMillis

    val endAtEpochMillis: Long?
        get() = points.lastOrNull()?.capturedAtEpochMillis
}

/**
 * Formats Android's signed battery current in mA without displaying rounded negative zero.
 * Null remains unavailable; small values that round to zero are rendered as `0 mA`.
 */
internal fun formatCurrentMilliAmps(currentMicroAmps: Long?): String {
    if (currentMicroAmps == null) return "—"
    val roundedMilliAmps = (currentMicroAmps / 1_000.0).roundToLong()
    return if (roundedMilliAmps == 0L) "0 mA" else "$roundedMilliAmps mA"
}

/** Builds chart labels and points from the latest 24 chronologically ordered valid samples. */
internal fun summarizeBatteryHistory(snapshots: List<BatterySnapshot>): BatteryHistoryChartSummary {
    val points = snapshots
        .sortedBy(BatterySnapshot::capturedAtEpochMillis)
        .takeLast(24)
        .mapNotNull { snapshot ->
            snapshot.levelPercent?.coerceIn(0, 100)?.let { level ->
                BatteryHistoryChartPoint(snapshot.capturedAtEpochMillis, level)
            }
        }

    return BatteryHistoryChartSummary(
        points = points,
        minimumLevelPercent = points.minOfOrNull(BatteryHistoryChartPoint::levelPercent),
        maximumLevelPercent = points.maxOfOrNull(BatteryHistoryChartPoint::levelPercent)
    )
}
