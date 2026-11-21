package com.vortix.app.data.repository

import com.vortix.app.data.local.dao.ChargingProfileDao
import com.vortix.app.data.local.entity.ChargingProfileEntity
import com.vortix.app.domain.analytics.ThermalEfficiencyScorer
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.ChargingProfile
import com.vortix.app.domain.model.PluggedSource
import com.vortix.app.domain.repository.ChargingProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ChargingProfileRepositoryImpl @Inject constructor(
    private val dao: ChargingProfileDao
) : ChargingProfileRepository {
    private val mutex = Mutex()

    override fun observeProfiles(): Flow<List<ChargingProfile>> = dao.observeRecent().map { items -> items.map(::toDomain) }
    override fun observeActiveProfile(): Flow<ChargingProfile?> = dao.observeActive().map { it?.let(::toDomain) }

    override suspend fun startProfile(name: String, snapshot: BatterySnapshot): Long = mutex.withLock {
        val cleanName = name.trim().take(48)
        require(cleanName.isNotBlank()) { "Adaptör profili için bir isim gir." }
        require(snapshot.isActivelyCharging()) { "Adaptör profili yalnızca cihaz şarj olurken başlatılabilir." }
        require(dao.findActive() == null) { "Önce açık adaptör profilini bitir." }
        val power = snapshot.powerWattsOrNull()
        dao.insert(
            ChargingProfileEntity(
                name = cleanName,
                startedAtEpochMillis = snapshot.capturedAtEpochMillis,
                sampleCount = if (power != null) 1 else 0,
                totalPowerWatts = power ?: 0.0,
                averagePowerWatts = power,
                peakPowerWatts = power ?: 0.0,
                startTemperatureCelsius = snapshot.temperatureCelsius,
                endTemperatureCelsius = snapshot.temperatureCelsius
            )
        )
    }

    override suspend fun recordSnapshot(snapshot: BatterySnapshot) = mutex.withLock {
        val active = dao.findActive() ?: return@withLock
        if (!snapshot.isActivelyCharging()) {
            finish(active, snapshot.capturedAtEpochMillis, snapshot.temperatureCelsius ?: active.endTemperatureCelsius)
            return@withLock
        }
        val power = snapshot.powerWattsOrNull()
        val count = active.sampleCount + if (power != null) 1 else 0
        val total = active.totalPowerWatts + (power ?: 0.0)
        val average = if (count > 0) total / count else null
        val endTemp = snapshot.temperatureCelsius ?: active.endTemperatureCelsius
        val delta = temperatureDelta(active.startTemperatureCelsius, endTemp)
        val updated = active.copy(
            sampleCount = count,
            totalPowerWatts = total,
            averagePowerWatts = average,
            peakPowerWatts = maxOf(active.peakPowerWatts, power ?: 0.0),
            endTemperatureCelsius = endTemp,
            temperatureDeltaCelsius = delta,
            efficiencyGrade = ThermalEfficiencyScorer.grade(average, delta)
        )
        dao.update(updated)
    }

    override suspend fun finishActiveProfile() = mutex.withLock {
        val active = dao.findActive() ?: return@withLock
        finish(active, System.currentTimeMillis(), active.endTemperatureCelsius)
    }

    private suspend fun finish(active: ChargingProfileEntity, endedAt: Long, endTemperature: Double?) {
        val delta = temperatureDelta(active.startTemperatureCelsius, endTemperature)
        dao.update(
            active.copy(
                endedAtEpochMillis = endedAt.coerceAtLeast(active.startedAtEpochMillis),
                endTemperatureCelsius = endTemperature,
                temperatureDeltaCelsius = delta,
                efficiencyGrade = ThermalEfficiencyScorer.grade(active.averagePowerWatts, delta)
            )
        )
    }

    private fun temperatureDelta(start: Double?, end: Double?): Double? =
        if (start != null && end != null) end - start else null

    private fun BatterySnapshot.isActivelyCharging(): Boolean =
        status == BatteryStatus.CHARGING && pluggedSource != PluggedSource.NONE && pluggedSource != PluggedSource.UNKNOWN

    private fun BatterySnapshot.powerWattsOrNull(): Double? = batteryPowerMilliWatts
        ?.div(1000.0)
        ?.takeIf { it.isFinite() && it > 0.0 }

    private fun toDomain(entity: ChargingProfileEntity) = ChargingProfile(
        id = entity.id,
        name = entity.name,
        startedAtEpochMillis = entity.startedAtEpochMillis,
        endedAtEpochMillis = entity.endedAtEpochMillis,
        sampleCount = entity.sampleCount,
        averagePowerWatts = entity.averagePowerWatts,
        peakPowerWatts = entity.peakPowerWatts.takeIf { it > 0.0 },
        startTemperatureCelsius = entity.startTemperatureCelsius,
        endTemperatureCelsius = entity.endTemperatureCelsius,
        temperatureDeltaCelsius = entity.temperatureDeltaCelsius,
        efficiencyGrade = entity.efficiencyGrade
    )
}
