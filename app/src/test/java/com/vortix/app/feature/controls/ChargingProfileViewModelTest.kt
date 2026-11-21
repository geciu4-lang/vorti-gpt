package com.vortix.app.feature.controls

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.ChargingProfile
import com.vortix.app.domain.model.EnergyChangeMarker
import com.vortix.app.domain.model.PluggedSource
import com.vortix.app.domain.repository.BatteryRepository
import com.vortix.app.domain.repository.ChargingProfileRepository
import com.vortix.app.domain.repository.EnergyMonitorScheduler
import com.vortix.app.domain.repository.EnergySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startingProfileWhileChargingStartsMonitoringAndSavesName() = runTest(dispatcher) {
        val repository = FakeProfiles()
        val scheduler = FakeScheduler()
        val settings = FakeSettings()
        val viewModel = ChargingProfileViewModel(FakeBatteryRepository(), repository, scheduler, settings)
        runCurrent()
        viewModel.startProfile("Orijinal 65W")
        runCurrent()
        assertEquals("Orijinal 65W", repository.startedName)
        assertTrue(scheduler.started)
        assertTrue(settings.monitoring.value)
    }

    @Test fun blankProfileNameIsRejected() = runTest(dispatcher) {
        val viewModel = ChargingProfileViewModel(
            FakeBatteryRepository(), FakeProfiles(), FakeScheduler(), FakeSettings()
        )
        runCurrent()
        viewModel.startProfile("  ")
        runCurrent()
        assertEquals("Adaptör profili için bir isim gir.", viewModel.uiState.value.message)
    }

    private class FakeProfiles : ChargingProfileRepository {
        var startedName: String? = null
        private val active = MutableStateFlow<ChargingProfile?>(null)
        override fun observeProfiles(): Flow<List<ChargingProfile>> = flowOf(emptyList())
        override fun observeActiveProfile(): Flow<ChargingProfile?> = active
        override suspend fun startProfile(name: String, snapshot: BatterySnapshot): Long {
            startedName = name
            active.value = ChargingProfile(1, name, 1L, null, 1, 5.0, 5.0, 35.0, 35.0, 0.0, null)
            return 1L
        }
        override suspend fun recordSnapshot(snapshot: BatterySnapshot) = Unit
        override suspend fun finishActiveProfile() { active.value = null }
    }

    private class FakeScheduler : EnergyMonitorScheduler {
        var started = false
        override fun start() { started = true }
        override fun stop() = Unit
    }

    private class FakeBatteryRepository : BatteryRepository {
        override fun observeLatestSnapshot(): Flow<BatterySnapshot?> = flowOf(chargingSnapshot())
        override suspend fun refreshSnapshot(): BatterySnapshot = chargingSnapshot()
        override suspend fun readCurrentSnapshot(): BatterySnapshot = chargingSnapshot()
        override fun observeHistory(sinceEpochMillis: Long): Flow<List<BatterySnapshot>> = flowOf(emptyList())
        override fun observeRecentSnapshots(limit: Int): Flow<List<BatterySnapshot>> = flowOf(emptyList())
        override suspend fun loadHistory(sinceEpochMillis: Long): List<BatterySnapshot> = emptyList()
        override suspend fun pruneHistory(olderThanEpochMillis: Long): Int = 0
        override suspend fun clearHistory(): Int = 0
    }

    private class FakeSettings : EnergySettingsRepository {
        val monitoring = MutableStateFlow(false)
        override val monitoringEnabled: Flow<Boolean> = monitoring
        override val chargeLimitEnabled: Flow<Boolean> = flowOf(false)
        override val chargeTargetPercent: Flow<Int> = flowOf(80)
        override val chargeTargetAlertSent: Flow<Boolean> = flowOf(false)
        override val lowBatteryAlertEnabled: Flow<Boolean> = flowOf(false)
        override val lowBatteryThresholdPercent: Flow<Int> = flowOf(20)
        override val lowBatteryAlertSent: Flow<Boolean> = flowOf(false)
        override val thermalAlertEnabled: Flow<Boolean> = flowOf(false)
        override val thermalThresholdCelsius: Flow<Int> = flowOf(42)
        override val thermalAlertSent: Flow<Boolean> = flowOf(false)
        override val ecoGuardEnabled: Flow<Boolean> = flowOf(false)
        override val designCapacityMah: Flow<Int> = flowOf(5000)
        override val energyChangeMarkers: Flow<List<EnergyChangeMarker>> = flowOf(emptyList())
        override val batteryBaselineAtEpochMillis: Flow<Long> = flowOf(0L)
        override suspend fun setMonitoringEnabled(enabled: Boolean) { monitoring.value = enabled }
        override suspend fun setChargeLimitEnabled(enabled: Boolean) = Unit
        override suspend fun setChargeTargetPercent(percent: Int) = Unit
        override suspend fun setChargeTargetAlertSent(sent: Boolean) = Unit
        override suspend fun setLowBatteryAlertEnabled(enabled: Boolean) = Unit
        override suspend fun setLowBatteryThresholdPercent(percent: Int) = Unit
        override suspend fun setLowBatteryAlertSent(sent: Boolean) = Unit
        override suspend fun setThermalAlertEnabled(enabled: Boolean) = Unit
        override suspend fun setThermalThresholdCelsius(celsius: Int) = Unit
        override suspend fun setThermalAlertSent(sent: Boolean) = Unit
        override suspend fun setEcoGuardEnabled(enabled: Boolean) = Unit
        override suspend fun setDesignCapacityMah(capacityMah: Int) = Unit
        override suspend fun addEnergyChangeMarker(marker: EnergyChangeMarker) = Unit
        override suspend fun setBatteryBaselineAtEpochMillis(epochMillis: Long) = Unit
    }

    companion object {
        private fun chargingSnapshot() = BatterySnapshot(
            capturedAtEpochMillis = 1L,
            levelPercent = 65,
            status = BatteryStatus.CHARGING,
            pluggedSource = PluggedSource.AC,
            temperatureCelsius = 35.0,
            voltageMillivolts = 5_000,
            chargeCounterMicroAh = null,
            currentNowMicroAmps = 2_000_000L,
            cycleCount = null,
            health = BatteryHealth.GOOD
        )
    }
}
