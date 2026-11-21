package com.vortix.app.platform.telemetry

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vortix.app.MainActivity
import com.vortix.app.domain.repository.BatteryRepository
import com.vortix.app.domain.repository.ChargingProfileRepository
import com.vortix.app.data.work.ChargeWebhookTriggerCoordinator
import com.vortix.app.domain.repository.EnergySettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LiveBatteryTelemetryService : Service() {
    @Inject lateinit var batteryRepository: BatteryRepository
    @Inject lateinit var settings: EnergySettingsRepository
    @Inject lateinit var chargingProfiles: ChargingProfileRepository
    @Inject lateinit var webhookCoordinator: ChargeWebhookTriggerCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var updateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(null), type)
        } catch (_: Exception) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (updateJob?.isActive != true) {
            updateJob = serviceScope.launch {
                while (isActive) {
                    try {
                        val snapshot = batteryRepository.readCurrentSnapshot()
                        // While the user has opted into live telemetry, charger profiles receive 5 s samples.
                        chargingProfiles.recordSnapshot(snapshot)
                        webhookCoordinator.onBatterySnapshot(snapshot)?.let { result ->
                            notifyWebhookResult(result.success, result.message)
                        }
                        val limitEnabled = settings.chargeLimitEnabled.first()
                        val target = settings.chargeTargetPercent.first()
                        val content = LiveTelemetryFormatter.format(snapshot, limitEnabled, target)
                        getSystemService(NotificationManager::class.java)?.notify(
                            NOTIFICATION_ID,
                            buildNotification(content)
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the persistent notification alive; the next sensor cycle retries.
                    }
                    delay(UPDATE_INTERVAL_MILLIS)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(content: LiveTelemetryFormatter.Content?): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(content?.title ?: "VORTIX · CANLI TELEMETRİ")
            .setContentText(content?.compactText ?: "Pil sensörleri okunuyor…")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content?.expandedText ?: "Pil sensörleri okunuyor…"))
            .setContentIntent(openAppIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        return builder.build()
    }

    private fun notifyWebhookResult(success: Boolean, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(if (success) "VORTIX · Akıllı priz tetiklendi" else "VORTIX · Webhook başarısız")
            .setContentText(message.take(160))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(WEBHOOK_RESULT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Canlı pil telemetrisi", NotificationManager.IMPORTANCE_LOW).apply {
                description = "VORTIX anlık pil gücü, akım ve sıcaklık bilgileri"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    override fun onDestroy() {
        updateJob?.cancel()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.vortix.app.action.STOP_LIVE_TELEMETRY"
        private const val CHANNEL_ID = "vortix_live_battery_telemetry"
        private const val NOTIFICATION_ID = 7406
        private const val WEBHOOK_RESULT_NOTIFICATION_ID = 7407
        private const val UPDATE_INTERVAL_MILLIS = 5_000L
    }
}
