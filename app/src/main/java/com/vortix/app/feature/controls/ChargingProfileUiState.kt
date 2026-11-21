package com.vortix.app.feature.controls

import com.vortix.app.domain.model.ChargingProfile

data class ChargingProfileUiState(
    val isCharging: Boolean = false,
    val isSaving: Boolean = false,
    val activeProfile: ChargingProfile? = null,
    val profiles: List<ChargingProfile> = emptyList(),
    val message: String? = null
)
