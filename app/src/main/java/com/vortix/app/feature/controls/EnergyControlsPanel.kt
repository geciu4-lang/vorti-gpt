package com.vortix.app.feature.controls

import android.Manifest
import android.content.Intent
import android.content.Context
import android.widget.Toast
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortix.app.domain.model.AppUsageItem
import com.vortix.app.domain.model.WakelockAccessState
import com.vortix.app.domain.model.WakelockRecord
import com.vortix.app.feature.dashboard.GlassContainer
import com.vortix.app.platform.UsageAccessSettingsLauncher
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnergyControlsPanel(viewModel: EnergyControlsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.refreshUsageApps()
            viewModel.refreshWakelockAccess()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsageApps()
                viewModel.refreshWakelockAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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
                text = "Şarj hedefi bildirimi, düşük pil alarmı, sıcaklık uyarısı ve uygulama radarı.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.message?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
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
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = viewModel::clearMessage) {
                        Text("Tamam", style = MaterialTheme.typography.labelSmall)
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

                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    OutlinedButton(
                        onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Bildirim iznini etkinleştir") }
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

        // 6. Vortex Drain Radar Card
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Vortex Drain Radar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ön plan süresi bugüne aittir. Mobil ve Wi-Fi verileri son 24 saatlik sorgu aralığındaki saatlik sistem kayıtlarından gelir; uygulama başına mAh veya kesin pil tüketimi değildir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!state.hasUsageAccess) {
                    Button(
                        onClick = {
                            openUsageAccessSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Kullanım erişimi iznini aç") }
                } else {
                    Text(
                        text = "Ön plan: bugün · Ağ: son 24 saatlik sorgu aralığı (saatlik kovalar) · Sıralama pil tüketimi değildir.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.isLoadingUsage) {
                        Text("Kullanım ve ağ istatistikleri taranıyor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (state.topApps.isEmpty()) {
                        Text("Görüntülenecek kullanım veya UID ağ trafiği kaydı bulunamadı.")
                    } else {
                        state.topApps.forEach { app ->
                            UsageAppRow(
                                app = app,
                                onOpenSettings = {
                                    safeStartActivity(
                                        context,
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                        }
                                    )
                                }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::refreshUsageApps,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("↻ Radar listesini yenile") }
                }
            }
        }

        // 7. Optional advanced Wakelock diagnostics (Shizuku/Sui)
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "GELİŞMİŞ WAKELOCK TANILAMASI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Android Batterystats kayıtları son şarjdan itibaren okunur. Bu süreler uygulama başına mAh tüketimi değildir ve kilitleri otomatik kapatmaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state.wakelockAccessState) {
                    WakelockAccessState.SHIZUKU_NOT_RUNNING -> {
                        Text(
                            "İsteğe bağlı Shizuku veya Sui bağlantısı bulunamadı. Standart Radar bundan etkilenmez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                safeStartActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Shizuku / Sui kurulum rehberi") }
                    }
                    WakelockAccessState.UNSUPPORTED_VERSION -> {
                        Text(
                            "Shizuku sürümü eski. UserService desteği için Shizuku API v11 veya üzeri gerekir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    WakelockAccessState.PERMISSION_REQUIRED -> {
                        Text(
                            "Shizuku bağlantısı bulundu. Wakelock tanılaması için VORTIX'e Shizuku izni vermelisin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = viewModel::requestWakelockPermission,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Shizuku iznini iste") }
                    }
                    WakelockAccessState.READY -> {
                        OutlinedButton(
                            onClick = viewModel::refreshWakelockDiagnostics,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoadingWakelocks,
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (state.isLoadingWakelocks) "Wakelock kayıtları okunuyor…" else "Wakelock kayıtlarını tara") }
                    }
                }

                if (state.isLoadingWakelocks) {
                    Text("Android Batterystats verisi salt-okunur olarak alınıyor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.wakelockErrorMessage?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.wakelockRecords.isNotEmpty()) {
                    Text(
                        "SON ŞARJDAN BERİ · ${state.wakelockRecords.size} KAYIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.wakelockRecords.take(12).forEach { record ->
                        WakelockDiagnosticRow(record)
                    }
                    Text(
                        "Kayıtlar Android sistem istatistikleridir. Üretici/Android sürümüne göre bazı ayrıntılar bulunmayabilir.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.wakelockAccessState == WakelockAccessState.READY &&
                    !state.isLoadingWakelocks && state.wakelockErrorMessage == null &&
                    state.wakelockCapturedAtEpochMillis != null
                ) {
                    Text("Son şarjdan beri gösterilecek partial wakelock kaydı bulunamadı.", style = MaterialTheme.typography.bodySmall)
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
private fun UsageAppRow(app: AppUsageItem, onOpenSettings: () -> Unit) {
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 16) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (app.foregroundTimeMillis > 0L) {
                        formatDuration(app.foregroundTimeMillis)
                    } else {
                        "Bugün ön plan kullanımı kaydedilmedi"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = if (app.mobileBytes == null && app.wifiBytes == null) {
                        "Ağ istatistiği bu cihazda sunulamadı"
                    } else {
                        "Mobil ${formatBytes(app.mobileBytes)}  ·  Wi-Fi ${formatBytes(app.wifiBytes)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (app.hasSharedUidTraffic) {
                    Text(
                        text = "Ortak UID: en az ${app.sharedUidPackageCount} görünür paket aynı ağ toplamını paylaşır; tek uygulamaya atanamaz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(10.dp)
            ) { Text("Ayarlar") }
        }
    }
}


@Composable
private fun WakelockDiagnosticRow(record: WakelockRecord) {
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 16) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = record.packageNames.take(3).joinToString().ifBlank { "UID ${record.uid}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (record.packageNames.size > 1) {
                Text(
                    "Ortak UID grubu · ${record.packageNames.size} paket · UID ${record.uid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(record.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            val duration = record.actualPartialDurationMillis ?: record.partialTimeMillis
            Text(
                text = "Partial süre: ${formatElapsedDuration(duration)}" +
                    (record.partialCount?.let { " · $it kez" } ?: " · sayaç sunulmadı"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            record.backgroundPartialDurationMillis?.takeIf { it > 0L }?.let { backgroundDuration ->
                Text(
                    text = "Arka plan partial: ${formatElapsedDuration(backgroundDuration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (record.actualPartialDurationMillis == null) {
                Text(
                    "Bu Android sürümünde yalnızca paylaştırılmış süre mevcut.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

private fun openUsageAccessSettings(context: Context) {
    if (!UsageAccessSettingsLauncher.open(context)) {
        Toast.makeText(context, "Kullanım erişimi ayarları açılamadı.", Toast.LENGTH_LONG).show()
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

private fun formatElapsedDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours} sa ${minutes} dk"
        minutes > 0L -> "${minutes} dk ${seconds} sn"
        else -> "${seconds} sn"
    }
}
