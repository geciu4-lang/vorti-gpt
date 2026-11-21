package com.vortix.app.feature.dashboard

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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortix.app.domain.model.*
import com.vortix.app.domain.analytics.BatteryAlertEvaluator
import com.vortix.app.feature.controls.*
import com.vortix.app.feature.insights.*
import com.vortix.app.ui.theme.*
import com.vortix.app.ui.components.AmbientMeshBackground
import com.vortix.app.ui.components.GlassDock
import com.vortix.app.ui.components.VortixGlassShapes
import com.vortix.app.ui.components.GlassPillButton
import com.vortix.app.ui.components.GlassPill
import com.vortix.app.ui.components.GlassToggleRow
import com.vortix.app.ui.components.ThermalRing
import com.vortix.app.ui.components.TelemetryTile
import com.vortix.app.ui.components.HeaderUi
import com.vortix.app.ui.components.VortixHeader
import com.vortix.app.ui.components.VortixStatus
import com.vortix.app.ui.components.VortixTab
import com.vortix.app.ui.components.glassSurface
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
    val fill = if (highlighted) {
        VortixGlassColors.GlassFill.copy(alpha = 0.78f)
    } else {
        VortixGlassColors.GlassFill
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = if (highlighted) 15.dp else 8.dp,
                shape = shape,
                ambientColor = if (highlighted) VortixGlassColors.Emerald.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.22f),
                spotColor = if (highlighted) VortixGlassColors.Emerald.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.18f),
            )
            .glassSurface(
                shape = shape,
                fill = fill,
                bright = if (highlighted) VortixGlassColors.Emerald.copy(alpha = 0.30f) else VortixGlassColors.SpecularBright,
                dim = VortixGlassColors.SpecularDim,
            )
    ) {
        content()
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    energyControlsViewModel: EnergyControlsViewModel,
    insightsViewModel: BatteryInsightsViewModel,
    benchmarkViewModel: ChargingBenchmarkViewModel,
    chargingProfileViewModel: ChargingProfileViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val controlsState by energyControlsViewModel.uiState.collectAsStateWithLifecycle()
    val insightsState by insightsViewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = uiState.snapshot
    val dashboardScrollState = rememberScrollState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val onTabSelected: (Int) -> Unit = { nextTab -> selectedTab = nextTab.coerceIn(0, 2) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val alertReasons = remember(
        snapshot,
        controlsState.thermalThresholdCelsius,
        controlsState.lowBatteryThresholdPercent,
        controlsState.isThermalAlertEnabled,
        controlsState.isLowBatteryAlertEnabled
    ) {
        BatteryAlertEvaluator.activeAlertReasons(
            snapshot = snapshot,
            thermalAlertEnabled = controlsState.isThermalAlertEnabled,
            thermalThresholdCelsius = controlsState.thermalThresholdCelsius,
            lowBatteryAlertEnabled = controlsState.isLowBatteryAlertEnabled,
            lowBatteryThresholdPercent = controlsState.lowBatteryThresholdPercent
        )
    }
    val isAlert = alertReasons.isNotEmpty()

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
            AmbientGlassDockBar(selectedTab, onTabSelected)
        }
    ) { pad ->
        AmbientMeshBackground(
            scrollProvider = { dashboardScrollState.value.toFloat() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    // Scaffold has already accounted for the floating dock; the header
                    // applies the status-bar inset itself.
                    .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                    .verticalScroll(dashboardScrollState)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AmbientTopBar(
                    isAlert = isAlert,
                    isStatusKnown = snapshot != null,
                    temperatureCelsius = snapshot?.temperatureCelsius,
                    thermalStatus = snapshot?.thermalStatus,
                    alertReason = alertReasons.firstOrNull(),
                )
                uiState.errorMessage?.let {
                    GlassContainer(cornerRadius = 16) {
                        Text(
                            it,
                            color = VortixError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                key(selectedTab) {
                    when (selectedTab) {
                        0 -> when {
                            snapshot != null -> {
                                MediaInspiredHeroCard(
                                    snapshot = snapshot,
                                    isAlert = isAlert,
                                    onControls = { onTabSelected(1) },
                                    onRadar = { onTabSelected(2) },
                                )
                                AmbientQuickControlsCard(
                                    chargeLimit = controlsState.isChargeLimitEnabled,
                                    target = controlsState.chargeTargetPercent,
                                    thermalAlert = controlsState.isThermalAlertEnabled,
                                    threshold = controlsState.thermalThresholdCelsius,
                                    designCapacityMah = insightsState.designCapacityMah,
                                    healthPercent = insightsState.summary?.capacity?.healthPercent,
                                    onCharge = energyControlsViewModel::setChargeLimitEnabled,
                                    onThermal = energyControlsViewModel::setThermalAlertEnabled,
                                    onCalibrate = { onTabSelected(1) },
                                )
                                MetricGrid(snapshot)
                                if (uiState.recentSnapshots.size >= 2) {
                                    BatteryHistoryChartCard(uiState.recentSnapshots) { confirmClear = true }
                                }
                            }
                            uiState.isRefreshing -> LoadingPanel()
                            else -> EmptyState { viewModel.refresh() }
                        }
                        1 -> EnergyControlsPanel(energyControlsViewModel, benchmarkViewModel, chargingProfileViewModel)
                        else -> BatteryInsightsPanel(
                            insightsViewModel,
                            energyControlsViewModel,
                            onOpenControls = { onTabSelected(1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientTopBar(
    isAlert: Boolean,
    isStatusKnown: Boolean,
    temperatureCelsius: Double?,
    thermalStatus: Int?,
    alertReason: String?
) {
    val wear = com.vortix.app.domain.analytics.BatteryThermalWearAnalyzer.assess(temperatureCelsius)
    val thermalLabel = com.vortix.app.domain.analytics.SystemThermalStatus.label(thermalStatus)
    val thermalSummary = if (!isStatusKnown) {
        "TERMAL VERİ BEKLENİYOR"
    } else {
        val wearText = wear?.let { String.format(Locale.US, "×%.1f", it.multiplier) } ?: "—"
        "TERMAL $thermalLabel · AŞINMA $wearText"
    }
    VortixHeader(
        header = HeaderUi(
            thermalSummary = thermalSummary,
            status = when {
                !isStatusKnown -> VortixStatus.Measuring
                isAlert -> VortixStatus.Warning
                else -> VortixStatus.Normal
            },
            alertReason = alertReason,
        )
    )
}

@Composable
private fun MediaInspiredHeroCard(
    snapshot: BatterySnapshot,
    isAlert: Boolean,
    onControls: () -> Unit,
    onRadar: () -> Unit
) {
    val level = snapshot.levelPercent?.coerceIn(0, 100)
    val currentMicroAmps = snapshot.currentNowMicroAmps
    val temperatureCelsius = snapshot.temperatureCelsius?.takeIf { it.isFinite() }
    val watts = if (currentMicroAmps != null && snapshot.voltageMillivolts != null) {
        abs((currentMicroAmps / 1000.0) * (snapshot.voltageMillivolts / 1000.0)) / 1000.0
    } else null
    val currentAmps = currentMicroAmps?.let { raw ->
        val magnitude = abs(raw) / 1_000_000.0
        val sign = if (snapshot.status == BatteryStatus.CHARGING || snapshot.status == BatteryStatus.FULL) "+" else if (magnitude == 0.0) "" else "−"
        sign + String.format(Locale.getDefault(), "%.1f A", magnitude)
    } ?: "—"

    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 28, highlighted = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("PİL", style = MaterialTheme.typography.labelLarge, color = VortixOnSurfaceVariant)
                    Text(
                        level?.let { "%$it" } ?: "—",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 58.sp,
                        lineHeight = 62.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Light
                    )
                    Text(statusTitle(snapshot.status), style = MaterialTheme.typography.labelLarge, color = VortixOnSurfaceVariant)
                }
                if (temperatureCelsius != null) {
                    ThermalRing(
                        tempC = temperatureCelsius.toFloat(),
                        modifier = Modifier.size(92.dp),
                        size = 92.dp,
                    )
                } else {
                    Box(
                        Modifier.size(92.dp)
                            .glassSurface(shape = CircleShape, fill = Color.White.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("—", color = VortixOnSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .glassSurface(shape = VortixGlassShapes.Inner, fill = VortixGlassColors.InnerFill)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("GÜÇ", style = MaterialTheme.typography.labelMedium, color = VortixOnSurfaceVariant)
                    Text(watts?.let { String.format(Locale.getDefault(), "%.1f W", it) } ?: "— W",
                        style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Normal)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AKIM", style = MaterialTheme.typography.labelMedium, color = VortixOnSurfaceVariant)
                    Text(currentAmps, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Normal)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPillButton("Şarj Hedefi", onControls, Modifier.weight(1f), accent = true)
                GlassPillButton("Tüketim Radarı", onRadar, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AmbientQuickControlsCard(
    chargeLimit: Boolean,
    target: Int,
    thermalAlert: Boolean,
    threshold: Int,
    designCapacityMah: Int,
    healthPercent: Int?,
    onCharge: (Boolean) -> Unit,
    onThermal: (Boolean) -> Unit,
    onCalibrate: () -> Unit
) {
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hızlı Kontroller", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            QuickControlRow("Şarj Alarmı", if (chargeLimit) "Hedef %$target" else "Kapalı", chargeLimit, onCharge)
            QuickControlRow("Termal Koruma", if (thermalAlert) "$threshold°C" else "Kapalı", thermalAlert, onThermal)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .glassSurface(shape = VortixGlassShapes.Inner, fill = VortixGlassColors.InnerFill)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("KALİBRASYON · REFERANS KAPASİTE", style = MaterialTheme.typography.labelMedium,
                        color = VortixOnSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${if (designCapacityMah > 0) "$designCapacityMah mAh" else "Kapasite seçilmedi"} · Smart Health ${healthPercent?.let { "%$it" } ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassPillButton("Kalibre Et", onCalibrate, accent = false)
            }
        }
    }
}

@Composable
private fun QuickControlRow(label: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlassToggleRow(
        title = label,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun MetricGrid(s: BatterySnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorTile("ANLIK AKIM", formatCurrentMilliAmps(s.currentNowMicroAmps), Modifier.weight(1f))
            SensorTile("GERİLİM", s.voltageMillivolts?.let { String.format(Locale.getDefault(), "%.2f V", it / 1000.0) } ?: "—", Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorTile("BATARYA ISISI", s.temperatureCelsius?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "—", Modifier.weight(1f))
            SensorTile("DÖNGÜ SAYISI", s.cycleCount?.toString() ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SensorTile(label: String, value: String, modifier: Modifier = Modifier) {
    TelemetryTile(label, value, modifier = modifier)
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BatteryHistoryChartCard(snaps: List<BatterySnapshot>, onClear: () -> Unit) {
    val summary = remember(snaps) { summarizeBatteryHistory(snaps) }
    val points = summary.points
    GlassContainer(modifier = Modifier.fillMaxWidth(), cornerRadius = 24) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Son Ölçümler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) {
                    Text("Temizle", style = MaterialTheme.typography.labelSmall, color = VortixOnSurfaceVariant)
                }
            }
            if (points.isEmpty()) {
                Text("Grafik için geçerli pil seviyesi ölçümü bulunamadı.",
                    style = MaterialTheme.typography.bodySmall, color = VortixOnSurfaceVariant)
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(112.dp)) {
                    if (points.size == 1) {
                        val y = size.height * (1f - points.first().levelPercent / 100f)
                        drawCircle(VortixPrimary, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2f, y))
                    } else {
                        val step = size.width / (points.size - 1).coerceAtLeast(1)
                        val linePath = Path()
                        val fillPath = Path()
                        points.forEachIndexed { index, point ->
                            val x = index * step
                            val y = size.height * (1f - (point.levelPercent / 100f))
                            if (index == 0) {
                                linePath.moveTo(x, y)
                                fillPath.moveTo(x, size.height)
                                fillPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }
                        fillPath.lineTo(size.width, size.height)
                        fillPath.close()
                        drawPath(
                            fillPath,
                            Brush.verticalGradient(listOf(VortixPrimary.copy(alpha = 0.30f), VortixPrimary.copy(alpha = 0.01f)))
                        )
                        drawPath(linePath, VortixPrimary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                val startLevel = points.first().levelPercent
                val endLevel = points.last().levelPercent
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    HistoryStatPill("BAŞLANGIÇ %$startLevel")
                    HistoryStatPill("MİN %${summary.minimumLevelPercent ?: startLevel}")
                    HistoryStatPill("MAKS %${summary.maximumLevelPercent ?: endLevel}")
                    HistoryStatPill("BİTİŞ %$endLevel")
                }
            }
        }
    }
}

@Composable
private fun HistoryStatPill(text: String) {
    GlassPill(text)
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
private fun AmbientGlassDockBar(sel: Int, onSel: (Int) -> Unit) {
    val selected = when (sel.coerceIn(0, 2)) {
        0 -> VortixTab.Overview
        1 -> VortixTab.Controls
        else -> VortixTab.Insights
    }
    GlassDock(
        selected = selected,
        onSelect = { onSel(it.ordinal) },
        modifier = Modifier.fillMaxWidth(),
    )
}

