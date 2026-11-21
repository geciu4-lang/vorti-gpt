package com.vortix.app.data.repository

import com.vortix.app.data.local.dao.ChargingProfileDao
import com.vortix.app.data.local.entity.ChargingProfileEntity
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ChargingProfileRepositoryImplTest {
    @Test fun startsAndAccumulatesChargingProfile() = runBlocking {
        val dao = FakeChargingProfileDao()
        val repo = ChargingProfileRepositoryImpl(dao)
        repo.startProfile("Orijinal 65W", sample(1_000L, 4_000_000L, 350))
        repo.recordSnapshot(sample(2_000L, 6_000_000L, 370))
        val active = repo.observeActiveProfile().first()!!
        assertEquals("Orijinal 65W", active.name)
        assertEquals(2, active.sampleCount)
        assertEquals(25.0, active.averagePowerWatts!!, 0.001)
        assertEquals(30.0, active.peakPowerWatts!!, 0.001)
        repo.finishActiveProfile()
        assertNotNull(repo.observeProfiles().first().first().endedAtEpochMillis)
    }

    @Test fun refusesToStartWhenDeviceIsNotCharging() = runBlocking {
        val repo = ChargingProfileRepositoryImpl(FakeChargingProfileDao())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.startProfile("Test", sample(1L, 10_000_000L, 370).copy(status = BatteryStatus.DISCHARGING)) }
        }
    }

    private fun sample(at: Long, currentMicroAmps: Long, tempTenths: Int) = BatterySnapshot(
        capturedAtEpochMillis = at,
        levelPercent = 70,
        status = BatteryStatus.CHARGING,
        pluggedSource = PluggedSource.AC,
        temperatureCelsius = tempTenths / 10.0,
        voltageMillivolts = 5_000,
        chargeCounterMicroAh = null,
        currentNowMicroAmps = currentMicroAmps,
        cycleCount = null,
        health = null
    )

    private class FakeChargingProfileDao : ChargingProfileDao {
        private val data = mutableListOf<ChargingProfileEntity>()
        private val flow = MutableStateFlow<List<ChargingProfileEntity>>(emptyList())
        private fun publish() { flow.value = data.sortedByDescending { it.startedAtEpochMillis } }
        override suspend fun insert(profile: ChargingProfileEntity): Long {
            val id = (data.maxOfOrNull { it.id } ?: 0L) + 1L
            data += profile.copy(id = id)
            publish()
            return id
        }
        override suspend fun update(profile: ChargingProfileEntity) {
            val index = data.indexOfFirst { it.id == profile.id }
            if (index >= 0) data[index] = profile
            publish()
        }
        override fun observeRecent(): Flow<List<ChargingProfileEntity>> = flow
        override fun observeActive(): Flow<ChargingProfileEntity?> = MutableStateFlow(data.lastOrNull { it.endedAtEpochMillis == null })
        override suspend fun findActive(): ChargingProfileEntity? = data.lastOrNull { it.endedAtEpochMillis == null }
    }
}
