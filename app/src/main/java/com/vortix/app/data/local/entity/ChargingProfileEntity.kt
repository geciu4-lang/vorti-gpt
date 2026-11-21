package com.vortix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_profiles")
data class ChargingProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val sampleCount: Int = 0,
    val totalPowerWatts: Double = 0.0,
    val averagePowerWatts: Double? = null,
    val peakPowerWatts: Double = 0.0,
    val startTemperatureCelsius: Double? = null,
    val endTemperatureCelsius: Double? = null,
    val temperatureDeltaCelsius: Double? = null,
    val efficiencyGrade: String? = null
)
