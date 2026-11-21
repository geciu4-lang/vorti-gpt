package com.vortix.app.domain.repository

import com.vortix.app.domain.model.WakelockAccessState
import com.vortix.app.domain.model.WakelockDiagnosticsResult

interface WakelockDiagnosticsRepository {
    fun currentAccessState(): WakelockAccessState
    fun requestPermission(): Boolean
    suspend fun readSinceLastCharge(): WakelockDiagnosticsResult
}
