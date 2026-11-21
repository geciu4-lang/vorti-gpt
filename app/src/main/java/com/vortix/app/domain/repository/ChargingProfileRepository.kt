package com.vortix.app.domain.repository

import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.ChargingProfile
import kotlinx.coroutines.flow.Flow

interface ChargingProfileRepository {
    fun observeProfiles(): Flow<List<ChargingProfile>>
    fun observeActiveProfile(): Flow<ChargingProfile?>
    suspend fun startProfile(name: String, snapshot: BatterySnapshot): Long
    suspend fun recordSnapshot(snapshot: BatterySnapshot)
    suspend fun finishActiveProfile()
}
