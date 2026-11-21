package com.vortix.app.feature.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import com.vortix.app.domain.repository.BatteryRepository
import com.vortix.app.domain.repository.ChargingProfileRepository
import com.vortix.app.domain.repository.EnergyMonitorScheduler
import com.vortix.app.domain.repository.EnergySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ChargingProfileViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val profileRepository: ChargingProfileRepository,
    private val monitorScheduler: EnergyMonitorScheduler,
    private val settings: EnergySettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChargingProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeProfiles().collect { profiles ->
                _uiState.update { it.copy(profiles = profiles) }
            }
        }
        viewModelScope.launch {
            profileRepository.observeActiveProfile().collect { active ->
                _uiState.update { it.copy(activeProfile = active) }
            }
        }
        refreshChargeState()
    }

    fun refreshChargeState() {
        viewModelScope.launch {
            runCatching { batteryRepository.readCurrentSnapshot() }
                .onSuccess { snapshot ->
                    val charging = snapshot.status == BatteryStatus.CHARGING &&
                        snapshot.pluggedSource != PluggedSource.NONE && snapshot.pluggedSource != PluggedSource.UNKNOWN
                    _uiState.update { it.copy(isCharging = charging) }
                    if (!charging && _uiState.value.activeProfile != null) {
                        profileRepository.recordSnapshot(snapshot)
                    }
                }
        }
    }

    fun startProfile(name: String) {
        val cleanName = name.trim().take(48)
        if (cleanName.isBlank()) {
            _uiState.update { it.copy(message = "Adaptör profili için bir isim gir.") }
            return
        }
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val snapshot = batteryRepository.readCurrentSnapshot()
                profileRepository.startProfile(cleanName, snapshot)
                if (!settings.monitoringEnabled.first()) {
                    monitorScheduler.start()
                    settings.setMonitoringEnabled(true)
                }
                _uiState.update { it.copy(isSaving = false, message = "'$cleanName' profili kaydedildi; şarj oturumu izleniyor.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(isSaving = false, message = error.message ?: "Adaptör profili başlatılamadı.") }
            }
        }
    }

    fun finishProfile() {
        viewModelScope.launch {
            try {
                profileRepository.finishActiveProfile()
                _uiState.update { it.copy(message = "Adaptör profili oturumu tamamlandı.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "Adaptör profili tamamlanamadı.") }
            }
        }
    }
}
