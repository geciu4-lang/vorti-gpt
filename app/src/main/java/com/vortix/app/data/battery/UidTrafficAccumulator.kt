package com.vortix.app.data.battery

/**
 * Aggregates NetworkStats buckets by Android UID without allowing negative counters
 * or Long overflow to corrupt the result.
 */
internal class UidTrafficAccumulator {
    private val totalsByUid = LinkedHashMap<Int, Long>()

    fun add(uid: Int, rxBytes: Long, txBytes: Long) {
        // Negative UIDs represent system-wide or synthetic buckets, not an app UID.
        if (uid < 0) return

        val received = rxBytes.coerceAtLeast(0L)
        val transmitted = txBytes.coerceAtLeast(0L)
        val bucketBytes = saturatingAdd(received, transmitted)
        totalsByUid[uid] = saturatingAdd(totalsByUid[uid] ?: 0L, bucketBytes)
    }

    fun snapshot(): Map<Int, Long> = totalsByUid.toMap()

    private fun saturatingAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
}
