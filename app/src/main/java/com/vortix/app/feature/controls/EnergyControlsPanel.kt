package com.vortix.app.feature.controls

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortix.app.domain.model.AppUsageItem
import com.vortix.app.domain.model.ChargingProfile
import com.vortix.app.domain.model.WakelockAccessState
import com.vortix.app.feature.dashboard.GlassContainer
import com.vortix.app.platform.telemetry.LiveBatteryTelemetryService
import com.vortix.app.ui.theme.*
import com.vortix.app.ui.components.GlassChip
import com.vortix.app.ui.components.GlassPillButton
import com.vortix.app.ui.components.GlassToggleRow
import com.vortix.app.ui.components.GlassInner
import com.vortix.app.ui.components.GlassProgress
import com.vortix.app.ui.components.VortixGlassShapes
import com.vortix.app.ui.components.glassSurface
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

    LaunchedEffect(lifecycleOwner, benchmarkViewModel, chargingProfileViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                benchmarkViewModel.refreshChargeState()
                chargingProfileViewModel.refreshChargeState()
                viewModel.refreshWakelockAccess()
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

    var sliderTarget by remember(state.chargeTargetPercent) { mutableFloatStateOf(state.chargeTargetPercent.toFloat()) }
    var lowBatSlider by remember(state.lowBatteryThresholdPercent) { mutableFloatStateOf(state.lowBatteryThresholdPercent.toFloat()) }
    var thermalSlider by remember(state.thermalThresholdCelsius) { mutableFloatStateOf(state.thermalThresholdCelsius.toFloat()) }
    var showLowBatterySettings by remember { mutableStateOf(false) }
    var showThermalSettings by remember { mutableStateOf(false) }
    var webhookProvider by rememberSaveable { mutableStateOf("Akıllı Priz") }
    var showAdvancedControls by rememberSaveable { mutableStateOf(false) }

    if (showLowBatterySettings) {
        AlertDialog(
            onDismissRequest = { showLowBatterySettings = false },
            title = { Text("Düşük Pil Uyarısı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Uyarı etkin", modifier = Modifier.weight(1f))
                        Switch(state.isLowBatteryAlertEnabled, viewModel::setLowBatteryAlertEnabled)
                    }
                    Text("Eşik: %${lowBatSlider.roundToInt()}", color = VortixPrimary)
                    Slider(
                        value = lowBatSlider,
                        onValueChange = { lowBatSlider = it },
                        onValueChangeFinished = { viewModel.setLowBatteryThreshold(lowBatSlider.roundToInt().coerceIn(10, 40)) },
                        valueRange = 10f..40f,
                        steps = 5
                    )
                    Text("Yalnızca cihaz pilde kullanılırken eşik altına inerse uyarılır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showLowBatterySettings = false }) { Text("Tamam") } }
        )
    }

    if (showThermalSettings) {
        AlertDialog(
            onDismissRequest = { showThermalSettings = false },
            title = { Text("Sıcaklık Uyarısı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Uyarı etkin", modifier = Modifier.weight(1f))
                        Switch(state.isThermalAlertEnabled, viewModel::setThermalAlertEnabled)
                    }
                    Text("Eşik: ${thermalSlider.roundToInt()}°C", color = VortixTertiary)
                    Slider(
                        value = thermalSlider,
                        onValueChange = { thermalSlider = it },
                        onValueChangeFinished = { viewModel.setThermalThreshold(thermalSlider.roundToInt().coerceIn(36, 48)) },
                        valueRange = 36f..48f,
                        steps = 11
                    )
                    Text("Önerilen sınır 40–43°C. Android MODERATE+ termal durumu ayrıca uyarı üretir.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showThermalSettings = false }) { Text("Tamam") } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Charge target card, matching the compact reference layout.
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22, highlighted = state.isChargeLimitEnabled) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassToggleRow(
                    title = "Şarj Hedef Bildirimi",
                    subtitle = if (state.isChargeLimitEnabled) "Etkin · hedef %${state.chargeTargetPercent}" else "Kapalı",
                    checked = state.isChargeLimitEnabled,
                    onCheckedChange = viewModel::setChargeLimitEnabled,
                )
                if (state.isChargeLimitEnabled) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("HEDEF", style = MaterialTheme.typography.labelMedium, color = VortixOnSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("%${sliderTarget.roundToInt()}", style = MaterialTheme.typography.headlineMedium,
                            color = Color.White, fontWeight = FontWeight.Normal)
                    }
                    Slider(
                        value = sliderTarget,
                        onValueChange = { sliderTarget = it },
                        onValueChangeFinished = { viewModel.setChargeTarget(sliderTarget.roundToInt().coerceIn(70, 100)) },
                        valueRange = 70f..100f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = VortixPrimary,
                            activeTrackColor = VortixPrimary,
                            inactiveTrackColor = VortixOutline
                        )
                    )
                }
            }
        }

        // 2. Webhook URL, method selector and explicit test action stay visible.
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22, highlighted = state.isChargeWebhookEnabled) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Webhook Tetikleyici", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Akıllı Priz", "Home Assistant").forEach { provider ->
                        GlassChip(
                            text = provider,
                            selected = webhookProvider == provider,
                            onClick = { webhookProvider = provider },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                TextField(
                    value = state.chargeWebhookUrl,
                    onValueChange = viewModel::setChargeWebhookUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("WEBHOOK URL") },
                    placeholder = { Text("https://home.example/api/webhook/…") },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF202129),
                        unfocusedContainerColor = Color(0xFF202129),
                        disabledContainerColor = Color(0xFF202129),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hedefe tetikle", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.isChargeWebhookEnabled, onCheckedChange = viewModel::setChargeWebhookEnabled)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassChip("GET", state.chargeWebhookMethod == "GET",
                        { viewModel.setChargeWebhookMethod("GET") }, Modifier.weight(1f))
                    GlassChip("POST", state.chargeWebhookMethod == "POST",
                        { viewModel.setChargeWebhookMethod("POST") }, Modifier.weight(1f))
                    GlassPillButton(
                        text = if (state.isTestingChargeWebhook) "Gönderiliyor…" else "Test Et",
                        onClick = viewModel::testChargeWebhook,
                        modifier = Modifier.weight(1.25f),
                        accent = true,
                        enabled = !state.isTestingChargeWebhook && state.chargeWebhookUrl.isNotBlank(),
                    )
                }
                state.chargeWebhookLastResult?.let { result ->
                    Text(result, style = MaterialTheme.typography.labelSmall,
                        color = if (result.contains("başarılı", true) || result.contains("HTTP 2")) VortixPrimary else VortixError,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "Test isteği gerçek akıllı priz veya Home Assistant otomasyonunu tetikleyebilir. HTTPS desteklenir.",
                    style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant
                )
            }
        }

        // 3. Compact threshold tiles open detailed controls on tap.
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Düşük Pil & Sıcaklık Uyarıları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThresholdValueTile(
                        label = "DÜŞÜK PİL",
                        value = "%${state.lowBatteryThresholdPercent}",
                        enabled = state.isLowBatteryAlertEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = { showLowBatterySettings = true }
                    )
                    ThresholdValueTile(
                        label = "SICAKLIK",
                        value = "${state.thermalThresholdCelsius}°C",
                        enabled = state.isThermalAlertEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = { showThermalSettings = true }
                    )
                }
            }
        }

        // 4. The fixed 4x2 calibration preset grid mirrors the reference screen.
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nominal Tasarım Kapasitesi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOf(listOf(3000, 4000, 5000, 6000), listOf(7000, 8000, 10000, 12000)).forEach { values ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        values.forEach { capacity ->
                            CapacityPresetChip(
                                value = capacity,
                                selected = state.designCapacityMah == capacity,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setDesignCapacityMah(capacity) }
                            )
                        }
                    }
                }
                Text("mAh", style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
                state.designCapacityMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.labelSmall,
                        color = if (state.isDesignCapacitySaveError) VortixError else VortixPrimary)
                }
            }
        }

        ChargingBenchmarkCard(benchmarkViewModel)
        ChargingProfileCard(chargingProfileViewModel, profileState)

        // 7. Compact live telemetry switch. Permission flow remains unchanged.
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 18, highlighted = state.isLiveTelemetryEnabled) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassToggleRow(
                    title = "Canlı Bildirim Telemetrisi",
                    subtitle = "Bildirim çubuğunda canlı pil verisi",
                    checked = state.isLiveTelemetryEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) viewModel.setLiveTelemetryEnabled(false)
                        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingLiveTelemetryPermission = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else viewModel.setLiveTelemetryEnabled(true)
                    },
                )
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    GlassPillButton(
                        text = "Bildirim iznini ver",
                        onClick = {
                            pendingLiveTelemetryPermission = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accent = true,
                    )
                }
            }
        }

        // 8. Shizuku status is visible like the reference; advanced controls expand below it.
        GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 18) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val wakelockStatusLabel = when (state.wakelockAccessState) {
                    WakelockAccessState.READY -> "Shizuku bağlı"
                    WakelockAccessState.PERMISSION_REQUIRED -> "Shizuku izni bekleniyor"
                    WakelockAccessState.UNSUPPORTED_VERSION -> "Shizuku API güncellenmeli"
                    else -> "Shizuku bağlı değil"
                }
                GlassToggleRow(
                    title = "Gelişmiş Wakelock / Shizuku",
                    subtitle = wakelockStatusLabel,
                    checked = state.wakelockAccessState == WakelockAccessState.READY,
                    onCheckedChange = { enabled ->
                        if (enabled) viewModel.requestWakelockPermission() else viewModel.refreshWakelockAccess()
                    },
                )
                TextButton(onClick = { showAdvancedControls = !showAdvancedControls }) {
                    Text(if (showAdvancedControls) "Gelişmiş ayarları gizle" else "Gelişmiş ayarları göster", color = VortixOnSurfaceVariant)
                }
                AnimatedVisibility(visible = showAdvancedControls) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingSwitchRow(
                            title = "Arka Plan Telemetri Planı",
                            subtitle = "Gece bekleme analizi için en az 15 dakikalık örnekleme.",
                            checked = state.isMonitoringEnabled,
                            onCheckedChange = viewModel::setMonitoringEnabled
                        )
                        SettingSwitchRow(
                            title = "Eco Guard Öneri Profili",
                            subtitle = "Düşük pilde Tasarruf modunu önerir; ayarı otomatik değiştirmez.",
                            checked = state.isEcoGuardEnabled,
                            onCheckedChange = viewModel::setEcoGuardEnabled
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassPillButton(
                                "Pil Tasarrufu",
                                { safeStartActivity(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) },
                                Modifier.weight(1f),
                            )
                            GlassPillButton(
                                "Uygulama İzinleri",
                                {
                                    safeStartActivity(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    })
                                },
                                Modifier.weight(1f),
                            )
                        }
                        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VortixPrimary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdValueTile(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .glassSurface(
                shape = VortixGlassShapes.Inner,
                fill = if (enabled) VortixPrimary.copy(alpha = 0.10f) else VortixGlassColors.InnerFill,
                bright = if (enabled) VortixPrimary.copy(alpha = 0.22f) else GlassBorderHighlight,
                dim = GlassBorderSubtle,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun CapacityPresetChip(value: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .glassSurface(
                shape = VortixGlassShapes.Inner,
                fill = if (selected) VortixPrimary.copy(alpha = 0.18f) else VortixGlassColors.InnerFill,
                bright = if (selected) VortixPrimary.copy(alpha = 0.28f) else GlassBorderHighlight,
                dim = GlassBorderSubtle,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$value", style = MaterialTheme.typography.bodyMedium,
            color = if (selected) VortixPrimary else Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChargingProfileCard(
    viewModel: ChargingProfileViewModel,
    state: ChargingProfileUiState
) {
    var profileName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showComparisonDialog by remember { mutableStateOf(false) }
    val displayedProfile = state.activeProfile ?: state.profiles.firstOrNull()

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Adaptör profili kaydet") },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it.take(48) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Profil adı") },
                    placeholder = { Text("Orijinal 65W / Araç Şarjı") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startProfile(profileName.trim())
                        profileName = ""
                        showSaveDialog = false
                    },
                    enabled = profileName.isNotBlank() && state.isCharging && !state.isSaving
                ) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Vazgeç") } }
        )
    }

    if (showComparisonDialog) {
        AlertDialog(
            onDismissRequest = { showComparisonDialog = false },
            title = { Text("Adaptör karşılaştırması") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.profiles.take(2).forEach { profile ->
                        AdapterProfileComparisonColumn(profile, Modifier.weight(1f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showComparisonDialog = false }) { Text("Kapat") }
            }
        )
    }

    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22, highlighted = state.activeProfile != null) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Adaptör Dijital Parmak İzi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            GlassInner(modifier = Modifier.fillMaxWidth()) {
                Text("KAYITLI PROFİL", style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
                Text(
                    displayedProfile?.let { profile ->
                        val avg = profile.averagePowerWatts?.let { String.format(Locale.US, "%.0f W", it) } ?: "ölçüm sürüyor"
                        "${profile.name} · $avg"
                    } ?: "Henüz adaptör profili kaydedilmedi",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                displayedProfile?.let { profile ->
                    Text(
                        "Tepe ${profile.peakPowerWatts?.let { String.format(Locale.US, "%.1f W", it) } ?: "—"} · ΔT ${profile.temperatureDeltaCelsius?.let { String.format(Locale.US, "%+.1f°C", it) } ?: "—"} · ${profile.efficiencyGrade ?: "Skor bekleniyor"}",
                        style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant,
                    )
                }
            }
            if (state.activeProfile != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("KAYIT DEVAM EDİYOR · ${state.activeProfile.sampleCount} örnek",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = VortixPrimary)
                    GlassPillButton("Bitir", viewModel::finishProfile, accent = true)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPillButton(
                    text = if (state.isSaving) "Kaydediliyor…" else "Profili Kaydet",
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.weight(1f),
                    accent = true,
                    enabled = state.isCharging && state.activeProfile == null && !state.isSaving,
                )
                GlassPillButton(
                    text = "Kıyasla",
                    onClick = { showComparisonDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = state.profiles.size >= 2,
                )
            }
            if (!state.isCharging && state.activeProfile == null) {
                Text("Profil kaydı yalnızca cihaz şarj olurken başlatılabilir.",
                    style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VortixPrimary) }
        }
    }
}

@Composable
private fun AdapterProfileComparisonColumn(profile: ChargingProfile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .glassSurface(shape = VortixGlassShapes.Inner, fill = VortixGlassColors.InnerFill)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
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

@Composable
private fun ChargingBenchmarkCard(viewModel: ChargingBenchmarkViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 22, highlighted = state.isRunning) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Adaptör Hız Testi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("30 SN BENCHMARK · ŞARJ ADAPTÖRÜNÜ ÖLÇER",
                style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
            if (state.isRunning) {
                Text("Test sürüyor · ${state.remainingSeconds} sn kaldı", color = VortixPrimary, fontWeight = FontWeight.SemiBold)
                GlassProgress(
                    fraction = (1f - state.remainingSeconds / 30f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = VortixPrimary,
                )
            }
            GlassPillButton(
                text = if (state.isRunning) "Test devam ediyor" else "Testi Başlat",
                onClick = viewModel::startTest,
                modifier = Modifier.fillMaxWidth(),
                accent = true,
                enabled = state.isDeviceCharging && !state.isRunning,
            )
            if (!state.isDeviceCharging && !state.isRunning) {
                Text("Test yalnızca cihaz şarj olurken etkinleşir.", style = MaterialTheme.typography.labelSmall,
                    color = VortixOnSurfaceVariant)
            }
            if (state.isRunning) {
                GlassPillButton("Testi iptal et", viewModel::cancelTest, Modifier.fillMaxWidth())
            }
            state.message?.let { Text(it, color = VortixError, style = MaterialTheme.typography.bodySmall) }
            state.result?.let { result ->
                GlassInner(
                    modifier = Modifier.fillMaxWidth(),
                    fill = VortixPrimary.copy(alpha = 0.07f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(result.grade.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                            color = VortixPrimary, modifier = Modifier.weight(1f))
                        Text("${result.score}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    BenchmarkMetricLine("Tepe güç", String.format(Locale.getDefault(), "%.1f W", result.peakPowerWatts))
                    BenchmarkMetricLine("Ortalama akım", String.format(Locale.getDefault(), "%.0f mA", result.averageCurrentMilliamps))
                    BenchmarkMetricLine("Voltaj kararlılığı", result.voltageVariationPercent?.let { String.format(Locale.getDefault(), "%.2f%%", it) } ?: "Yetersiz veri")
                    BenchmarkMetricLine("Sıcaklık artışı", result.temperatureIncreaseCelsius?.let { String.format(Locale.getDefault(), "%+.1f °C", it) } ?: "Yetersiz veri")
                    Text(result.summary, style = MaterialTheme.typography.bodySmall, color = VortixOnSurfaceVariant)
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
                    GlassPillButton(
                        text = if (isBusy) "İşleniyor…" else "Durdur",
                        onClick = onForceStop,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        accent = false,
                    )
                    GlassPillButton(
                        text = "Arka Planı Sınırla",
                        onClick = onRestrictBackground,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Shizuku shell üzerinden işlem yapılır. Arka plan kısıtlaması seçilen uygulamanın bildirimlerini ve zamanlanmış işlerini etkileyebilir.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GlassPillButton("Uygulama Ayarları", onOpenSettings, Modifier.fillMaxWidth())
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
