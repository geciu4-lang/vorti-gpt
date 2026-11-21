package com.vortix.app.domain.model

/** Availability of the optional Shizuku/Sui diagnostics bridge. */
enum class WakelockAccessState {
    SHIZUKU_NOT_RUNNING,
    UNSUPPORTED_VERSION,
    PERMISSION_REQUIRED,
    READY
}

/**
 * A single Android Batterystats wake-lock entry.
 *
 * [partialTimeMillis] is Android's apportioned partial-wake-lock time. On newer
 * check-in formats [actualPartialDurationMillis] is the non-apportioned total
 * duration. Neither value is a battery-energy or mAh measurement.
 */
data class WakelockRecord(
    val uid: Int,
    val name: String,
    val partialTimeMillis: Long,
    /** Null for aggregate `awl` rows, which do not contain an acquisition count. */
    val partialCount: Long? = null,
    val actualPartialDurationMillis: Long? = null,
    val backgroundPartialDurationMillis: Long? = null,
    val packageNames: List<String> = emptyList()
) {
    val preferredDurationMillis: Long
        get() = (actualPartialDurationMillis ?: partialTimeMillis).coerceAtLeast(0L)
}

sealed interface WakelockDiagnosticsResult {
    data class Success(
        val records: List<WakelockRecord>,
        val capturedAtEpochMillis: Long
    ) : WakelockDiagnosticsResult

    data class Unavailable(val accessState: WakelockAccessState) : WakelockDiagnosticsResult

    data class Failure(val message: String) : WakelockDiagnosticsResult
}
