package com.vortix.app.feature.controls

import android.Manifest
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortix.app.domain.model.AppUsageItem
import com.vortix.app.domain.model.ChargingProfile
import com.vortix.app.feature.dashboard.GlassContainer
import com.vortix.app.platform.telemetry.LiveBatteryTelemetryService
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnergyControlsPanel(
    viewModel: EnergyControlsViewModel,
    benchmarkViewModel: ChargingBenchmarkViewModel,
    chargingProfileViewModel: ChargingProfileViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profileState by chargingProfileViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Keep charging-dependent cards live even when the user plugs/unplugs the phone
    // without leaving the Controls tab. The loop pauses while the app is not RESUMED.
    LaunchedEffect(lifecycleOwner, benchmarkViewModel, chargingProfileViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                benchmarkViewModel.refreshChargeState()
                chargingProfileViewModel.refreshChargeState()
                delay(5_000L)
            }
        }
    }
    var pendingLiveTelemetryPermission by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingLiveTelemetryPermission) {
            pendingLiveTelemetryPermission = false
            if (granted) viewModel.setLiveTelemetryEnabled(true)
            else {
                viewModel.setLiveTelemetryEnabled(false)
                Toast.makeText(context, "Canlı telemetri için bildirim izni gerekli.", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(state.isLiveTelemetryEnabled) {
        val serviceIntent = Intent(context, LiveBatteryTelemetryService::class.java)
        if (state.isLiveTelemetryEnabled) {
            val permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (permissionGranted) {
                runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
                    .onFailure {
                        viewModel.setLiveTelemetryEnabled(false)
                        Toast.makeText(context, "Canlı telemetri servisi başlatılamadı.", Toast.LENGTH_LONG).show()
                    }
            } else {
                viewModel.setLiveTelemetryEnabled(false)
                Toast.makeText(context, "Canlı telemetri için bildirim izni gerekli.", Toast.LENGTH_LONG).show()
            }
        } else {
            context.stopService(serviceIntent)
        }
    }

    var sliderTarget by remember(state.chargeTargetPercent) {
        mutableFloatStateOf(state.chargeTargetPercent.toFloat())
    }
    var lowBatSlider by remember(state.lowBatteryThresholdPercent) {
        mutableFloatStateOf(state.lowBatteryThresholdPercent.toFloat())
    }
    var thermalSlider by remember(state.thermalThresholdCelsius) {
        mutableFloatStateOf(state.thermalThresholdCelsius.toFloat())
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "ENERJİ KONTROL MERKEZİ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Şarj hedefi, pil koruması, canlı telemetri ve adaptör profillerini yönet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // High-value 7.1.2 UI integrations stay above the legacy tuning cards so users can find them immediately.
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22, highlighted = state.isLiveTelemetryEnabled) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CANLI BİLDİRİM TELEMETRİSİ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Bildirim panelinde güç, akım, batarya sıcaklığı ve hedefe kalan yüzdeyi yaklaşık 5 saniyede bir gösterir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isLiveTelemetryEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                viewModel.setLiveTelemetryEnabled(false)
                            } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                pendingLiveTelemetryPermission = true
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setLiveTelemetryEnabled(true)
                            }
                        }
                    )
                }
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    OutlinedButton(
                        onClick = {
                            pendingLiveTelemetryPermission = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Canlı bildirim için izin ver") }
                }
                Text(
                    if (state.isLiveTelemetryEnabled) "Bildirim servisi etkinleştirildiğinde kalıcı ve sessiz bir bildirim gösterilir."
                    else "Kapalı · Bildirim servisi çalışmıyor.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isLiveTelemetryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ChargingBenchmarkCard(benchmarkViewModel)
        ChargingProfileCard(chargingProfileViewModel, profileState)

        AnimatedContent(
            targetState = state.message?.let { state.designCapacityFeedbackId to it },
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) +
                    slideInVertically(animationSpec = tween(180), initialOffsetY = { -it / 4 })) togetherWith
                    (fadeOut(animationSpec = tween(120)) +
                        slideOutVertically(animationSpec = tween(120), targetOffsetY = { -it / 4 }))
            },
            label = "controlsStatusBanner"
        ) { bannerEvent ->
            bannerEvent?.second?.let { message ->
                val bannerTint = when {
                    state.isDesignCapacitySaveError -> MaterialTheme.colorScheme.error
                    state.isSavingDesignCapacity -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = bannerTint.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, bannerTint.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = bannerTint,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = viewModel::clearMessage) {
                            Text("Tamam", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 1. Charge Limit Card
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22, highlighted = state.isChargeLimitEnabled) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Şarj Hedef Bildirimi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isChargeLimitEnabled) {
                                "Aktif — hedefe ulaşıldığında bildirim gönderilir; donanım şarjı durdurulmaz."
                            } else {
                                "Kapalı — bu şarj hedefi için bildirim gönderilmez."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isChargeLimitEnabled,
                        onCheckedChange = viewModel::setChargeLimitEnabled
                    )
                }

                AnimatedVisibility(visible = state.isChargeLimitEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "%${sliderTarget.roundToInt()}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(80, 85, 90, 95).forEach { preset ->
                                    FilterChip(
                                        selected = state.chargeTargetPercent == preset,
                                        onClick = {
                                            sliderTarget = preset.toFloat()
                                            viewModel.setChargeTarget(preset)
                                        },
                                        label = { Text("%$preset", fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }

                        Slider(
                            value = sliderTarget,
                            onValueChange = { sliderTarget = it },
                            onValueChangeFinished = {
                                viewModel.setChargeTarget(sliderTarget.roundToInt().coerceIn(70, 100))
                            },
                            valueRange = 70f..100f,
                            steps = 5
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Webhook Tetikleyici", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Akıllı priz veya Home Assistant webhook adresini hedefe ulaşıldığında çağırır.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.isChargeWebhookEnabled,
                                onCheckedChange = viewModel::setChargeWebhookEnabled
                            )
                        }
                        OutlinedTextField(
                            value = state.chargeWebhookUrl,
                            onValueChange = viewModel::setChargeWebhookUrl,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("HTTPS Webhook URL") },
                            placeholder = { Text("https://home.example/api/webhook/…") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = state.chargeWebhookMethod == "GET",
                                onClick = { viewModel.setChargeWebhookMethod("GET") },
                                label = { Text("GET") }
                            )
                            FilterChip(
                                selected = state.chargeWebhookMethod == "POST",
                                onClick = { viewModel.setChargeWebhookMethod("POST") },
                                label = { Text("POST") }
                            )
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = viewModel::testChargeWebhook,
                                enabled = !state.isTestingChargeWebhook && state.chargeWebhookUrl.isNotBlank(),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(if (state.isTestingChargeWebhook) "Gönderiliyor…" else "Test Et") }
                        }
                        Text(
                            if (state.isChargeWebhookEnabled) "Otomatik tetikleme açık: şarj hedefi algılandığında webhook çağrılır."
                            else "Otomasyon kapalı: URL'yi kaydedebilir ve Test Et ile bağlantıyı deneyebilirsiniz.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isChargeWebhookEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Test Et gerçek uç noktayı çağırır; Home Assistant otomasyonunu tetikleyebilir. WorkManager nedeniyle otomatik çağrı tam anlık olmayabilir. Yalnızca HTTPS desteklenir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        state.chargeWebhookLastResult?.let { result ->
                            Text(
                                result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.contains("başarılı", true) || result.contains("HTTP 2"))
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                }
            }
        }

        // 2. Low Battery Alarm Card
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Düşük Pil Uyarısı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Derin deşarjı önlemek için pil %${state.lowBatteryThresholdPercent} altına indiğinde uyar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isLowBatteryAlertEnabled,
                        onCheckedChange = viewModel::setLowBatteryAlertEnabled
                    )
                }

                AnimatedVisibility(visible = state.isLowBatteryAlertEnabled) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "%${lowBatSlider.roundToInt()}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 20, 25, 30).forEach { preset ->
                                    FilterChip(
                                        selected = state.lowBatteryThresholdPercent == preset,
                                        onClick = {
                                            lowBatSlider = preset.toFloat()
                                            viewModel.setLowBatteryThreshold(preset)
                                        },
                                        label = { Text("%$preset") }
                                    )
                                }
                            }
                        }
                        Slider(
                            value = lowBatSlider,
                            onValueChange = { lowBatSlider = it },
                            onValueChangeFinished = {
                                viewModel.setLowBatteryThreshold(lowBatSlider.roundToInt().coerceIn(10, 40))
                            },
                            valueRange = 10f..40f,
                            steps = 5
                        )
                    }
                }
            }
        }

        // 3. Thermal Guard Card
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Pil Sıcaklığı Uyarısı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Batarya sıcaklığı ${state.thermalThresholdCelsius} °C üzerine çıktığında uyar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isThermalAlertEnabled,
                        onCheckedChange = viewModel::setThermalAlertEnabled
                    )
                }

                AnimatedVisibility(visible = state.isThermalAlertEnabled) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${thermalSlider.roundToInt()} °C",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Önerilen sınır: 40–43 °C",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = thermalSlider,
                            onValueChange = { thermalSlider = it },
                            onValueChangeFinished = {
                                viewModel.setThermalThreshold(thermalSlider.roundToInt().coerceIn(36, 48))
                            },
                            valueRange = 36f..48f,
                            steps = 11
                        )
                    }
                }
            }
        }

        // 4. Design Capacity Calibration Card (for Health % calculation)
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nominal Tasarım Kapasitesi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sağlık yüzdesi hesabı için cihaz üreticisinin belirttiği nominal tasarım kapasitesini seçin. Emin değilseniz cihaz teknik özelliklerini kontrol edin; VORTIX bunu kendiliğinden varsaymaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000, 9000, 10000, 11000, 12000).forEach { cap ->
                        FilterChip(
                            selected = state.designCapacityMah == cap,
                            onClick = { viewModel.setDesignCapacityMah(cap) },
                            label = { Text("$cap mAh", fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
                state.designCapacityMessage?.let { capacityMessage ->
                    Text(
                        text = capacityMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            state.isDesignCapacitySaveError -> MaterialTheme.colorScheme.error
                            state.isSavingDesignCapacity -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 5. Background Telemetry & Eco Guard Card
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Arka Plan Telemetri Planı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "En az 15 dakikalık aralıklarla ölçüm yapar. Kapalıysa arka plan bildirim kontrolleri de durur; Android çalışmayı erteleyebilir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isMonitoringEnabled,
                        onCheckedChange = viewModel::setMonitoringEnabled
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Eco Guard Öneri Profili",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Açıksa Power Advisor, %35 pil seviyesinden itibaren Tasarruf modunu daha erken önerebilir. Ayarı otomatik açmaz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isEcoGuardEnabled,
                        onCheckedChange = viewModel::setEcoGuardEnabled
                    )
                }
            }
        }

        // 8. System Shortcuts Card
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Hızlı Sistem Kontrolleri",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = {
                        safeStartActivity(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Android Pil Tasarrufu Ayarları") }
                OutlinedButton(
                    onClick = {
                        safeStartActivity(
                            context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Vortix Arka Plan & Pil İzinleri") }
            }
        }
    }
}

@Composable
private fun ChargingProfileCard(
    viewModel: ChargingProfileViewModel,
    state: ChargingProfileUiState
) {
    var profileName by remember { mutableStateOf("") }
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22, highlighted = state.activeProfile != null) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ADAPTÖR DİJİTAL PARMAK İZİ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Şarj oturumu boyunca batarya tarafında ölçülen ortalama/tepe güç ve sıcaklık değişimini profil olarak kaydeder. USB adaptör giriş gücü veya kablo direnci doğrudan ölçülmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Canlı telemetri açıksa yaklaşık 5 sn örneklenir; yalnızca WorkManager kullanılıyorsa Android örneklemeyi yaklaşık 15 dk veya daha uzun süre erteleyebilir. Skor aynı cihazdaki profilleri kıyaslamak için sezgiseldir.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it.take(48) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Adaptör profili adı") },
                placeholder = { Text("Orijinal 65W / Araç Şarjı") }
            )
            if (state.activeProfile == null) {
                Button(
                    onClick = { viewModel.startProfile(profileName); profileName = "" },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isCharging && !state.isSaving && profileName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (state.isSaving) "Başlatılıyor…" else "Adaptör Profilini Kaydet") }
                if (!state.isCharging) {
                    Text("Profil kaydı yalnızca cihaz şarj olurken etkinleşir.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AKTİF · ${state.activeProfile.name}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("${state.activeProfile.sampleCount} geçerli güç örneği toplandı",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = viewModel::finishProfile, shape = RoundedCornerShape(10.dp)) { Text("Bitir") }
                }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            if (state.profiles.size >= 2) {
                Text("SON İKİ PROFİL · YAN YANA KARŞILAŞTIRMA", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdapterProfileComparisonColumn(state.profiles[0], Modifier.weight(1f))
                    AdapterProfileComparisonColumn(state.profiles[1], Modifier.weight(1f))
                }
            }
            if (state.profiles.isNotEmpty()) {
                Text("KAYITLI ADAPTÖRLER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.profiles.take(8).forEach { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(profile.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Ort. ${profile.averagePowerWatts?.let { String.format(Locale.US, "%.1f W", it) } ?: "—"}  ·  Tepe ${profile.peakPowerWatts?.let { String.format(Locale.US, "%.1f W", it) } ?: "—"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "ΔT ${profile.temperatureDeltaCelsius?.let { String.format(Locale.US, "%+.1f °C", it) } ?: "—"}  ·  ${profile.efficiencyGrade ?: "Skor yok"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (profile.efficiencyGrade) {
                                    "A+", "A" -> MaterialTheme.colorScheme.primary
                                    "C" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdapterProfileComparisonColumn(profile: ChargingProfile, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 2)
            Text("Ort. ${profile.averagePowerWatts?.let { String.format(Locale.US, "%.1f W", it) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall)
            Text("Tepe ${profile.peakPowerWatts?.let { String.format(Locale.US, "%.1f W", it) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall)
            Text("ΔT ${profile.temperatureDeltaCelsius?.let { String.format(Locale.US, "%+.1f°C", it) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall)
            Text(profile.efficiencyGrade ?: "Skor yok", style = MaterialTheme.typography.labelLarge,
                color = if (profile.efficiencyGrade == "C") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChargingBenchmarkCard(viewModel: ChargingBenchmarkViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22, highlighted = state.isRunning) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ADAPTÖR HIZ TESTİ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "30 saniyelik akım, voltaj ve sıcaklık örneklemesi. Puan, cihazın batarya tarafındaki ölçümlerine dayanır; USB adaptör çıkış gücünü veya kabloyu doğrudan test etmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                state.isRunning -> {
                    Text("Test sürüyor · ${state.remainingSeconds} sn kaldı", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (1f - state.remainingSeconds / 30f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.isDeviceCharging -> Text("Şarj bağlantısı algılandı · Teste hazır", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                else -> Text("Test yalnızca cihaz şarj olurken kullanılabilir.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::startTest,
                    enabled = state.isDeviceCharging && !state.isRunning,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.isRunning) "Test sürüyor" else "30 sn testi başlat") }
                if (state.isRunning) {
                    OutlinedButton(onClick = viewModel::cancelTest) { Text("İptal") }
                }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.result?.let { result ->
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(result.grade.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Text("${result.score}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        BenchmarkMetricLine("Tepe güç", String.format(Locale.getDefault(), "%.1f W", result.peakPowerWatts))
                        BenchmarkMetricLine("Ortalama akım", String.format(Locale.getDefault(), "%.0f mA", result.averageCurrentMilliamps))
                        BenchmarkMetricLine("Voltaj kararlılığı (CV)", result.voltageVariationPercent?.let { String.format(Locale.getDefault(), "%.2f%%", it) } ?: "Yetersiz veri")
                        BenchmarkMetricLine("Sıcaklık artışı", result.temperatureIncreaseCelsius?.let { String.format(Locale.getDefault(), "%+.1f °C", it) } ?: "Yetersiz veri")
                        Text(result.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkMetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun UsageAppRow(
    app: AppUsageItem,
    showShizukuActions: Boolean,
    isBusy: Boolean,
    onForceStop: () -> Unit,
    onRestrictBackground: () -> Unit,
    onOpenSettings: () -> Unit
) {
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 16) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(app.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (app.foregroundTimeMillis > 0L) formatDuration(app.foregroundTimeMillis)
                        else "Bugün ön plan kullanımı kaydedilmedi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        if (app.mobileBytes == null && app.wifiBytes == null) "Ağ istatistiği bu cihazda sunulamadı"
                        else "Mobil ${formatBytes(app.mobileBytes)}  ·  Wi-Fi ${formatBytes(app.wifiBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (app.hasSharedUidTraffic) {
                        Text(
                            "Ortak UID: en az ${app.sharedUidPackageCount} paket aynı ağ toplamını paylaşır; tek uygulamaya atanamaz.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            if (showShizukuActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onForceStop,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(if (isBusy) "İşleniyor…" else "Durdur") }
                    OutlinedButton(
                        onClick = onRestrictBackground,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Arka Planı Sınırla") }
                }
                Text(
                    "Shizuku shell üzerinden işlem yapılır. Arka plan kısıtlaması seçilen uygulamanın bildirimlerini ve zamanlanmış işlerini etkileyebilir.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Uygulama Ayarları") }
            }
        }
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    val safeBytes = bytes.coerceAtLeast(0L)
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    return when {
        safeBytes >= gib -> String.format(Locale.getDefault(), "%.2f GB", safeBytes / gib)
        safeBytes >= mib -> String.format(Locale.getDefault(), "%.1f MB", safeBytes / mib)
        safeBytes >= kib -> String.format(Locale.getDefault(), "%.1f KB", safeBytes / kib)
        else -> "$safeBytes B"
    }
}

private fun safeStartActivity(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun formatDuration(milliseconds: Long): String {
    val minutes = milliseconds / 60_000L
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return if (hours > 0L) "Bugün $hours sa $remainder dk" else "Bugün $remainder dk"
}
