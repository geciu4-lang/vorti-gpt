package com.vortix.app.feature.controls

import com.vortix.app.domain.model.AppUsageItem
import com.vortix.app.domain.model.WakelockAccessState
import com.vortix.app.domain.model.WakelockRecord

data class EnergyControlsUiState(
    val isMonitoringEnabled: Boolean = false,
    val isChargeLimitEnabled: Boolean = false,
    val chargeTargetPercent: Int = 80,
    val isLowBatteryAlertEnabled: Boolean = false,
    val lowBatteryThresholdPercent: Int = 20,
    val isThermalAlertEnabled: Boolean = false,
    val thermalThresholdCelsius: Int = 42,
    val isEcoGuardEnabled: Boolean = false,
    val designCapacityMah: Int = 5000,
    val designCapacityMessage: String? = null,
    // Monotonic event ID lets the UI replay banner feedback for every capacity selection.
    val designCapacityFeedbackId: Long = 0L,
    val isSavingDesignCapacity: Boolean = false,
    val isDesignCapacitySaveError: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val isLoadingUsage: Boolean = false,
    val topApps: List<AppUsageItem> = emptyList(),
    val wakelockAccessState: WakelockAccessState = WakelockAccessState.SHIZUKU_NOT_RUNNING,
    val isLoadingWakelocks: Boolean = false,
    val wakelockRecords: List<WakelockRecord> = emptyList(),
    val wakelockErrorMessage: String? = null,
    val wakelockCapturedAtEpochMillis: Long? = null,
    val message: String? = null
)
