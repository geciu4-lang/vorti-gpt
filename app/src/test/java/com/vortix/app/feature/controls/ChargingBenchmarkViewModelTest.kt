package com.vortix.app.feature.controls

import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import com.vortix.app.domain.repository.BatteryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingBenchmarkViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun refusesToStartWhenDeviceIsNotCharging() = runTest(dispatcher) {
        val vm = ChargingBenchmarkViewModel(FakeBatteryRepository(charging = false))
        runCurrent()
        vm.startTest()
        runCurrent()

        assertFalse(vm.uiState.value.isRunning)
        assertNotNull(vm.uiState.value.message)
    }

    @Test fun completesThirtySecondSamplingWindowAndProducesResult() = runTest(dispatcher) {
        val vm = ChargingBenchmarkViewModel(FakeBatteryRepository(charging = true))
        runCurrent()
        assertTrue(vm.uiState.value.isDeviceCharging)

        vm.startTest()
        runCurrent()
        assertTrue(vm.uiState.value.isRunning)
        advanceTimeBy(30_000L)
        runCurrent()

        assertFalse(vm.uiState.value.isRunning)
        assertNotNull(vm.uiState.value.result)
    }

    private class FakeBatteryRepository(private val charging: Boolean) : BatteryRepository {
        private var sequence = 0L
        private fun snapshot() = BatterySnapshot(
            capturedAtEpochMillis = sequence++ * 1_000L,
            levelPercent = 60,
            status = if (charging) BatteryStatus.CHARGING else BatteryStatus.DISCHARGING,
            pluggedSource = if (charging) PluggedSource.AC else PluggedSource.NONE,
            temperatureCelsius = 30.0 + sequence * 0.01,
            voltageMillivolts = 4_100,
            chargeCounterMicroAh = 2_400_000L,
            currentNowMicroAmps = 3_000_000L,
            cycleCount = null,
            health = BatteryHealth.GOOD
        )
        override fun observeLatestSnapshot(): Flow<BatterySnapshot?> = flowOf(null)
        override suspend fun refreshSnapshot(): BatterySnapshot = snapshot()
        override suspend fun readCurrentSnapshot(): BatterySnapshot = snapshot()
        override fun observeHistory(sinceEpochMillis: Long): Flow<List<BatterySnapshot>> = flowOf(emptyList())
        override fun observeRecentSnapshots(limit: Int): Flow<List<BatterySnapshot>> = flowOf(emptyList())
        override suspend fun loadHistory(sinceEpochMillis: Long): List<BatterySnapshot> = emptyList()
        override suspend fun pruneHistory(olderThanEpochMillis: Long): Int = 0
        override suspend fun clearHistory(): Int = 0
    }
}
