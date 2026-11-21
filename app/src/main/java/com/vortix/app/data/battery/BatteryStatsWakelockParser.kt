package com.vortix.app.data.battery

import com.vortix.app.domain.model.WakelockRecord

/** Parses the versioned wl/awl rows returned by `dumpsys batterystats --checkin`. */
object BatteryStatsWakelockParser {
    private const val MODE_SINCE_CHARGED = "l"
    private const val MAX_RECORDS = 250

    /** Identifies the record type using the first four unquoted columns. */
    fun isWakelockCheckinRow(line: String): Boolean {
        val recordType = line.split(',', limit = 5).getOrNull(3)
        return recordType == "wl" || recordType == "awl"
    }

    fun parse(checkinRows: String): List<WakelockRecord> {
        val detailed = linkedMapOf<Pair<Int, String>, MutableWakelock>()
        val aggregate = linkedMapOf<Int, Pair<Long, Long>>()

        checkinRows.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val fields = splitCsv(line)
            if (fields.size < 5 || fields[2] != MODE_SINCE_CHARGED) return@forEach

            val uid = fields[1].toIntOrNull() ?: return@forEach
            when (fields[3]) {
                "wl" -> parseWakeLock(fields)?.let { row ->
                    val key = uid to row.name
                    val current = detailed.getOrPut(key) { MutableWakelock(uid, row.name) }
                    current.add(row)
                }
                "awl" -> {
                    if (fields.size >= 6) {
                        val partial = fields[4].toLongOrNull()?.coerceAtLeast(0L) ?: return@forEach
                        val background = fields[5].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        aggregate[uid] = aggregate[uid]?.let { old ->
                            safeAdd(old.first, partial) to safeAdd(old.second, background)
                        } ?: (partial to background)
                    }
                }
            }
        }

        val detailedRecords = detailed.values.mapNotNull(MutableWakelock::toRecord)
        val detailedUids = detailedRecords.mapTo(mutableSetOf()) { it.uid }
        val aggregateFallbacks = aggregate.mapNotNull { (uid, totals) ->
            if (uid in detailedUids || (totals.first <= 0L && totals.second <= 0L)) null
            else WakelockRecord(
                uid = uid,
                name = "Partial wakelock toplamı",
                partialTimeMillis = totals.first,
                partialCount = null,
                actualPartialDurationMillis = totals.first.takeIf { it > 0L },
                backgroundPartialDurationMillis = totals.second.takeIf { it > 0L }
            )
        }

        return (detailedRecords + aggregateFallbacks)
            .sortedWith(
                compareByDescending<WakelockRecord> { it.preferredDurationMillis }
                    .thenByDescending { it.partialCount }
            )
            .take(MAX_RECORDS)
    }

    private fun parseWakeLock(fields: List<String>): ParsedWakeLock? {
        val name = fields.getOrNull(4)?.takeIf(String::isNotBlank) ?: return null

        // Check-in v18: ... name, fullTime, f, fullCount, partialTime, p, partialCount, ...
        // v19-v20 add current/max durations; v21 adds actual total partial duration.
        // v22+ adds the background-partial block before window wakelock fields.
        val partialTimeIndex: Int
        val partialCountIndex: Int
        val actualPartialIndex: Int?
        val backgroundPartialIndex: Int?
        when {
            fields.getOrNull(12) == "p" && fields.size >= 29 && fields.getOrNull(18) == "bp" -> {
                // Check-in v22+: actual partial duration plus background partial counters.
                partialTimeIndex = 11
                partialCountIndex = 13
                actualPartialIndex = 16
                backgroundPartialIndex = 22
            }
            fields.getOrNull(12) == "p" && fields.size >= 23 -> {
                // Check-in v21: actual partial duration, but no background partial block yet.
                partialTimeIndex = 11
                partialCountIndex = 13
                actualPartialIndex = 16
                backgroundPartialIndex = null
            }
            fields.getOrNull(11) == "p" && fields.size >= 20 -> {
                // Check-in v19-v20: current/max durations, without actual total duration.
                partialTimeIndex = 10
                partialCountIndex = 12
                actualPartialIndex = null
                backgroundPartialIndex = null
            }
            fields.getOrNull(9) == "p" && fields.size >= 14 -> {
                // Check-in v18: legacy, compact timer fields.
                partialTimeIndex = 8
                partialCountIndex = 10
                actualPartialIndex = null
                backgroundPartialIndex = null
            }
            else -> return null
        }

        val partialTime = fields.getOrNull(partialTimeIndex)?.toLongOrNull()?.coerceAtLeast(0L)
            ?: return null
        val partialCount = fields.getOrNull(partialCountIndex)?.toLongOrNull()?.coerceAtLeast(0L)
            ?: 0L
        val actualPartial = actualPartialIndex?.let { index ->
            fields.getOrNull(index)?.toLongOrNull()?.takeIf { it >= 0L }
        }
        val backgroundPartial = backgroundPartialIndex?.let { index ->
            fields.getOrNull(index)?.toLongOrNull()?.takeIf { it >= 0L }
        }

        if (partialTime <= 0L && partialCount <= 0L && (actualPartial ?: 0L) <= 0L) return null
        return ParsedWakeLock(name, partialTime, partialCount, actualPartial, backgroundPartial)
    }

    /** Small RFC-4180-compatible reader for quoted check-in tags containing commas. */
    private fun splitCsv(line: String): List<String> {
        val fields = ArrayList<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    value.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    fields += value.toString()
                    value.setLength(0)
                }
                else -> value.append(char)
            }
            index++
        }
        fields += value.toString()
        return fields
    }

    private data class ParsedWakeLock(
        val name: String,
        val partialTimeMillis: Long,
        val partialCount: Long,
        val actualPartialDurationMillis: Long?,
        val backgroundPartialDurationMillis: Long?
    )

    private class MutableWakelock(val uid: Int, val name: String) {
        private var partialTime = 0L
        private var partialCount = 0L
        private var actualDuration = 0L
        private var backgroundDuration = 0L
        private var allRowsHaveActualDuration = true
        private var hasActualDuration = false
        private var hasBackgroundDuration = false

        fun add(row: ParsedWakeLock) {
            partialTime = safeAdd(partialTime, row.partialTimeMillis)
            partialCount = safeAdd(partialCount, row.partialCount)
            if (row.actualPartialDurationMillis == null) {
                allRowsHaveActualDuration = false
            } else {
                hasActualDuration = true
                actualDuration = safeAdd(actualDuration, row.actualPartialDurationMillis)
            }
            row.backgroundPartialDurationMillis?.let { duration ->
                hasBackgroundDuration = true
                backgroundDuration = safeAdd(backgroundDuration, duration)
            }
        }

        fun toRecord(): WakelockRecord? {
            if (partialTime <= 0L && partialCount <= 0L && actualDuration <= 0L) return null
            return WakelockRecord(
                uid = uid,
                name = name,
                partialTimeMillis = partialTime,
                partialCount = partialCount,
                actualPartialDurationMillis = actualDuration.takeIf { hasActualDuration && allRowsHaveActualDuration },
                backgroundPartialDurationMillis = backgroundDuration.takeIf { hasBackgroundDuration }
            )
        }
    }

    private fun safeAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
}
