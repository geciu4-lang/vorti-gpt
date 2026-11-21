package com.vortix.app.feature.controls

import com.vortix.app.domain.analytics.ChargingBenchmarkAnalyzer

 data class ChargingBenchmarkUiState(
    val isDeviceCharging: Boolean = false,
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 30,
    val result: ChargingBenchmarkAnalyzer.Result? = null,
    val message: String? = null
)
