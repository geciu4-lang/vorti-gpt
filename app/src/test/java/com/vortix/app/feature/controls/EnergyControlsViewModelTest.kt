package com.vortix.app.feature.controls

import com.vortix.app.domain.model.AppUsageItem
import com.vortix.app.domain.model.DormantAppItem
import com.vortix.app.domain.model.EnergyChangeMarker
import com.vortix.app.domain.model.WakelockRecord
import com.vortix.app.domain.repository.EnergyMonitorScheduler
import com.vortix.app.domain.repository.EnergySettingsRepository
import com.vortix.app.domain.repository.UsageStatsRepository
import com.vortix.app.domain.repository.WakelockDiagnosticsRepository
import com.vortix.app.domain.model.WakelockAccessState
import com.vortix.app.domain.model.WakelockDiagnosticsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnergyControlsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun enablingMonitoringSchedulesWorkerAndPersistsSetting() = runTest(dispatcher) {
        val settings = FakeSettings()
        val scheduler = FakeScheduler()
        val viewModel = EnergyControlsViewModel(settings, scheduler, FakeUsageStats(), FakeWakelockDiagnostics())
        runCurrent()

        viewModel.setMonitoringEnabled(true)
        runCurrent()

        assertTrue(scheduler.started)
        assertTrue(settings.enabled.value)
        assertTrue(viewModel.uiState.value.isMonitoringEnabled)
    }

    @Test
    fun disablingMonitoringCancelsWorker() = runTest(dispatcher) {
        val settings = FakeSettings(initialEnabled = true)
        val scheduler = FakeScheduler()
        val viewModel = EnergyControlsViewModel(settings, scheduler, FakeUsageStats(), FakeWakelockDiagnostics())
        runCurrent()

        viewModel.setMonitoringEnabled(false)
        runCurrent()

        assertTrue(scheduler.stopped)
        assertFalse(settings.enabled.value)
    }

    @Test
    fun chargeTargetIsClampedToSupportedRange() = runTest(dispatcher) {
        val settings = FakeSettings()
        val viewModel = EnergyControlsViewModel(settings, FakeScheduler(), FakeUsageStats(), FakeWakelockDiagnostics())
        runCurrent()

        viewModel.setChargeTarget(120)
        runCurrent()

        assertEquals(100, settings.target.value)
    }


    @Test
    fun rapidDesignCapacitySelectionsKeepLatestValueAndConfirmationInSync() = runTest(dispatcher) {
        val settings = FakeSettings()
        val viewModel = EnergyControlsViewModel(
            settings, FakeScheduler(), FakeUsageStats(), FakeWakelockDiagnostics()
        )
        runCurrent()

        viewModel.setDesignCapacityMah(4_000)
        viewModel.setDesignCapacityMah(5_000)
        viewModel.setDesignCapacityMah(6_000)
        runCurrent()

        assertEquals(listOf(6_000), settings.designCapacityWrites)
        assertEquals(6_000, settings.designCapacity.value)
        assertEquals(6_000, viewModel.uiState.value.designCapacityMah)
        assertEquals("Referans kapasite 6000 mAh olarak kaydedildi.", viewModel.uiState.value.designCapacityMessage)
        assertEquals("Referans kapasite 6000 mAh olarak kaydedildi.", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSavingDesignCapacity)
        assertFalse(viewModel.uiState.value.isDesignCapacitySaveError)
    }

    @Test
    fun usageListIsLoadedOnlyWhenAccessIsGranted() = runTest(dispatcher) {
        val usage = FakeUsageStats(granted = true)
        val expected = listOf(AppUsageItem("com.example.app", "Example", 120_000L, 0L))
        usage.items = expected
        val viewModel = EnergyControlsViewModel(FakeSettings(), FakeScheduler(), usage, FakeWakelockDiagnostics())
        runCurrent()
        viewModel.refreshUsageApps()
        runCurrent()

        assertTrue(viewModel.uiState.value.hasUsageAccess)
        assertEquals(expected, viewModel.uiState.value.topApps)
    }


    @Test
    fun loadsWakelockRecordsOnlyWhenShizukuAccessIsReady() = runTest(dispatcher) {
        val expected = listOf(
            WakelockRecord(
                uid = 10_001,
                name = "GmsNetworkManager",
                partialTimeMillis = 12_000L,
                partialCount = 3L,
                actualPartialDurationMillis = 9_000L,
                packageNames = listOf("com.example.app")
            )
        )
        val diagnostics = FakeWakelockDiagnostics(
            access = WakelockAccessState.READY,
            result = WakelockDiagnosticsResult.Success(expected, 123L)
        )
        val viewModel = EnergyControlsViewModel(
            FakeSettings(), FakeScheduler(), FakeUsageStats(), diagnostics
        )
        runCurrent()

        viewModel.refreshWakelockDiagnostics()
        runCurrent()

        assertEquals(1, diagnostics.readCount)
        assertEquals(expected, viewModel.uiState.value.wakelockRecords)
        assertEquals(123L, viewModel.uiState.value.wakelockCapturedAtEpochMillis)
        assertFalse(viewModel.uiState.value.isLoadingWakelocks)
    }

    @Test
    fun doesNotReadWakelocksWhenShizukuIsUnavailable() = runTest(dispatcher) {
        val diagnostics = FakeWakelockDiagnostics(
            access = WakelockAccessState.SHIZUKU_NOT_RUNNING
        )
        val viewModel = EnergyControlsViewModel(
            FakeSettings(), FakeScheduler(), FakeUsageStats(), diagnostics
        )
        runCurrent()

        viewModel.refreshWakelockDiagnostics()
        runCurrent()

        assertEquals(0, diagnostics.readCount)
        assertEquals(WakelockAccessState.SHIZUKU_NOT_RUNNING, viewModel.uiState.value.wakelockAccessState)
        assertFalse(viewModel.uiState.value.isLoadingWakelocks)
    }

    private class FakeSettings(initialEnabled: Boolean = false) : EnergySettingsRepository {
        val enabled = MutableStateFlow(initialEnabled)
        val target = MutableStateFlow(80)
        val alertSent = MutableStateFlow(false)
        override val monitoringEnabled: Flow<Boolean> = enabled
        override val chargeLimitEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val chargeTargetPercent: Flow<Int> = target
        override val chargeTargetAlertSent: Flow<Boolean> = alertSent
        override val lowBatteryAlertEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val lowBatteryThresholdPercent: Flow<Int> = MutableStateFlow(20)
        override val lowBatteryAlertSent: Flow<Boolean> = MutableStateFlow(false)
        override val thermalAlertEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val thermalThresholdCelsius: Flow<Int> = MutableStateFlow(42)
        override val thermalAlertSent: Flow<Boolean> = MutableStateFlow(false)
        override val ecoGuardEnabled: Flow<Boolean> = MutableStateFlow(false)
        val designCapacity = MutableStateFlow(0)
        val designCapacityWrites = mutableListOf<Int>()
        override val designCapacityMah: Flow<Int> = designCapacity
        override val energyChangeMarkers: Flow<List<EnergyChangeMarker>> = MutableStateFlow(emptyList())
        override val batteryBaselineAtEpochMillis: Flow<Long> = MutableStateFlow(0L)
        override suspend fun setMonitoringEnabled(enabled: Boolean) { this.enabled.value = enabled }
        override suspend fun setChargeLimitEnabled(enabled: Boolean) = Unit
        override suspend fun setChargeTargetPercent(percent: Int) { target.value = percent.coerceIn(70, 100) }
        override suspend fun setChargeTargetAlertSent(sent: Boolean) { alertSent.value = sent }
        override suspend fun setLowBatteryAlertEnabled(enabled: Boolean) = Unit
        override suspend fun setLowBatteryThresholdPercent(percent: Int) = Unit
        override suspend fun setLowBatteryAlertSent(sent: Boolean) = Unit
        override suspend fun setThermalAlertEnabled(enabled: Boolean) = Unit
        override suspend fun setThermalThresholdCelsius(celsius: Int) = Unit
        override suspend fun setThermalAlertSent(sent: Boolean) = Unit
        override suspend fun setEcoGuardEnabled(enabled: Boolean) = Unit
        override suspend fun setDesignCapacityMah(capacityMah: Int) {
            designCapacityWrites += capacityMah
            designCapacity.value = capacityMah
        }
        override suspend fun addEnergyChangeMarker(marker: EnergyChangeMarker) = Unit
        override suspend fun setBatteryBaselineAtEpochMillis(epochMillis: Long) = Unit
    }

    private class FakeScheduler : EnergyMonitorScheduler {
        var started = false
        var stopped = false
        override fun start() { started = true; stopped = false }
        override fun stop() { stopped = true; started = false }
    }

    private class FakeWakelockDiagnostics(
        private val access: WakelockAccessState = WakelockAccessState.SHIZUKU_NOT_RUNNING,
        private val result: WakelockDiagnosticsResult = WakelockDiagnosticsResult.Success(emptyList(), 1L)
    ) : WakelockDiagnosticsRepository {
        var readCount: Int = 0
            private set

        override fun currentAccessState(): WakelockAccessState = access
        override fun requestPermission(): Boolean = false
        override suspend fun readSinceLastCharge(): WakelockDiagnosticsResult {
            readCount++
            return result
        }
    }

    private class FakeUsageStats(
        private val granted: Boolean = false
    ) : UsageStatsRepository {
        var items: List<AppUsageItem> = emptyList()
        override fun hasUsageAccess(): Boolean = granted
        override suspend fun loadTodayUsage(): List<AppUsageItem> = items
        override suspend fun loadDormantApps(minimumUnusedDays: Int): List<DormantAppItem> = emptyList()
    }
}
