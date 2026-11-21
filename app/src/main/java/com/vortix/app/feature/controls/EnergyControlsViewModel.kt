package com.vortix.app.feature.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortix.app.domain.repository.EnergyMonitorScheduler
import com.vortix.app.domain.repository.EnergySettingsRepository
import com.vortix.app.domain.repository.UsageStatsRepository
import com.vortix.app.domain.repository.WakelockDiagnosticsRepository
import com.vortix.app.domain.model.WakelockAccessState
import com.vortix.app.domain.model.WakelockDiagnosticsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class EnergyControlsViewModel @Inject constructor(
    private val settings: EnergySettingsRepository,
    private val monitorScheduler: EnergyMonitorScheduler,
    private val usageStats: UsageStatsRepository,
    private val wakelockDiagnostics: WakelockDiagnosticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnergyControlsUiState())
    val uiState = _uiState.asStateFlow()

    // Capacity chips can be tapped rapidly. Keep the newest selection visible immediately,
    // serialize persistence, and skip stale writes that have not started yet.
    private val designCapacityWriteMutex = Mutex()
    private var designCapacityRequestSequence: Long = 0L
    private var pendingDesignCapacityRequestId: Long? = null
    private var pendingDesignCapacityMah: Int? = null
    private var persistedDesignCapacityMah: Int = 0

    init {
        viewModelScope.launch {
            val part1 = combine(
                settings.monitoringEnabled,
                settings.chargeLimitEnabled,
                settings.chargeTargetPercent,
                settings.lowBatteryAlertEnabled,
                settings.lowBatteryThresholdPercent
            ) { monitoring, chargeLimit, chargeTarget, lowBatAlert, lowBatThreshold ->
                Part1(monitoring, chargeLimit, chargeTarget, lowBatAlert, lowBatThreshold)
            }
            val part2 = combine(
                settings.thermalAlertEnabled,
                settings.thermalThresholdCelsius,
                settings.ecoGuardEnabled,
                settings.designCapacityMah
            ) { thermalAlert, thermalThreshold, ecoGuard, designCap ->
                Part2(thermalAlert, thermalThreshold, ecoGuard, designCap)
            }
            combine(part1, part2) { p1, p2 -> p1 to p2 }.collect { (p1, p2) ->
                persistedDesignCapacityMah = p2.designCap
                _uiState.update {
                    it.copy(
                        isMonitoringEnabled = p1.monitoring,
                        isChargeLimitEnabled = p1.chargeLimit,
                        chargeTargetPercent = p1.chargeTarget,
                        isLowBatteryAlertEnabled = p1.lowBatAlert,
                        lowBatteryThresholdPercent = p1.lowBatThreshold,
                        isThermalAlertEnabled = p2.thermalAlert,
                        thermalThresholdCelsius = p2.thermalThreshold,
                        isEcoGuardEnabled = p2.ecoGuard,
                        designCapacityMah = pendingDesignCapacityMah ?: p2.designCap
                    )
                }
            }
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) monitorScheduler.start() else monitorScheduler.stop()
                settings.setMonitoringEnabled(enabled)
                _uiState.update {
                    it.copy(message = if (enabled) "Arka plan ölçümü ve bildirim kontrolleri başlatıldı." else "Arka plan ölçümü durduruldu.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Ölçüm planı güncellenemedi.") }
            }
        }
    }

    fun setChargeLimitEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settings.setChargeLimitEnabled(enabled)
                if (!enabled) settings.setChargeTargetAlertSent(false)
                if (enabled && !_uiState.value.isMonitoringEnabled) {
                    monitorScheduler.start()
                    settings.setMonitoringEnabled(true)
                }
                _uiState.update {
                    it.copy(
                        message = if (enabled) "Şarj hedef bildirimi etkinleştirildi."
                        else "Şarj hedef bildirimi kapatıldı."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Şarj hedef bildirimi ayarı kaydedilemedi.") }
            }
        }
    }

    fun setChargeTarget(percent: Int) {
        val safeTarget = percent.coerceIn(70, 100)
        viewModelScope.launch {
            try {
                settings.setChargeTargetPercent(safeTarget)
                settings.setChargeTargetAlertSent(false)
                _uiState.update {
                    it.copy(message = "Şarj hedefi %$safeTarget olarak güncellendi.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Şarj hedefi kaydedilemedi.") }
            }
        }
    }

    fun setLowBatteryAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settings.setLowBatteryAlertEnabled(enabled)
                if (!enabled) settings.setLowBatteryAlertSent(false)
                if (enabled && !_uiState.value.isMonitoringEnabled) {
                    monitorScheduler.start()
                    settings.setMonitoringEnabled(true)
                }
                _uiState.update {
                    it.copy(
                        message = if (enabled) "Düşük pil uyarısı açıldı."
                        else "Düşük pil uyarısı kapatıldı."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Düşük pil ayarı kaydedilemedi.") }
            }
        }
    }

    fun setLowBatteryThreshold(percent: Int) {
        val safePercent = percent.coerceIn(10, 40)
        viewModelScope.launch {
            try {
                settings.setLowBatteryThresholdPercent(safePercent)
                settings.setLowBatteryAlertSent(false)
                _uiState.update {
                    it.copy(message = "Düşük pil uyarı eşiği %$safePercent olarak ayarlandı.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Düşük pil eşiği kaydedilemedi.") }
            }
        }
    }

    fun setThermalAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settings.setThermalAlertEnabled(enabled)
                if (!enabled) settings.setThermalAlertSent(false)
                if (enabled && !_uiState.value.isMonitoringEnabled) {
                    monitorScheduler.start()
                    settings.setMonitoringEnabled(true)
                }
                _uiState.update {
                    it.copy(
                        message = if (enabled) "Sıcaklık uyarısı açıldı."
                        else "Sıcaklık uyarısı kapatıldı."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Termal uyarı ayarı kaydedilemedi.") }
            }
        }
    }

    fun setThermalThreshold(celsius: Int) {
        val safeTemp = celsius.coerceIn(36, 48)
        viewModelScope.launch {
            try {
                settings.setThermalThresholdCelsius(safeTemp)
                settings.setThermalAlertSent(false)
                _uiState.update {
                    it.copy(message = "Sıcaklık uyarı sınırı $safeTemp °C olarak ayarlandı.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Sıcaklık sınırı kaydedilemedi.") }
            }
        }
    }

    fun setEcoGuardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settings.setEcoGuardEnabled(enabled)
                _uiState.update {
                    it.copy(
                        message = if (enabled) "Eco Guard öneri profili kaydedildi; Android ayarları otomatik değiştirilmez."
                        else "Eco Guard öneri profili kapatıldı."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Eco Guard ayarı güncellenemedi.") }
            }
        }
    }

    fun setDesignCapacityMah(capacityMah: Int) {
        val safeCap = capacityMah.coerceIn(1500, 12000)
        val requestId = ++designCapacityRequestSequence
        pendingDesignCapacityRequestId = requestId
        pendingDesignCapacityMah = safeCap
        _uiState.update {
            it.copy(
                designCapacityMah = safeCap,
                designCapacityMessage = "$safeCap mAh kaydediliyor…",
                isSavingDesignCapacity = true,
                isDesignCapacitySaveError = false
            )
        }

        viewModelScope.launch {
            try {
                designCapacityWriteMutex.withLock {
                    // A newer chip tap superseded this request before its write began.
                    if (pendingDesignCapacityRequestId != requestId || pendingDesignCapacityMah != safeCap) {
                        return@withLock
                    }

                    settings.setDesignCapacityMah(safeCap)
                    if (pendingDesignCapacityRequestId == requestId) {
                        persistedDesignCapacityMah = safeCap
                        pendingDesignCapacityRequestId = null
                        pendingDesignCapacityMah = null
                        _uiState.update {
                            it.copy(
                                designCapacityMah = safeCap,
                                designCapacityMessage = "Referans kapasite $safeCap mAh olarak kaydedildi.",
                                isSavingDesignCapacity = false,
                                isDesignCapacitySaveError = false
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (pendingDesignCapacityRequestId == requestId) {
                    pendingDesignCapacityRequestId = null
                    pendingDesignCapacityMah = null
                    _uiState.update {
                        it.copy(
                            designCapacityMah = persistedDesignCapacityMah,
                            designCapacityMessage = "Kapasite kaydedilemedi; son kayıtlı değer korundu.",
                            isSavingDesignCapacity = false,
                            isDesignCapacitySaveError = true
                        )
                    }
                }
            }
        }
    }

    fun refreshUsageApps() {
        viewModelScope.launch {
            try {
                val hasAccess = usageStats.hasUsageAccess()
                _uiState.update { it.copy(hasUsageAccess = hasAccess, isLoadingUsage = hasAccess) }
                if (!hasAccess) {
                    _uiState.update { it.copy(topApps = emptyList(), isLoadingUsage = false) }
                    return@launch
                }
                val apps = usageStats.loadTodayUsage()
                _uiState.update { it.copy(topApps = apps, isLoadingUsage = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isLoadingUsage = false, message = "Uygulama kullanım verileri okunamadı.")
                }
            }
        }
    }


    fun refreshWakelockAccess() {
        _uiState.update {
            it.copy(wakelockAccessState = wakelockDiagnostics.currentAccessState())
        }
    }

    fun requestWakelockPermission() {
        val requested = wakelockDiagnostics.requestPermission()
        _uiState.update {
            it.copy(
                wakelockAccessState = wakelockDiagnostics.currentAccessState(),
                wakelockErrorMessage = if (requested) null
                else "Shizuku izni istenemedi. Shizuku/Sui uygulamasının çalıştığını kontrol et."
            )
        }
    }

    fun refreshWakelockDiagnostics() {
        if (_uiState.value.isLoadingWakelocks) return
        viewModelScope.launch {
            val access = wakelockDiagnostics.currentAccessState()
            _uiState.update {
                it.copy(
                    wakelockAccessState = access,
                    isLoadingWakelocks = access == WakelockAccessState.READY,
                    wakelockErrorMessage = null,
                    wakelockRecords = emptyList(),
                    wakelockCapturedAtEpochMillis = null
                )
            }
            if (access != WakelockAccessState.READY) return@launch

            try {
                when (val result = wakelockDiagnostics.readSinceLastCharge()) {
                    is WakelockDiagnosticsResult.Success -> _uiState.update {
                        it.copy(
                            isLoadingWakelocks = false,
                            wakelockRecords = result.records,
                            wakelockCapturedAtEpochMillis = result.capturedAtEpochMillis,
                            wakelockAccessState = WakelockAccessState.READY,
                            wakelockErrorMessage = null
                        )
                    }
                    is WakelockDiagnosticsResult.Unavailable -> _uiState.update {
                        it.copy(
                            isLoadingWakelocks = false,
                            wakelockRecords = emptyList(),
                            wakelockAccessState = result.accessState
                        )
                    }
                    is WakelockDiagnosticsResult.Failure -> _uiState.update {
                        it.copy(isLoadingWakelocks = false, wakelockErrorMessage = result.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingWakelocks = false,
                        wakelockErrorMessage = error.message?.takeIf(String::isNotBlank)
                            ?: "Wakelock tanılama işlemi tamamlanamadı."
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private data class Part1(
        val monitoring: Boolean,
        val chargeLimit: Boolean,
        val chargeTarget: Int,
        val lowBatAlert: Boolean,
        val lowBatThreshold: Int
    )

    private data class Part2(
        val thermalAlert: Boolean,
        val thermalThreshold: Int,
        val ecoGuard: Boolean,
        val designCap: Int
    )
}
