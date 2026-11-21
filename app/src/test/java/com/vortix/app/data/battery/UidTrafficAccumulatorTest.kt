package com.vortix.app.data.battery

import org.junit.Assert.assertEquals
import org.junit.Test

class UidTrafficAccumulatorTest {
    @Test
    fun aggregatesReceiveAndTransmitBytesAcrossBucketsForSameUid() {
        val accumulator = UidTrafficAccumulator()

        accumulator.add(uid = 10_001, rxBytes = 120L, txBytes = 30L)
        accumulator.add(uid = 10_001, rxBytes = 40L, txBytes = 10L)
        accumulator.add(uid = 10_002, rxBytes = 5L, txBytes = 7L)

        assertEquals(200L, accumulator.snapshot()[10_001])
        assertEquals(12L, accumulator.snapshot()[10_002])
    }

    @Test
    fun ignoresNegativeSystemUidsAndNegativeByteCounters() {
        val accumulator = UidTrafficAccumulator()

        accumulator.add(uid = -1, rxBytes = 100L, txBytes = 100L)
        accumulator.add(uid = 10_001, rxBytes = -50L, txBytes = 20L)

        assertEquals(mapOf(10_001 to 20L), accumulator.snapshot())
    }

    @Test
    fun saturatesInsteadOfOverflowingLongCounters() {
        val accumulator = UidTrafficAccumulator()

        accumulator.add(uid = 10_001, rxBytes = Long.MAX_VALUE, txBytes = 1L)
        accumulator.add(uid = 10_001, rxBytes = 1L, txBytes = 1L)

        assertEquals(Long.MAX_VALUE, accumulator.snapshot()[10_001])
    }
}
