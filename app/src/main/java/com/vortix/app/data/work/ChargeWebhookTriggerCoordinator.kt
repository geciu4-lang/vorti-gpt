package com.vortix.app.data.work

import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import com.vortix.app.domain.repository.EnergySettingsRepository
import com.vortix.app.platform.webhook.ChargeTargetWebhookManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared at-most-once gate for WorkManager and the optional 5-second live telemetry service. */
@Singleton
class ChargeWebhookTriggerCoordinator @Inject constructor(
    private val settings: EnergySettingsRepository,
    private val webhookManager: ChargeTargetWebhookManager
) {
    data class DispatchResult(val success: Boolean, val message: String)

    private val mutex = Mutex()

    suspend fun onBatterySnapshot(snapshot: BatterySnapshot): DispatchResult? = mutex.withLock {
        val enabled = settings.chargeWebhookEnabled.first()
        val level = snapshot.levelPercent
        val plugged = snapshot.pluggedSource != PluggedSource.NONE && snapshot.pluggedSource != PluggedSource.UNKNOWN
        val charging = snapshot.status == BatteryStatus.CHARGING || snapshot.status == BatteryStatus.FULL
        val target = settings.chargeTargetPercent.first()
        val chargeTargetEnabled = settings.chargeLimitEnabled.first()
        val reached = enabled && chargeTargetEnabled && plugged && charging && level != null && level >= target
        val alreadyTriggered = settings.chargeWebhookTriggered.first()

        if (!reached) {
            if (alreadyTriggered) settings.setChargeWebhookTriggered(false)
            return@withLock null
        }
        if (alreadyTriggered) return@withLock null

        // Persist before the network call so concurrent polling sources cannot send twice.
        settings.setChargeWebhookTriggered(true)
        val url = settings.chargeWebhookUrl.first()
        val method = if (settings.chargeWebhookMethod.first() == "GET") {
            ChargeTargetWebhookManager.Method.GET
        } else {
            ChargeTargetWebhookManager.Method.POST
        }
        val result = webhookManager.execute(
            url = url,
            method = method,
            test = false,
            batteryLevelPercent = level,
            targetPercent = target
        )
        settings.setChargeWebhookLastResult(result.message)
        DispatchResult(result.success, result.message)
    }
}
