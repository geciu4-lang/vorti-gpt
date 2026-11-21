package com.vortix.app.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortix.app.domain.model.*
import com.vortix.app.feature.controls.*
import com.vortix.app.feature.insights.*
import com.vortix.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 26,
    highlighted: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    val bg = Brush.verticalGradient(
        listOf(
            if (highlighted) Color(0xB8233146) else GlassSurfaceTop,
            GlassSurfaceBottom
        )
    )
    val brd = Brush.verticalGradient(
        listOf(
            if (highlighted) VortixPrimary.copy(alpha = 0.65f) else GlassBorderHighlight,
            GlassBorderSubtle
        )
    )
    Box(modifier = modifier.clip(shape).background(bg).border(1.dp, brd, shape)) {
        content()
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    energyControlsViewModel: EnergyControlsViewModel,
    insightsViewModel: BatteryInsightsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val controlsState by energyControlsViewModel.uiState.collectAsStateWithLifecycle()
    val insightsState by insightsViewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = uiState.snapshot
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val onTabSelected: (Int) -> Unit = { nextTab -> selectedTab = nextTab.coerceIn(0, 2) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val isAlert = remember(
        snapshot,
        controlsState.thermalThresholdCelsius,
        controlsState.lowBatteryThresholdPercent,
        controlsState.isThermalAlertEnabled,
        controlsState.isLowBatteryAlertEnabled
    ) {
        val temp = snapshot?.temperatureCelsius ?: 0.0
        val lvl = snapshot?.levelPercent
        snapshot?.health == BatteryHealth.OVERHEAT ||
            (controlsState.isThermalAlertEnabled && temp >= controlsState.thermalThresholdCelsius.toDouble()) ||
            (controlsState.isLowBatteryAlertEnabled && snapshot?.status == BatteryStatus.DISCHARGING &&
                lvl != null && lvl <= controlsState.lowBatteryThresholdPercent)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color(0xFF131A26),
            title = { Text("Geçmişi Temizle") },
            text = { Text("Pil örnekleri silinsin mi?") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) {
                    Text("Sil", color = VortixError)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } }
        )
    }

    Scaffold(
        containerColor = VortixBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AmbientGlassDockBar(selectedTab, onTabSelected, viewModel::refresh)
        }
    ) { pad ->
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(VortixBackdropMid, VortixBackground, Color(0xFF04070B)))
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    // Apply Scaffold insets before verticalScroll so the scroll viewport
                    // ends above the dock instead of drawing beneath it.
                    .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    // Keep a scrollable tail after the last card for comfortable reach.
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AmbientTopBar(isAlert, uiState.isRefreshing)
                uiState.errorMessage?.let {
                    GlassContainer(cornerRadius = 16) {
                        Text(it, color = VortixError, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp))
                    }
                }
                key(selectedTab) {
                    when (selectedTab) {
                        0 -> when {
                        snapshot != null -> {
                            MediaInspiredHeroCard(
                                snapshot = snapshot,
                                chargeLimitEnabled = controlsState.isChargeLimitEnabled,
                                chargeTarget = controlsState.chargeTargetPercent,
                                isAlert = isAlert,
                                onControls = { onTabSelected(1) },
                                // Hero and dock both route through the same tab-state callback.
                                onRadar = { onTabSelected(2) }
                            )
                            AmbientQuickControlsRow(
                                controlsState.isChargeLimitEnabled, controlsState.chargeTargetPercent,
                                controlsState.isThermalAlertEnabled, controlsState.thermalThresholdCelsius,
                                energyControlsViewModel::setChargeLimitEnabled,
                                energyControlsViewModel::setThermalAlertEnabled
                            )
                            BatteryHealthDiagnosticsCard(insightsState.summary, insightsState.designCapacityMah) {
                                onTabSelected(1)
                            }
                            Text("CANLI SENSÖR TELEMETRİSİ", style = MaterialTheme.typography.labelLarge,
                                color = VortixOnSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                            MetricGrid(snapshot)
                            VortexApexIntelligenceCard(
                                snapshot, insightsState.summary, insightsState.dormantApps,
                                insightsState.networkUsage, insightsState.ecoGuardEnabled
                            )
                            if (uiState.recentSnapshots.size >= 2) {
                                BatteryHistoryChartCard(uiState.recentSnapshots) { confirmClear = true }
                            }
                            DataIntegrityNote(snapshot)
                        }
                        uiState.isRefreshing -> LoadingPanel()
                        else -> EmptyState { viewModel.refresh() }
                    }
                        1 -> EnergyControlsPanel(energyControlsViewModel)
                        else -> BatteryInsightsPanel(insightsViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientTopBar(isAlert: Boolean, isRefreshing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(if (isAlert) VortixError else VortixPrimary),
                contentAlignment = Alignment.Center
            ) { Text("⚡", color = VortixBackground, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            Column {
                Text("VORTIX", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                Text(if (isRefreshing) "GÜNCELLENİYOR…" else "AMBIENT ENERGY OS",
                    style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
            }
        }
        if (isAlert) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VortixError.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, VortixError.copy(alpha = 0.5f))
            ) {
                Text(
                    "UYARI",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = VortixError,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaInspiredHeroCard(
    snapshot: BatterySnapshot,
    chargeLimitEnabled: Boolean,
    chargeTarget: Int,
    isAlert: Boolean,
    onControls: () -> Unit,
    onRadar: () -> Unit
) {
    val level = (snapshot.levelPercent ?: 0).coerceIn(0, 100)
    val accent = when {
        isAlert -> VortixError
        snapshot.status == BatteryStatus.CHARGING || snapshot.status == BatteryStatus.FULL -> VortixPrimary
        else -> VortixSecondary
    }
    val watts = if (snapshot.currentNowMicroAmps != null && snapshot.voltageMillivolts != null) {
        abs((snapshot.currentNowMicroAmps / 1000.0) * (snapshot.voltageMillivolts / 1000.0)) / 1000.0
    } else null
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 28, highlighted = true) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(statusTitle(snapshot.status), style = MaterialTheme.typography.labelLarge, color = accent)
                    Text("%$level", style = MaterialTheme.typography.displayLarge, color = Color.White)
                    Text(formatPowerText(chargeLimitEnabled, chargeTarget, watts),
                        style = MaterialTheme.typography.bodyMedium, color = VortixOnSurfaceVariant)
                }
                Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 9.dp.toPx()
                        drawArc(Color.White.copy(alpha = 0.1f), -90f, 360f, false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round))
                        drawArc(accent, -90f, 360f * (level / 100f), false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(snapshot.temperatureCelsius?.let { String.format(Locale.getDefault(), "%.1f°", it) } ?: "—",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("ISI", style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onControls, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, GlassBorderHighlight)) {
                    Text("Şarj Hedefi", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = { onRadar() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.copy(alpha = 0.22f),
                        contentColor = accent
                    )
                ) {
                    Text("Tüketim Radarı", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AmbientQuickControlsRow(
    chargeLimit: Boolean, target: Int, thermalAlert: Boolean, threshold: Int,
    onCharge: (Boolean) -> Unit, onThermal: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassContainer(modifier = Modifier.weight(1f), cornerRadius = 22) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Şarj Alarmı", style = MaterialTheme.typography.titleSmall)
                    Text(if (chargeLimit) "Hedef %$target" else "Kapalı", style = MaterialTheme.typography.bodySmall,
                        color = VortixOnSurfaceVariant)
                }
                Switch(checked = chargeLimit, onCheckedChange = onCharge)
            }
        }
        GlassContainer(modifier = Modifier.weight(1f), cornerRadius = 22) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Termal Koruma", style = MaterialTheme.typography.titleSmall)
                    Text(if (thermalAlert) "$threshold °C üstü" else "Kapalı",
                        style = MaterialTheme.typography.bodySmall, color = VortixOnSurfaceVariant)
                }
                Switch(checked = thermalAlert, onCheckedChange = onThermal)
            }
        }
    }
}

@Composable
private fun BatteryHealthDiagnosticsCard(summary: PowerInsightSummary?, designMah: Int, onCalibrate: () -> Unit) {
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SMART HEALTH", style = MaterialTheme.typography.labelLarge, color = VortixSecondary)
                Text(summary?.capacity?.healthPercent?.let { "%$it Tahmini Sağlık" } ?: "Kalibrasyon Bekleniyor",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (designMah > 0) "Referans: $designMah mAh" else "Kapasite seçilmedi",
                    style = MaterialTheme.typography.bodySmall, color = VortixOnSurfaceVariant)
            }
            OutlinedButton(onClick = onCalibrate, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GlassBorderHighlight)) {
                Text("Kalibre Et", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MetricGrid(s: BatterySnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorTile("ANLIK AKIM", formatCurrentMilliAmps(s.currentNowMicroAmps), VortixPrimary, Modifier.weight(1f))
            SensorTile("GERİLİM", s.voltageMillivolts?.let { String.format(Locale.getDefault(), "%.2f V", it / 1000.0) } ?: "—", VortixSecondary, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorTile("BATARYA ISISI", s.temperatureCelsius?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "—", VortixTertiary, Modifier.weight(1f))
            SensorTile("DÖNGÜ SAYISI", s.cycleCount?.toString() ?: "—", Color.White, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SensorTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassContainer(modifier = modifier, cornerRadius = 20) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VortexApexIntelligenceCard(
    s: BatterySnapshot, sum: PowerInsightSummary?, dormant: List<DormantAppItem>,
    net: NetworkUsageSnapshot?, eco: Boolean
) {
    val advice = remember(s, sum, eco) {
        when {
            (s.temperatureCelsius ?: 0.0) >= 41.0 -> "Batarya sıcaklığı yüksek; yükü azaltmanız önerilir."
            eco && (s.levelPercent ?: 100) <= 35 && s.status == BatteryStatus.DISCHARGING ->
                "Eco Guard aktif: Pil Tasarrufu modunu açmanız önerilir."
            sum?.screenOffDrainAnomaly != null -> "Ekran kapalı bekleme tüketimi normal tabanın üzerinde."
            else -> "Telemetri dengeli; arka plan güç tüketimi normal."
        }
    }
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("POWER ADVISOR", style = MaterialTheme.typography.labelLarge, color = VortixPrimary)
                Text("${dormant.size} uyuyan uyg.", style = MaterialTheme.typography.labelSmall,
                    color = VortixOnSurfaceVariant)
            }
            Text(advice, style = MaterialTheme.typography.bodyMedium)
            net?.let {
                val mb = ((it.mobileBytes ?: 0L) + (it.wifiBytes ?: 0L)) / (1024.0 * 1024.0)
                Text(String.format(Locale.getDefault(), "24s Ağ: %.1f MB", mb),
                    style = MaterialTheme.typography.bodySmall, color = VortixOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BatteryHistoryChartCard(snaps: List<BatterySnapshot>, onClear: () -> Unit) {
    val summary = remember(snaps) { summarizeBatteryHistory(snaps) }
    val timeFormatter = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("SON ÖLÇÜMLER (${summary.points.size})", style = MaterialTheme.typography.labelLarge,
                    color = VortixOnSurfaceVariant)
                TextButton(onClick = onClear) {
                    Text("Temizle", style = MaterialTheme.typography.labelSmall, color = VortixError)
                }
            }
            if (summary.points.isEmpty()) {
                Text("Grafik için geçerli pil seviyesi ölçümü bulunamadı.",
                    style = MaterialTheme.typography.bodySmall, color = VortixOnSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BAŞLANGIÇ", style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
                        Text(
                            summary.startAtEpochMillis?.let { timeFormatter.format(Date(it)) } ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("BİTİŞ", style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
                        Text(
                            summary.endAtEpochMillis?.let { timeFormatter.format(Date(it)) } ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = VortixTertiary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, VortixTertiary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("MİN", style = MaterialTheme.typography.labelSmall, color = VortixTertiary)
                            Text("${summary.minimumLevelPercent?.let { "%$it" } ?: "—"}",
                                style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = VortixPrimary.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, VortixPrimary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("MAKS", style = MaterialTheme.typography.labelSmall, color = VortixPrimary)
                            Text("${summary.maximumLevelPercent?.let { "%$it" } ?: "—"}",
                                style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                    val points = summary.points
                    if (points.size == 1) {
                        val y = size.height * (1f - points.first().levelPercent / 100f)
                        drawCircle(VortixPrimary, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2f, y))
                    } else if (points.size >= 2) {
                        val step = size.width / (points.size - 1).coerceAtLeast(1)
                        val path = Path()
                        points.forEachIndexed { index, point ->
                            val x = index * step
                            val y = size.height * (1f - (point.levelPercent / 100f))
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, VortixPrimary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

@Composable
private fun DataIntegrityNote(s: BatterySnapshot) {
    val time = remember(s.capturedAtEpochMillis) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(s.capturedAtEpochMillis))
    }
    Text("Son okuma: $time · Android BatteryManager sensör verisi.",
        style = MaterialTheme.typography.bodySmall, color = VortixOnSurfaceVariant,
        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun LoadingPanel() {
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = VortixPrimary, modifier = Modifier.size(24.dp))
            Text("Pil telemetrisi okunuyor…")
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Henüz ölçüm yok", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRefresh) { Text("Şimdi Ölç") }
        }
    }
}

private fun statusTitle(status: BatteryStatus): String = when (status) {
    BatteryStatus.CHARGING -> "ŞARJ OLUYOR"
    BatteryStatus.FULL -> "TAM DOLU"
    BatteryStatus.DISCHARGING -> "PİLDE KULLANIM"
    BatteryStatus.NOT_CHARGING -> "DURAKLATILDI"
    BatteryStatus.UNKNOWN -> "TELEMETRİ"
}

private fun formatPowerText(limit: Boolean, target: Int, watts: Double?): String =
    "${if (limit) "Hedef %$target" else "Canlı Akış"} • ${watts?.let { String.format(Locale.getDefault(), "%.1f W", it) } ?: "— W"}"

@Composable
private fun AmbientGlassDockBar(sel: Int, onSel: (Int) -> Unit, onRef: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            // Opaque full-width dock backdrop prevents scroll content showing around the capsule.
            .background(Color(0xFF070B10))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp),
            // Fully opaque to prevent scroll content from showing through the dock.
            color = Color(0xFF080D14), border = BorderStroke(1.dp, GlassBorderHighlight), tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DockNavPill("Özet", "⌂", sel == 0, { onSel(0) }, Modifier.weight(1f)); Spacer(Modifier.width(6.dp))
                DockNavPill("Kontrol", "⚡", sel == 1, { onSel(1) }, Modifier.weight(1f)); Spacer(Modifier.width(6.dp))
                DockNavPill("Radar", "◈", sel == 2, { onSel(2) }, Modifier.weight(1f)); Spacer(Modifier.width(6.dp))
                Surface(modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onRef),
                    shape = CircleShape, color = Color(0x1AFFFFFF)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("↻", color = VortixPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DockNavPill(label: String, icon: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    Surface(modifier = modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp), color = if (selected) GlassPillActive else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, VortixPrimary.copy(alpha = 0.55f)) else null) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = if (selected) VortixPrimary else VortixOnSurfaceVariant,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else VortixOnSurfaceVariant, maxLines = 1)
        }
    }
}
