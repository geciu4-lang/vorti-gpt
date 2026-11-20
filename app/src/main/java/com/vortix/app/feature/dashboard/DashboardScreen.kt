package com.vortix.app.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortix.app.domain.model.BatteryHealth
import com.vortix.app.domain.model.BatterySnapshot
import com.vortix.app.domain.model.PowerInsightSummary
import com.vortix.app.domain.model.RecommendationPriority
import com.vortix.app.domain.analytics.PowerAdvisorEngine
import com.vortix.app.domain.model.BatteryStatus
import com.vortix.app.domain.model.PluggedSource
import com.vortix.app.feature.controls.EnergyControlsPanel
import com.vortix.app.feature.controls.EnergyControlsViewModel
import com.vortix.app.feature.insights.BatteryInsightsPanel
import com.vortix.app.feature.insights.BatteryInsightsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Spatial frosted glass palette inspired by translucent glass windows & floating menus
private val SpatialGlassPanel = Color(0x9E262B33)
private val SpatialGlassSubPanel = Color(0x88343A46)
private val SpatialGlassPopMenu = Color(0xC42C323C)
private val SpatialRimLight = Color(0x73FFFFFF)
private val SpatialRimSubtle = Color(0x38FFFFFF)
private val SpatialSelectedPill = Color(0x66788296)
private val SpatialAccentMint = Color(0xFF86EFAC)
private val SpatialAccentSky = Color(0xFF93C5FD)
private val SpatialAccentWarm = Color(0xFFFDE68A)

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

    val isBadBattery = remember(snapshot, controlsState.thermalThresholdCelsius, controlsState.lowBatteryThresholdPercent) {
        val temp = snapshot?.temperatureCelsius ?: 0.0
        val lvl = snapshot?.levelPercent
        snapshot?.health == BatteryHealth.OVERHEAT ||
            temp >= controlsState.thermalThresholdCelsius.toDouble() ||
            (snapshot?.status == BatteryStatus.DISCHARGING && lvl != null && lvl <= controlsState.lowBatteryThresholdPercent)
    }
    val isChargingNow = snapshot?.status == BatteryStatus.CHARGING || snapshot?.status == BatteryStatus.FULL

    var danceVisible by remember { mutableStateOf(false) }
    var danceModePositive by remember { mutableStateOf(true) }
    var danceTriggerId by remember { mutableLongStateOf(0L) }
    var hasPlayedLaunchDance by rememberSaveable { mutableStateOf(false) }
    var wasChargingBefore by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(snapshot?.status, isBadBattery) {
        if (snapshot == null) return@LaunchedEffect
        if (!hasPlayedLaunchDance) {
            hasPlayedLaunchDance = true
            wasChargingBefore = isChargingNow
            if (isChargingNow && !isBadBattery) {
                danceModePositive = true
                danceVisible = true
                danceTriggerId = System.currentTimeMillis()
            } else if (isBadBattery) {
                danceModePositive = false
                danceVisible = true
                danceTriggerId = System.currentTimeMillis()
            }
            return@LaunchedEffect
        }
        val startedCharging = wasChargingBefore == false && isChargingNow
        wasChargingBefore = isChargingNow
        if (startedCharging && !isBadBattery) {
            danceModePositive = true
            danceVisible = true
            danceTriggerId = System.currentTimeMillis()
        } else if (isBadBattery && danceModePositive) {
            danceModePositive = false
            danceVisible = true
            danceTriggerId = System.currentTimeMillis()
        }
    }

    LaunchedEffect(danceTriggerId) {
        if (danceTriggerId == 0L) return@LaunchedEffect
        delay(1550L)
        danceVisible = false
    }

    var quickMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }

    val ambientBackdrop = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A1D24),
            Color(0xFF13161C),
            Color(0xFF0D0F14)
        )
    )

    Scaffold(
        containerColor = Color(0xFF0D0F14),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            SpatialFloatingMediaBar(
                snapshot = snapshot,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onRefresh = viewModel::refresh,
                onOpenMenu = { quickMenuExpanded = !quickMenuExpanded }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientBackdrop)
        ) {
            // Soft spatial ambient light orbs for frosted glass depth
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2893C5FD), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.18f),
                        radius = size.minDimension * 0.65f
                    ),
                    radius = size.minDimension * 0.65f,
                    center = Offset(size.width * 0.82f, size.height * 0.18f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x22D6BCFA), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.58f),
                        radius = size.minDimension * 0.70f
                    ),
                    radius = size.minDimension * 0.70f,
                    center = Offset(size.width * 0.18f, size.height * 0.58f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = innerPadding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SpatialTopHeader(
                    onRefresh = viewModel::refresh,
                    isRefreshing = uiState.isRefreshing,
                    chargeLimitEnabled = controlsState.isChargeLimitEnabled,
                    chargeTarget = controlsState.chargeTargetPercent,
                    menuExpanded = quickMenuExpanded,
                    onToggleMenu = { quickMenuExpanded = !quickMenuExpanded }
                )

                // Translucent floating action popover (inspired by the floating context menu)
                if (quickMenuExpanded) {
                    SpatialActionPopover(
                        chargeLimitEnabled = controlsState.isChargeLimitEnabled,
                        chargeTarget = controlsState.chargeTargetPercent,
                        onSetPreset80 = {
                            energyControlsViewModel.setChargeTarget(80)
                            quickMenuExpanded = false
                        },
                        onSetPreset90 = {
                            energyControlsViewModel.setChargeTarget(90)
                            quickMenuExpanded = false
                        },
                        onOpenFullControls = {
                            selectedTab = 1
                            quickMenuExpanded = false
                        },
                        onClearHistory = {
                            confirmClearHistory = true
                            quickMenuExpanded = false
                        }
                    )
                }

                if (selectedTab == 0) {
                    when {
                        snapshot != null -> {
                            SpatialSplitWorkspace(
                                snapshot = snapshot,
                                chargeLimitEnabled = controlsState.isChargeLimitEnabled,
                                chargeTarget = controlsState.chargeTargetPercent,
                                thermalAlertEnabled = controlsState.isThermalAlertEnabled,
                                thermalThreshold = controlsState.thermalThresholdCelsius,
                                onToggleChargeLimit = energyControlsViewModel::setChargeLimitEnabled,
                                onToggleThermalAlert = energyControlsViewModel::setThermalAlertEnabled,
                                onOpenControls = { selectedTab = 1 },
                                onOpenInsights = { selectedTab = 2 }
                            )
                            ChargingDanceBurstCard(
                                visible = danceVisible,
                                isChargingDance = danceModePositive,
                                snapshot = snapshot,
                                onReplay = {
                                    danceModePositive = !isBadBattery
                                    danceVisible = true
                                    danceTriggerId = System.currentTimeMillis()
                                }
                            )
                            BatteryHeroCard(
                                snapshot = snapshot,
                                chargeLimitEnabled = controlsState.isChargeLimitEnabled,
                                chargeTarget = controlsState.chargeTargetPercent,
                                onOpenControls = { selectedTab = 1 }
                            )
                            BatteryHealthDiagnosticsCard(
                                capacity = insightsState.summary?.capacity,
                                designCapacityMah = insightsState.designCapacityMah,
                                onCalibrateClick = { selectedTab = 1 }
                            )
                            VortexApexIntelligenceCard(
                                snapshot = snapshot,
                                summary = insightsState.summary,
                                dormantApps = insightsState.dormantApps,
                                networkUsage = insightsState.networkUsage,
                                ecoGuardEnabled = insightsState.ecoGuardEnabled
                            )
                            Text(
                                text = "PİL TELEMETRİSİ & SENSÖRLER",
                                style = MaterialTheme.typography.labelLarge,
                                color = SpatialAccentSky,
                                fontWeight = FontWeight.Bold
                            )
                            MetricGrid(snapshot)
                            if (uiState.recentSnapshots.size >= 2) {
                                BatteryHistoryChartCard(
                                    snapshots = uiState.recentSnapshots,
                                    onClearHistory = { confirmClearHistory = true }
                                )
                            }
                            DataIntegrityNote(snapshot)
                        }
                        uiState.isRefreshing -> LoadingPanel()
                        else -> EmptyState(onRefresh = viewModel::refresh)
                    }

                    uiState.errorMessage?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Text(
                        text = snapshot?.let { "Son ölçüm: ${formatTimestamp(it.capturedAtEpochMillis)}" }
                            ?: "Henüz ölçüm alınmadı",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (selectedTab == 1) {
                    EnergyControlsPanel(viewModel = energyControlsViewModel)
                } else {
                    BatteryInsightsPanel(viewModel = insightsViewModel)
                }
            }
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Pil geçmişi silinsin mi?") },
            text = { Text("Kaydedilmiş pil ölçümleri ve grafik geçmişi bu cihazdan silinir. Ayarlar ve değişiklik notları korunur. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    viewModel.clearHistory()
                }) { Text("Geçmişi sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun SpatialTopHeader(
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    chargeLimitEnabled: Boolean,
    chargeTarget: Int,
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = SpatialGlassPanel,
        border = BorderStroke(1.dp, SpatialRimSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VORTIX",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SpatialSelectedPill,
                        border = BorderStroke(1.dp, SpatialRimSubtle)
                    ) {
                        Text(
                            text = "7.0 DANCE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SpatialAccentSky,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = if (chargeLimitEnabled) "Hedef bildirimi açık · %$chargeTarget" else "Hedef bildirimi kapalı",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chargeLimitEnabled) SpatialAccentMint else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isRefreshing, onClick = onRefresh),
                    shape = CircleShape,
                    color = SpatialGlassSubPanel,
                    border = BorderStroke(1.dp, SpatialRimSubtle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = SpatialAccentSky
                            )
                        } else {
                            Text("↻", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleMenu),
                    shape = CircleShape,
                    color = if (menuExpanded) SpatialSelectedPill else SpatialGlassSubPanel,
                    border = BorderStroke(1.dp, if (menuExpanded) SpatialRimLight else SpatialRimSubtle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("•••", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialActionPopover(
    chargeLimitEnabled: Boolean,
    chargeTarget: Int,
    onSetPreset80: () -> Unit,
    onSetPreset90: () -> Unit,
    onOpenFullControls: () -> Unit,
    onClearHistory: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SpatialGlassPopMenu,
        border = BorderStroke(1.dp, SpatialRimLight)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "HIZLI KONTROL MENÜSÜ (MEVCUT HEDEF: %$chargeTarget)",
                style = MaterialTheme.typography.labelSmall,
                color = SpatialAccentSky,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            PopoverActionRow(
                title = "Şarj Hedefini %80 Yap",
                badge = if (chargeTarget == 80 && chargeLimitEnabled) "Aktif" else "%80",
                onClick = onSetPreset80
            )
            PopoverActionRow(
                title = "Şarj Hedefini %90 Yap",
                badge = if (chargeTarget == 90 && chargeLimitEnabled) "Aktif" else "%90",
                onClick = onSetPreset90
            )
            PopoverActionRow(
                title = "Tüm Kontrol & Radar Ayarlarını Aç",
                badge = "⚡",
                onClick = onOpenFullControls
            )
            PopoverActionRow(
                title = "Geçmiş Telemetri Kayıtlarını Temizle",
                badge = "🗑",
                onClick = onClearHistory
            )
        }
    }
}

@Composable
private fun PopoverActionRow(
    title: String,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = SpatialGlassSubPanel.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = badge,
                style = MaterialTheme.typography.labelMedium,
                color = SpatialAccentMint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SpatialSplitWorkspace(
    snapshot: BatterySnapshot,
    chargeLimitEnabled: Boolean,
    chargeTarget: Int,
    thermalAlertEnabled: Boolean,
    thermalThreshold: Int,
    onToggleChargeLimit: (Boolean) -> Unit,
    onToggleThermalAlert: (Boolean) -> Unit,
    onOpenControls: () -> Unit,
    onOpenInsights: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = SpatialGlassPanel,
        border = BorderStroke(1.dp, SpatialRimSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left translucent sidebar column (inspired by spatial sidebar list)
            Column(
                modifier = Modifier.weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MODÜLLER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                SidebarPillItem(
                    label = "Şarj Hedefi",
                    selected = chargeLimitEnabled,
                    onClick = { onToggleChargeLimit(!chargeLimitEnabled) }
                )
                SidebarPillItem(
                    label = "Isı Uyarısı",
                    selected = thermalAlertEnabled,
                    onClick = { onToggleThermalAlert(!thermalAlertEnabled) }
                )
                SidebarPillItem(
                    label = "Drain Radar",
                    selected = false,
                    onClick = onOpenInsights
                )
            }

            // Right translucent glass quick switch cards
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SpatialGlassSubPanel,
                    border = BorderStroke(1.dp, if (chargeLimitEnabled) SpatialRimLight else SpatialRimSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Şarj Hedefi",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (chargeLimitEnabled) "Hedef %$chargeTarget · ${snapshot.levelPercent?.let { "%$it" } ?: "—"}" else "Kapalı",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (chargeLimitEnabled) SpatialAccentMint else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = chargeLimitEnabled,
                            onCheckedChange = onToggleChargeLimit,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4ADE80),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0x66525866)
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SpatialGlassSubPanel,
                    border = BorderStroke(1.dp, if (thermalAlertEnabled) SpatialRimLight else SpatialRimSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Isı Uyarısı",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = snapshot.temperatureCelsius?.let {
                                    String.format(Locale.getDefault(), "%.1f°C / %d°C", it, thermalThreshold)
                                } ?: "Sınır $thermalThreshold°C",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (thermalAlertEnabled) SpatialAccentSky else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = thermalAlertEnabled,
                            onCheckedChange = onToggleThermalAlert,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF60A5FA),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0x66525866)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarPillItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) SpatialSelectedPill else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, SpatialRimSubtle) else null
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SpatialFloatingMediaBar(
    snapshot: BatterySnapshot?,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: () -> Unit
) {
    // Translucent floating capsule bar inspired by the bottom spatial dock
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = SpatialGlassPopMenu,
            border = BorderStroke(1.dp, SpatialRimLight),
            tonalElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockCircleBtn(
                        glyph = "⌂",
                        active = selectedTab == 0,
                        onClick = { onSelectTab(0) }
                    )
                    DockCircleBtn(
                        glyph = "↻",
                        active = false,
                        onClick = onRefresh
                    )
                }

                // Center pill showing live battery status & power (like the center media capsule)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSelectTab((selectedTab + 1) % 3) },
                    shape = RoundedCornerShape(24.dp),
                    color = SpatialGlassSubPanel,
                    border = BorderStroke(1.dp, SpatialRimSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = snapshot?.levelPercent?.let { "%$it" } ?: "VORTIX",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpatialAccentMint,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = snapshot?.batteryPowerMilliWatts?.let {
                                String.format(Locale.getDefault(), "%+.1fW", it / 1000.0)
                            } ?: when (selectedTab) {
                                0 -> "Kontrol"
                                1 -> "Enerji"
                                else -> "Radar"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockCircleBtn(
                        glyph = "⚡",
                        active = selectedTab == 1,
                        onClick = { onSelectTab(1) }
                    )
                    DockCircleBtn(
                        glyph = "◈",
                        active = selectedTab == 2,
                        onClick = { onSelectTab(2) }
                    )
                    DockCircleBtn(
                        glyph = "•••",
                        active = false,
                        onClick = onOpenMenu
                    )
                }
            }
        }
    }
}

@Composable
private fun DockCircleBtn(
    glyph: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) SpatialSelectedPill else Color.Transparent,
        border = if (active) BorderStroke(1.dp, SpatialRimLight) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BatteryHeroCard(
    snapshot: BatterySnapshot,
    chargeLimitEnabled: Boolean,
    chargeTarget: Int,
    onOpenControls: () -> Unit
) {
    val level = snapshot.levelPercent
    val animatedProgress by animateFloatAsState(
        targetValue = (level ?: 0) / 100f,
        animationSpec = tween(durationMillis = 650),
        label = "battery_progress"
    )
    val accentColor = when {
        snapshot.health == BatteryHealth.OVERHEAT -> MaterialTheme.colorScheme.error
        snapshot.status == BatteryStatus.CHARGING -> SpatialAccentMint
        (level ?: 100) <= 20 -> SpatialAccentWarm
        else -> SpatialAccentSky
    }
    val remainingText = snapshot.dischargePredictionMillis
        ?.takeIf { snapshot.status == BatteryStatus.DISCHARGING && it > 0L }
        ?.div(60_000L)
        ?.let { minutes ->
            val hours = minutes / 60L
            val remainder = minutes % 60L
            "Android tahmini kalan süre: ${if (hours > 0) "$hours sa $remainder dk" else "$remainder dk"}"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, SpatialRimSubtle),
        colors = CardDefaults.cardColors(containerColor = SpatialGlassPanel)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ENERJİ ÇEKİRDEĞİ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = level?.let { "%$it" } ?: "—",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "${statusLabel(snapshot.status)} · ${pluggedLabel(snapshot.pluggedSource)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    if (remainingText != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(SpatialGlassSubPanel),
                    contentAlignment = Alignment.Center
                ) {
                    if (level != null) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            strokeWidth = 9.dp,
                            color = accentColor,
                            trackColor = SpatialRimSubtle
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = level?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = snapshot.batteryPowerMilliWatts?.let {
                                String.format(Locale.getDefault(), "%+.1fW", it / 1000.0)
                            } ?: if (snapshot.status == BatteryStatus.CHARGING) "ŞARJ" else "PİL",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpatialAccentSky,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (level != null) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = accentColor,
                    trackColor = SpatialGlassSubPanel
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (chargeLimitEnabled) "Şarj hedef bildirimi %$chargeTarget aktif" else "Şarj hedef bildirimi kapalı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onOpenControls,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SpatialRimSubtle),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("Tüm Ayarlar", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BatteryHealthDiagnosticsCard(
    capacity: com.vortix.app.domain.model.BatteryCapacityEstimate?,
    designCapacityMah: Int,
    onCalibrateClick: () -> Unit
) {
    val estimatedFullMah = capacity?.estimatedFullCapacityMicroAh?.div(1000L)
    val healthPercent = capacity?.healthPercent
    val confidence = when (capacity?.confidence) {
        com.vortix.app.domain.model.EvidenceConfidence.HIGH -> "Yüksek güven"
        com.vortix.app.domain.model.EvidenceConfidence.MEDIUM -> "Orta güven"
        com.vortix.app.domain.model.EvidenceConfidence.LOW -> "Düşük güven · sağlık yüzdesi gösterilmez"
        com.vortix.app.domain.model.EvidenceConfidence.LEARNING -> "Ölçüm öğreniliyor"
        com.vortix.app.domain.model.EvidenceConfidence.INSUFFICIENT, null -> "Yeterli veri yok"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SpatialRimSubtle),
        colors = CardDefaults.cardColors(containerColor = SpatialGlassPanel)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BATARYA SAĞLIĞI & KAPASİTE ANALİZİ",
                        style = MaterialTheme.typography.labelLarge,
                        color = SpatialAccentSky,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Referans tasarım kapasitesi: ${designCapacityMah.takeIf { it > 0 }?.let { "$it mAh" } ?: "ayarlanmadı"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onCalibrateClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SpatialRimSubtle)
                ) {
                    Text("Kalibre Et", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = SpatialGlassSubPanel,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Tahmini Sağlık", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = healthPercent?.let { "%$it" } ?: "Hesaplanmıyor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                healthPercent == null -> Color.White
                                healthPercent >= 85 -> SpatialAccentMint
                                healthPercent >= 75 -> SpatialAccentWarm
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(confidence, style = MaterialTheme.typography.labelSmall, color = SpatialAccentSky)
                    }
                }
                Surface(
                    color = SpatialGlassSubPanel,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Tahmini Tam Kapasite", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = estimatedFullMah?.let { "$it mAh" } ?: "Öğreniliyor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpatialAccentSky
                        )
                    }
                }
            }
            Text(
                "Sağlık yüzdesi yalnızca orta veya yüksek kanıt güveninde gösterilir. Bu değer üretici BMS tanısı değil, ölçüm geçmişinden elde edilen tahmindir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VortexApexIntelligenceCard(
    snapshot: BatterySnapshot,
    summary: PowerInsightSummary?,
    dormantApps: List<com.vortix.app.domain.model.DormantAppItem>,
    networkUsage: com.vortix.app.domain.model.NetworkUsageSnapshot?,
    ecoGuardEnabled: Boolean
) {
    val recommendations = remember(snapshot, summary, dormantApps, networkUsage, ecoGuardEnabled) {
        PowerAdvisorEngine.evaluate(snapshot, summary, dormantApps, networkUsage, ecoGuardEnabled)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SpatialRimSubtle),
        colors = CardDefaults.cardColors(containerColor = SpatialGlassPanel)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "VORTIX POWER ADVISOR",
                style = MaterialTheme.typography.labelLarge,
                color = SpatialAccentSky,
                fontWeight = FontWeight.ExtraBold
            )
            recommendations.forEach { recommendation ->
                val accent = when (recommendation.priority) {
                    RecommendationPriority.CRITICAL, RecommendationPriority.HIGH -> MaterialTheme.colorScheme.error
                    RecommendationPriority.MEDIUM -> SpatialAccentWarm
                    RecommendationPriority.INFO -> SpatialAccentMint
                }
                Surface(
                    color = SpatialGlassSubPanel,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            recommendation.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(recommendation.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        recommendation.action?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = SpatialAccentSky)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricGrid(snapshot: BatterySnapshot) {
    val powerWatts = snapshot.batteryPowerMilliWatts?.div(1000.0)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                title = "Sıcaklık",
                value = snapshot.temperatureCelsius?.let { formatDecimal(it, "°C") } ?: "Desteklenmiyor",
                badge = snapshot.temperatureCelsius?.let {
                    when {
                        it >= 43.0 -> "Yüksek"
                        it >= 38.0 -> "Ilık"
                        else -> "İdeal"
                    }
                },
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Voltaj",
                value = snapshot.voltageMillivolts?.let { "$it mV" } ?: "Desteklenmiyor",
                badge = snapshot.voltageMillivolts?.let {
                    String.format(Locale.getDefault(), "%.2f V", it / 1000.0)
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                title = "Anlık Akım",
                value = snapshot.currentNowMicroAmps?.let { formatCurrent(it) } ?: "Desteklenmiyor",
                badge = powerWatts?.let { String.format(Locale.getDefault(), "%.1f W", it) },
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Kalan Yük",
                value = snapshot.chargeCounterMicroAh?.let { formatChargeCounter(it) } ?: "Desteklenmiyor",
                badge = "Coulomb",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                title = "Döngü Sayısı",
                value = snapshot.cycleCount?.toString() ?: "Desteklenmiyor",
                badge = "Android 14+",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Sistem Sağlığı",
                value = snapshot.health?.let(::healthLabel) ?: "Bilinmiyor",
                badge = "BMS",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(102.dp),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SpatialRimSubtle),
        colors = CardDefaults.cardColors(containerColor = SpatialGlassPanel)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = SpatialAccentSky,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BatteryHistoryChartCard(
    snapshots: List<BatterySnapshot>,
    onClearHistory: () -> Unit
) {
    val ordered = snapshots.mapNotNull { it.levelPercent }
    if (ordered.size < 2) return
    val lineColor = SpatialAccentMint

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SpatialRimSubtle),
        colors = CardDefaults.cardColors(containerColor = SpatialGlassPanel)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BATARYA GEÇMİŞİ & TREND",
                        style = MaterialTheme.typography.labelLarge,
                        color = SpatialAccentSky,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Son ${ordered.size} kayıt (%${ordered.min()} – %${ordered.max()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onClearHistory,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SpatialRimSubtle)
                ) {
                    Text("Temizle", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .padding(vertical = 4.dp)
            ) {
                val stepX = size.width / (ordered.size - 1).coerceAtLeast(1)
                val path = Path()
                ordered.forEachIndexed { idx, level ->
                    val x = idx * stepX
                    val y = size.height * (1f - (level.coerceIn(0, 100) / 100f))
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                ordered.forEachIndexed { idx, level ->
                    val x = idx * stepX
                    val y = size.height * (1f - (level.coerceIn(0, 100) / 100f))
                    drawCircle(
                        color = lineColor,
                        radius = 3.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

@Composable
private fun DataIntegrityNote(snapshot: BatterySnapshot) {
    val unsupported = buildList {
        if (snapshot.levelPercent == null) add("batarya yüzdesi")
        if (snapshot.temperatureCelsius == null) add("sıcaklık")
        if (snapshot.voltageMillivolts == null) add("voltaj")
        if (snapshot.chargeCounterMicroAh == null) add("şarj sayacı")
        if (snapshot.currentNowMicroAmps == null) add("anlık akım")
        if (snapshot.cycleCount == null) add("döngü sayısı")
    }
    Surface(
        color = SpatialGlassPanel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, SpatialRimSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "DONANIM VERİ DOĞRULUĞU",
                style = MaterialTheme.typography.labelMedium,
                color = SpatialAccentSky,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (unsupported.isEmpty()) {
                    "Tüm sensör değerleri doğrudan cihazın Android güç yöneticisinden canlı olarak okundu."
                } else {
                    "Cihaz üreticisi ${unsupported.joinToString()} sensörlerini raporlamadı. Tahmini veri üretilmedi."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = SpatialAccentSky)
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SpatialRimSubtle),
        colors = CardDefaults.cardColors(containerColor = SpatialGlassPanel)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pil verisi okunamadı", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "Cihazdan henüz bir batarya anlık görüntüsü alınamadı.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRefresh) { Text("Tekrar dene") }
        }
    }
}

private fun statusLabel(status: BatteryStatus): String = when (status) {
    BatteryStatus.CHARGING -> "Şarj oluyor"
    BatteryStatus.DISCHARGING -> "Deşarj oluyor"
    BatteryStatus.NOT_CHARGING -> "Şarj duraklatıldı"
    BatteryStatus.FULL -> "Tam dolu"
    BatteryStatus.UNKNOWN -> "Durum bilinmiyor"
}

private fun pluggedLabel(source: PluggedSource): String = when (source) {
    PluggedSource.UNKNOWN -> "Bilinmiyor"
    PluggedSource.NONE -> "Batarya"
    PluggedSource.AC -> "AC Adaptör"
    PluggedSource.USB -> "USB"
    PluggedSource.WIRELESS -> "Kablosuz"
    PluggedSource.DOCK -> "Dock"
    PluggedSource.OTHER -> "Harici Kaynak"
}

private fun healthLabel(health: BatteryHealth): String = when (health) {
    BatteryHealth.UNKNOWN -> "Bilinmiyor"
    BatteryHealth.GOOD -> "Sağlıklı"
    BatteryHealth.OVERHEAT -> "Aşırı Sıcak"
    BatteryHealth.DEAD -> "Arızalı"
    BatteryHealth.OVER_VOLTAGE -> "Aşırı Voltaj"
    BatteryHealth.UNSPECIFIED_FAILURE -> "Sistem Hatası"
    BatteryHealth.COLD -> "Çok Soğuk"
}

private fun formatDecimal(value: Double, suffix: String): String =
    String.format(Locale.getDefault(), "%.1f %s", value, suffix)

private fun formatChargeCounter(value: Long): String =
    String.format(Locale.getDefault(), "%.0f mAh", value / 1000.0)

private fun formatCurrent(value: Long): String =
    String.format(Locale.getDefault(), "%+.0f mA", value / 1000.0)

private fun formatTimestamp(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

@Composable
private fun ChargingDanceBurstCard(
    visible: Boolean,
    isChargingDance: Boolean,
    snapshot: BatterySnapshot,
    onReplay: () -> Unit
) {
    val jumpOffsetY = remember { Animatable(0f) }
    val scaleDance = remember { Animatable(1f) }
    val bodySwing = remember { Animatable(0f) }
    val armWaveAngle = remember { Animatable(0f) }
    val shadowScale = remember { Animatable(1f) }

    LaunchedEffect(visible, isChargingDance) {
        if (!visible) return@LaunchedEffect
        if (isChargingDance) {
            // Şarj sırasında açılışta 1.5 saniyelik enerjik dans: yukarı zıplama, kol sallama ve gövde ritmi
            launch {
                jumpOffsetY.animateTo(-18f, tween(210, easing = FastOutSlowInEasing))
                jumpOffsetY.animateTo(0f, tween(190, easing = FastOutSlowInEasing))
                jumpOffsetY.animateTo(-15f, tween(200, easing = FastOutSlowInEasing))
                jumpOffsetY.animateTo(0f, tween(190, easing = FastOutSlowInEasing))
                jumpOffsetY.animateTo(-10f, tween(180, easing = FastOutSlowInEasing))
                jumpOffsetY.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            }
            launch {
                bodySwing.animateTo(-10f, tween(160))
                bodySwing.animateTo(10f, tween(190))
                bodySwing.animateTo(-9f, tween(190))
                bodySwing.animateTo(9f, tween(190))
                bodySwing.animateTo(0f, tween(180))
            }
            launch {
                armWaveAngle.animateTo(-18f, tween(170))
                armWaveAngle.animateTo(18f, tween(190))
                armWaveAngle.animateTo(-16f, tween(190))
                armWaveAngle.animateTo(14f, tween(190))
                armWaveAngle.animateTo(0f, tween(180))
            }
            launch {
                scaleDance.animateTo(1.10f, tween(220))
                scaleDance.animateTo(0.96f, tween(200))
                scaleDance.animateTo(1.08f, tween(220))
                scaleDance.animateTo(1f, tween(260))
            }
            launch {
                shadowScale.animateTo(0.72f, tween(210))
                shadowScale.animateTo(1.05f, tween(190))
                shadowScale.animateTo(0.78f, tween(200))
                shadowScale.animateTo(1f, tween(220))
            }
        } else {
            // Negatif durum animasyonu: kısa tedirgin sarsıntı ve hafif sönüm
            launch {
                bodySwing.animateTo(-8f, tween(120))
                bodySwing.animateTo(8f, tween(120))
                bodySwing.animateTo(-6f, tween(120))
                bodySwing.animateTo(0f, tween(160))
            }
            launch {
                scaleDance.animateTo(0.94f, tween(240))
                scaleDance.animateTo(1f, tween(300))
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(240), initialScale = 0.86f),
        exit = fadeOut(tween(260)) + scaleOut(tween(240), targetScale = 0.88f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onReplay),
            shape = RoundedCornerShape(24.dp),
            color = SpatialGlassPanel,
            border = BorderStroke(
                1.dp,
                if (isChargingDance) SpatialAccentMint.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(84.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Ground shadow beneath the dancing battery
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                            .size(width = 44.dp, height = 10.dp)
                            .graphicsLayer {
                                scaleX = shadowScale.value
                                scaleY = shadowScale.value
                            }
                    ) {
                        drawOval(color = Color.Black.copy(alpha = 0.55f))
                    }

                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .graphicsLayer {
                                translationY = jumpOffsetY.value
                                scaleX = scaleDance.value
                                scaleY = scaleDance.value
                                rotationZ = bodySwing.value
                            }
                    ) {
                        if (isChargingDance) {
                            DancingSilverGreenBatteryCanvas(armAngleDeg = armWaveAngle.value)
                        } else {
                            StressedBatteryCanvas()
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (isChargingDance) "Şarj Dansı Başladı! ⚡" else "Pil Uyarısı / Stres",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isChargingDance) SpatialAccentMint else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (isChargingDance) {
                            "Enerji doluyor (${snapshot.levelPercent?.let { "%$it" } ?: "Aktif"}) · Tekrar izlemek için dokun."
                        } else {
                            "Yüksek sıcaklık veya düşük pil seviyesi algılandı."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DancingSilverGreenBatteryCanvas(armAngleDeg: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bodyLeft = w * 0.27f
        val bodyTop = h * 0.17f
        val bodyWidth = w * 0.46f
        val bodyHeight = h * 0.63f

        // Top positive terminal
        drawRoundRect(
            color = Color(0xFF94A3B8),
            topLeft = Offset(w * 0.41f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color(0xFF111827),
            topLeft = Offset(w * 0.41f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            style = Stroke(width = 3f)
        )

        // Silver metallic upper body
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFCBD5E1), Color(0xFFF1F5F9), Color(0xFF94A3B8))
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
        )

        // Green charge bars in lower/mid section
        val barTops = listOf(h * 0.45f, h * 0.56f, h * 0.67f)
        barTops.forEach { topY ->
            drawRoundRect(
                color = Color(0xFF4ADE80),
                topLeft = Offset(bodyLeft + 4f, topY),
                size = androidx.compose.ui.geometry.Size(bodyWidth - 8f, h * 0.09f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }

        // Bold outer battery outline
        drawRoundRect(
            color = Color(0xFF111827),
            topLeft = Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
            style = Stroke(width = 3.8f)
        )

        // Happy eyes & open smile
        drawCircle(Color(0xFF111827), radius = w * 0.048f, center = Offset(w * 0.41f, h * 0.31f))
        drawCircle(Color(0xFF111827), radius = w * 0.048f, center = Offset(w * 0.59f, h * 0.31f))
        drawCircle(Color.White, radius = w * 0.017f, center = Offset(w * 0.398f, h * 0.298f))
        drawCircle(Color.White, radius = w * 0.017f, center = Offset(w * 0.578f, h * 0.298f))
        drawCircle(Color(0xFFF87171).copy(alpha = 0.75f), radius = w * 0.036f, center = Offset(w * 0.34f, h * 0.36f))
        drawCircle(Color(0xFFF87171).copy(alpha = 0.75f), radius = w * 0.036f, center = Offset(w * 0.66f, h * 0.36f))

        val mouthPath = Path().apply {
            moveTo(w * 0.42f, h * 0.36f)
            quadraticTo(w * 0.50f, h * 0.47f, w * 0.58f, h * 0.36f)
            close()
        }
        drawPath(mouthPath, color = Color(0xFF111827))

        // Animated raised green gloved arms
        val lift = armAngleDeg * 0.35f
        val leftArm = Path().apply {
            moveTo(bodyLeft, h * 0.46f)
            quadraticTo(w * 0.14f, h * 0.36f + lift, w * 0.16f, h * 0.23f + lift)
        }
        val rightArm = Path().apply {
            moveTo(bodyLeft + bodyWidth, h * 0.46f)
            quadraticTo(w * 0.86f, h * 0.36f - lift, w * 0.84f, h * 0.23f - lift)
        }
        drawPath(leftArm, color = Color(0xFF111827), style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawPath(rightArm, color = Color(0xFF111827), style = Stroke(width = 4f, cap = StrokeCap.Round))

        drawCircle(Color(0xFF4ADE80), radius = w * 0.075f, center = Offset(w * 0.16f, h * 0.22f + lift))
        drawCircle(Color(0xFF111827), radius = w * 0.075f, center = Offset(w * 0.16f, h * 0.22f + lift), style = Stroke(width = 3f))
        drawCircle(Color(0xFF4ADE80), radius = w * 0.075f, center = Offset(w * 0.84f, h * 0.22f - lift))
        drawCircle(Color(0xFF111827), radius = w * 0.075f, center = Offset(w * 0.84f, h * 0.22f - lift), style = Stroke(width = 3f))

        // Dancing legs & green shoes
        val leftLeg = Path().apply {
            moveTo(w * 0.39f, bodyTop + bodyHeight)
            quadraticTo(w * 0.28f, h * 0.88f, w * 0.22f, h * 0.84f)
        }
        val rightLeg = Path().apply {
            moveTo(w * 0.61f, bodyTop + bodyHeight)
            quadraticTo(w * 0.72f, h * 0.88f, w * 0.78f, h * 0.84f)
        }
        drawPath(leftLeg, color = Color(0xFF111827), style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawPath(rightLeg, color = Color(0xFF111827), style = Stroke(width = 4f, cap = StrokeCap.Round))

        drawOval(
            color = Color(0xFF16A34A),
            topLeft = Offset(w * 0.13f, h * 0.80f),
            size = androidx.compose.ui.geometry.Size(w * 0.15f, h * 0.10f)
        )
        drawOval(
            color = Color(0xFF16A34A),
            topLeft = Offset(w * 0.72f, h * 0.80f),
            size = androidx.compose.ui.geometry.Size(w * 0.15f, h * 0.10f)
        )
    }
}

@Composable
private fun StressedBatteryCanvas() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bodyLeft = w * 0.27f
        val bodyTop = h * 0.17f
        val bodyWidth = w * 0.46f
        val bodyHeight = h * 0.63f

        drawRoundRect(
            color = Color(0xFF94A3B8),
            topLeft = Offset(w * 0.41f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFACC15), Color(0xFFEF4444))
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
        )
        drawRoundRect(
            color = Color(0xFF111827),
            topLeft = Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
            style = Stroke(width = 3.8f)
        )
        drawCircle(Color(0xFF111827), radius = w * 0.048f, center = Offset(w * 0.41f, h * 0.34f))
        drawCircle(Color(0xFF111827), radius = w * 0.048f, center = Offset(w * 0.59f, h * 0.34f))
        drawCircle(Color.White, radius = w * 0.017f, center = Offset(w * 0.398f, h * 0.328f))
        drawCircle(Color.White, radius = w * 0.017f, center = Offset(w * 0.578f, h * 0.328f))
        drawCircle(Color(0xFF111827), radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.53f), style = Stroke(width = 3.2f))
    }
}
