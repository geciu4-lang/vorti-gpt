package com.vortix.app.domain.model

data class ChargingProfile(
    val id: Long,
    val name: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val sampleCount: Int,
    val averagePowerWatts: Double?,
    val peakPowerWatts: Double?,
    val startTemperatureCelsius: Double?,
    val endTemperatureCelsius: Double?,
    val temperatureDeltaCelsius: Double?,
    val efficiencyGrade: String?
)
