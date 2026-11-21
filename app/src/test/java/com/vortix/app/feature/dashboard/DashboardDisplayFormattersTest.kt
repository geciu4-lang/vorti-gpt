package com.vortix.app.feature.dashboard

import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardDisplayFormattersTest {

    @Test
    fun zeroCurrentNeverRendersAsNegativeZero() {
        assertEquals("0 mA", formatCurrentMilliAmps(0L))
        assertEquals("0 mA", formatCurrentMilliAmps(-499L))
        assertEquals("0 mA", formatCurrentMilliAmps(499L))
    }

    @Test
    fun signedCurrentIsPreservedWhenRoundedValueIsNonZero() {
        assertEquals("-450 mA", formatCurrentMilliAmps(-450_000L))
        assertEquals("125 mA", formatCurrentMilliAmps(125_000L))
    }

    @Test
    fun missingCurrentRemainsUnavailable() {
        assertEquals("—", formatCurrentMilliAmps(null))
    }

    @Test
    fun chartSummarySortsSamplesAndIgnoresMissingLevels() {
        val summary = summarizeBatteryHistory(
            listOf(
                snapshot(at = 3_000L, level = null),
                snapshot(at = 2_000L, level = 41),
                snapshot(at = 1_000L, level = 76)
            )
        )

        assertEquals(1_000L, summary.startAtEpochMillis)
        assertEquals(2_000L, summary.endAtEpochMillis)
        assertEquals(41, summary.minimumLevelPercent)
        assertEquals(76, summary.maximumLevelPercent)
        assertEquals(listOf(1_000L, 2_000L), summary.points.map { it.capturedAtEpochMillis })
    }

    @Test
    fun chartSummaryClampsOutOfRangeBatteryLevels() {
        val summary = summarizeBatteryHistory(
            listOf(snapshot(at = 1L, level = -5), snapshot(at = 2L, level = 120))
        )

        assertEquals(0, summary.minimumLevelPercent)
        assertEquals(100, summary.maximumLevelPercent)
    }

    @Test
    fun emptyChartSummaryHasNoTimeOrLevelLabels() {
        val summary = summarizeBatteryHistory(emptyList())

        assertNull(summary.startAtEpochMillis)
        assertNull(summary.endAtEpochMillis)
        assertNull(summary.minimumLevelPercent)
        assertNull(summary.maximumLevelPercent)
    }

    private fun snapshot(at: Long, level: Int?) = BatterySnapshot(
        capturedAtEpochMillis = at,
        levelPercent = level,
        status = BatteryStatus.DISCHARGING,
        pluggedSource = PluggedSource.NONE,
        temperatureCelsius = 30.0,
        voltageMillivolts = 4_000,
        chargeCounterMicroAh = null,
        currentNowMicroAmps = null,
        cycleCount = null,
        health = null
    )
}
