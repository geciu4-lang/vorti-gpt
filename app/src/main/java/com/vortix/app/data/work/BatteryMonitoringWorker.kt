package com.vortix.app.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.repository.EnergySettingsRepository
import com.vortix.app.domain.repository.BatteryRepository
import com.vortix.app.domain.usecase.RefreshBatterySnapshotUseCase
import com.vortix.app.widget.VortixBatteryWidgetProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class BatteryMonitoringWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refreshBatterySnapshot: RefreshBatterySnapshotUseCase,
    private val settings: EnergySettingsRepository,
    private val batteryRepository: BatteryRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!settings.monitoringEnabled.first()) return Result.success()

            val snapshot = refreshBatterySnapshot()
            VortixBatteryWidgetProvider.updateAll(applicationContext)
            // Keep the raw history bounded; 180 days is ample for trend analysis and local privacy.
            batteryRepository.pruneHistory(System.currentTimeMillis() - 180L * 24L * 60L * 60L * 1000L)

            val level = snapshot.levelPercent
            val isPluggedIn = snapshot.pluggedSource != com.vortix.app.domain.model.PluggedSource.NONE &&
                snapshot.pluggedSource != com.vortix.app.domain.model.PluggedSource.UNKNOWN
            val isChargingOrFull = snapshot.status == BatteryStatus.CHARGING || snapshot.status == BatteryStatus.FULL

            val chargeLimitEnabled = settings.chargeLimitEnabled.first()
            val chargeTarget = settings.chargeTargetPercent.first()
            val chargeAlertSent = settings.chargeTargetAlertSent.first()
            val chargeTargetReached = chargeLimitEnabled && isPluggedIn && isChargingOrFull &&
                level != null && level >= chargeTarget
            if (chargeTargetReached && !chargeAlertSent && notifyChargeTargetReached(level!!, chargeTarget)) {
                settings.setChargeTargetAlertSent(true)
            } else if ((!chargeTargetReached || !chargeLimitEnabled) && chargeAlertSent) {
                settings.setChargeTargetAlertSent(false)
            }

            val lowBatteryEnabled = settings.lowBatteryAlertEnabled.first()
            val lowBatteryThreshold = settings.lowBatteryThresholdPercent.first()
            val lowBatteryAlertSent = settings.lowBatteryAlertSent.first()
            val lowBatteryReached = lowBatteryEnabled && snapshot.status == BatteryStatus.DISCHARGING &&
                level != null && level <= lowBatteryThreshold
            if (lowBatteryReached && !lowBatteryAlertSent && notifyLowBattery(level!!, lowBatteryThreshold)) {
                settings.setLowBatteryAlertSent(true)
            } else if ((!lowBatteryReached || !lowBatteryEnabled) && lowBatteryAlertSent) {
                settings.setLowBatteryAlertSent(false)
            }

            val thermalEnabled = settings.thermalAlertEnabled.first()
            val thermalThreshold = settings.thermalThresholdCelsius.first()
            val thermalAlertAlreadySent = settings.thermalAlertSent.first()
            val temperature = snapshot.temperatureCelsius
            val batteryTooHot = thermalEnabled && (
                (temperature != null && temperature >= thermalThreshold.toDouble()) ||
                    (snapshot.thermalStatus ?: 0) >= SEVERE_THERMAL_STATUS
                )
            val batteryCooled = (temperature?.let { it <= (thermalThreshold - 3).toDouble() }
                ?: (snapshot.thermalStatus?.let { it < SEVERE_THERMAL_STATUS } ?: false)) &&
                (snapshot.thermalStatus?.let { it < SEVERE_THERMAL_STATUS } ?: true)
            if (batteryTooHot && !thermalAlertAlreadySent && notifyThermalWarning(temperature)) {
                settings.setThermalAlertSent(true)
            } else if ((!thermalEnabled || batteryCooled) && thermalAlertAlreadySent) {
                settings.setThermalAlertSent(false)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notifyChargeTargetReached(level: Int, target: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Şarj hedefi uyarıları",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Vortix şarj hedefi bildirimleri" }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Vortix şarj hedefi")
            .setContentText("Pil %$level seviyesinde. Belirlediğin %$target hedefini geçti.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }


    private fun notifyLowBattery(level: Int, threshold: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    LOW_BATTERY_CHANNEL_ID,
                    "Düşük pil uyarıları",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Vortix düşük pil bildirimleri" }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, LOW_BATTERY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Vortix · Pil seviyesi düşük")
            .setContentText("Pil %$level seviyesinde. Belirlediğin %$threshold eşiğine ulaşıldı.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(LOW_BATTERY_NOTIFICATION_ID, notification)
        return true
    }

    private fun notifyThermalWarning(temperatureCelsius: Double?): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    THERMAL_CHANNEL_ID,
                    "Pil sıcaklığı uyarıları",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Vortix pil sıcaklığı ve termal durum uyarıları" }
            )
        }
        val detail = temperatureCelsius?.let { "Pil sıcaklığı ${"%.1f".format(java.util.Locale.US, it)}°C." }
            ?: "Android termal durumu ciddi seviyeye ulaştı."
        val notification = NotificationCompat.Builder(applicationContext, THERMAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Vortix · Pil sıcaklığı yüksek")
            .setContentText("$detail Mümkünse ağır kullanımı durdurup cihazı serinlet.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(THERMAL_NOTIFICATION_ID, notification)
        return true
    }

    private companion object {
        const val CHANNEL_ID = "vortix_charge_target"
        const val NOTIFICATION_ID = 4101
        const val THERMAL_NOTIFICATION_ID = 4102
        const val LOW_BATTERY_NOTIFICATION_ID = 4103
        const val LOW_BATTERY_CHANNEL_ID = "vortix_low_battery"
        const val THERMAL_CHANNEL_ID = "vortix_thermal_alerts"
        const val SEVERE_THERMAL_STATUS = 3
    }
}
