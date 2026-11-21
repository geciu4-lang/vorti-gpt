package com.vortix.app.feature.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortix.app.domain.analytics.ChargingBenchmarkAnalyzer
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import com.vortix.app.domain.repository.BatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChargingBenchmarkViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChargingBenchmarkUiState())
    val uiState = _uiState.asStateFlow()
    private var benchmarkJob: Job? = null

    init { refreshChargeState() }

    fun refreshChargeState() {
        viewModelScope.launch {
            runCatching { batteryRepository.readCurrentSnapshot() }
                .onSuccess { snapshot ->
                    _uiState.update { it.copy(isDeviceCharging = snapshot.isActivelyCharging()) }
                }
        }
    }

    fun startTest() {
        if (benchmarkJob?.isActive == true || _uiState.value.isRunning) return
        benchmarkJob = viewModelScope.launch {
            _uiState.update { it.copy(result = null, message = null, remainingSeconds = TEST_DURATION_SECONDS) }
            try {
                val first = batteryRepository.readCurrentSnapshot()
                if (!first.isActivelyCharging()) {
                    _uiState.update {
                        it.copy(isDeviceCharging = false, isRunning = false,
                            message = "Adaptör testi yalnızca cihaz aktif olarak şarj olurken başlatılabilir.")
                    }
                    return@launch
                }

                val samples = mutableListOf(first)
                _uiState.update { it.copy(isDeviceCharging = true, isRunning = true) }
                repeat(TEST_DURATION_SECONDS) { index ->
                    delay(1_000L)
                    val sample = batteryRepository.readCurrentSnapshot()
                    if (!sample.isActivelyCharging()) {
                        _uiState.update {
                            it.copy(isDeviceCharging = false, isRunning = false,
                                message = "Şarj bağlantısı test sırasında kesildi. Sonuç oluşturulmadı.")
                        }
                        return@launch
                    }
                    samples += sample
                    _uiState.update { it.copy(remainingSeconds = (TEST_DURATION_SECONDS - index - 1).coerceAtLeast(0)) }
                }

                val result = ChargingBenchmarkAnalyzer.analyze(samples)
                _uiState.update {
                    it.copy(
                        isDeviceCharging = true,
                        isRunning = false,
                        remainingSeconds = 0,
                        result = result,
                        message = if (result == null) "Yeterli geçerli akım ve voltaj örneği alınamadı. Tekrar deneyin." else null
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(isRunning = false, message = "Şarj testi durduruldu.") }
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isRunning = false, message = "Şarj testi tamamlanamadı. Sensör verilerini kontrol edip tekrar deneyin.") }
            }
        }
    }

    fun cancelTest() {
        benchmarkJob?.cancel()
        benchmarkJob = null
        _uiState.update { it.copy(isRunning = false, message = "Şarj testi iptal edildi.") }
    }

    private fun BatterySnapshot.isActivelyCharging(): Boolean =
        status == BatteryStatus.CHARGING &&
            pluggedSource != PluggedSource.NONE &&
            pluggedSource != PluggedSource.UNKNOWN

    companion object { const val TEST_DURATION_SECONDS = 30 }
}
