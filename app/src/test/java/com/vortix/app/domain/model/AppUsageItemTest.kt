package com.vortix.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUsageItemTest {
    @Test
    fun totalNetworkBytesCombinesAvailableMobileAndWifiTraffic() {
        val item = AppUsageItem(
            packageName = "com.example.app",
            displayName = "Example",
            foregroundTimeMillis = 60_000L,
            lastTimeUsedMillis = 1L,
            mobileBytes = 120L,
            wifiBytes = 80L
        )

        assertEquals(200L, item.totalNetworkBytes)
    }

    @Test
    fun totalNetworkBytesRemainsUnknownWhenBothSourcesAreUnavailable() {
        val item = AppUsageItem(
            packageName = "com.example.app",
            displayName = "Example",
            foregroundTimeMillis = 0L,
            lastTimeUsedMillis = 0L
        )

        assertNull(item.totalNetworkBytes)
    }

    @Test
    fun totalNetworkBytesSaturatesOnOverflow() {
        val item = AppUsageItem(
            packageName = "com.example.app",
            displayName = "Example",
            foregroundTimeMillis = 0L,
            lastTimeUsedMillis = 0L,
            mobileBytes = Long.MAX_VALUE,
            wifiBytes = 1L
        )

        assertEquals(Long.MAX_VALUE, item.totalNetworkBytes)
    }
    @Test
    fun successfulZeroByteTransportIsDifferentFromUnavailableTraffic() {
        val item = AppUsageItem(
            packageName = "com.example.app",
            displayName = "Example",
            foregroundTimeMillis = 0L,
            lastTimeUsedMillis = 0L,
            mobileBytes = null,
            wifiBytes = 0L
        )

        assertEquals(0L, item.totalNetworkBytes)
    }

    @Test
    fun sharedUidTrafficIsExplicitlyMarked() {
        val item = AppUsageItem(
            packageName = "com.example.app",
            displayName = "Example",
            foregroundTimeMillis = 0L,
            lastTimeUsedMillis = 0L,
            mobileBytes = 100L,
            wifiBytes = 200L,
            sharedUidPackageCount = 2
        )

        assertEquals(true, item.hasSharedUidTraffic)
    }

}
