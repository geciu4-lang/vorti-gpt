package com.vortix.app.domain.model

/**
 * Usage and network activity observed for an installed application.
 * Foreground time is today's UsageStats value. Network counters come from a rolling
 * 24-hour Android NetworkStats query and are UID-level hour buckets; they are not
 * precise-to-the-second counters, nor battery or mAh readings.
 */
data class AppUsageItem(
    val packageName: String,
    val displayName: String,
    val foregroundTimeMillis: Long,
    val lastTimeUsedMillis: Long,
    val mobileBytes: Long? = null,
    val wifiBytes: Long? = null,
    /** More than one visible package shares this UID; network bytes are shared totals. */
    val sharedUidPackageCount: Int = 1
) {
    /** Null means neither network transport returned usable statistics. */
    val totalNetworkBytes: Long?
        get() {
            if (mobileBytes == null && wifiBytes == null) return null
            val mobile = mobileBytes?.coerceAtLeast(0L) ?: 0L
            val wifi = wifiBytes?.coerceAtLeast(0L) ?: 0L
            return if (Long.MAX_VALUE - mobile < wifi) Long.MAX_VALUE else mobile + wifi
        }

    val hasSharedUidTraffic: Boolean get() = sharedUidPackageCount > 1
}
