package com.vortix.app.feature.insights

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.vortix.app.domain.model.EvidenceConfidence
import com.vortix.app.domain.model.WakelockAccessState
import com.vortix.app.feature.controls.EnergyControlsViewModel
import com.vortix.app.feature.controls.UsageAppRow
import com.vortix.app.platform.UsageAccessSettingsLauncher
import com.vortix.app.ui.components.GlassPillButton
import com.vortix.app.ui.components.GlassProgress
import com.vortix.app.ui.components.glassSurface
import com.vortix.app.ui.components.VortixGlassShapes
import com.vortix.app.ui.theme.VortixGlassColors
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun BatteryInsightsPanel(
    viewModel: BatteryInsightsViewModel,
    controlsViewModel: EnergyControlsViewModel,
    onOpenControls: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val controlsState by controlsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showDormantList by remember { mutableStateOf(false) }
    var showAdvancedHistory by remember { mutableStateOf(false) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val csv = pendingCsv
        if (uri != null && csv != null) {
            exportMessage = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv) }
                    ?: error("Dosya yazma akışı açılamadı")
                "Pil geçmişi CSV olarak kaydedildi."
            }.getOrElse { "CSV dışa aktarılamadı: ${it.localizedMessage ?: "bilinmeyen hata"}" }
        }
        pendingCsv = null
    }

    LaunchedEffect(Unit) {
        viewModel.refreshNetworkUsage()
        viewModel.refreshDormantApps()
        controlsViewModel.refreshUsageApps()
        controlsViewModel.refreshWakelockAccess()
    }
    DisposableEffect(lifecycleOwner, viewModel, controlsViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNetworkUsage()
                viewModel.refreshDormantApps()
                controlsViewModel.refreshUsageApps()
                controlsViewModel.refreshWakelockAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val latestNightSession = state.nightStandbySessions.maxByOrNull { it.endEpochMillis }
    val summary = state.summary
    val healthPercent = summary?.capacity?.healthPercent
    val estimatedMah = summary?.capacity?.estimatedFullCapacityMicroAh?.let { (it / 1_000.0).toInt() }
    val designMah = summary?.capacity?.designCapacityMicroAh?.let { (it / 1_000.0).toInt() }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Night standby summary.
        InsightCard(title = "Gece Bekleme Analizi") {
            Text("VAMPIRE DRAIN · SON GECE", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (latestNightSession != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "−%${latestNightSession.batteryLossPercent}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ORTALAMA", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format(Locale.getDefault(), "%.1f %%/sa", latestNightSession.lossPercentPerHour),
                            style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
                GlassPillButton(
                    text = if (showAdvancedHistory) "Ayrıntıları gizle" else "Oturum ayrıntıları",
                    onClick = { showAdvancedHistory = !showAdvancedHistory },
                )
                if (showAdvancedHistory) {
                    MetricLine("Bekleme süresi", formatDuration(latestNightSession.durationMillis))
                    MetricLine("Tüketim", latestNightSession.consumedMah?.let {
                        "${if (latestNightSession.consumedMahIsEstimated) "≈" else ""}${formatMah(it)} mAh"
                    } ?: "mAh verisi yok")
                    latestNightSession.mostActiveAppName?.let { appName ->
                        MetricLine("En etkin uygulama sinyali", appName)
                    }
                    Text("Uygulama sinyali UsageStats ön plan süresidir; uygulama başına gerçek mAh tüketimi değildir.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("—", modifier = Modifier.weight(1f), style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Light, color = Color.White)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ORTALAMA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("— %/sa", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
                Text(
                    "En az 4 saat ekran kapalı, fişten çekilmiş ve düzenli örneklenmiş bir gece oturumu henüz yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!controlsState.isMonitoringEnabled) {
                    GlassPillButton("Arka plan ölçümünü aç", onOpenControls, Modifier.fillMaxWidth(), accent = true)
                }
            }
        }

        // 2. App actions entry points, as in the reference layout.
        InsightCard(title = "Vortix Radar Uygulama Aksiyonları") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPillButton(
                    text = if (controlsState.wakelockAccessState == WakelockAccessState.READY) "Wakelock Tara" else "Shizuku İzni",
                    onClick = {
                        when (controlsState.wakelockAccessState) {
                            WakelockAccessState.READY -> controlsViewModel.refreshWakelockDiagnostics()
                            else -> controlsViewModel.requestWakelockPermission()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    accent = true,
                )
                GlassPillButton(
                    text = "Kullanım Erişimi",
                    onClick = { openUsageAccessSettings(context) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text("UYGULAMALARI DONDUR · ARKA PLANI KISITLA", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (controlsState.wakelockAccessState) {
                WakelockAccessState.READY -> Text("SHIZUKU BAĞLI · Üçüncü taraf uygulamalar için aksiyonlar hazır.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                WakelockAccessState.PERMISSION_REQUIRED -> Text("Shizuku izni bekleniyor.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                WakelockAccessState.UNSUPPORTED_VERSION -> Text("Shizuku API v11 veya üzeri gerekir.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                else -> Text("Shizuku bağlı değil. Uygulama ayarlarına yönlendirme kullanılabilir.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            controlsState.packageActionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            controlsState.wakelockErrorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }

        // 3. Smart Health capacity card.
        InsightCard(title = "Smart Health Pil Kapasitesi") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(healthPercent?.let { "%$it" } ?: "—",
                    style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Normal)
            }
            GlassProgress(
                fraction = ((healthPercent ?: 0).coerceIn(0, 100)) / 100f,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (estimatedMah != null && designMah != null) "$estimatedMah / $designMah mAh"
                else "Kapasite ölçümü öğreniliyor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (healthPercent == null) {
                Text("Yeterli ölçüm güveni oluştuğunda sağlık yüzdesi gösterilir.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 4. Charge/discharge session timeline.
        InsightCard(title = "Tüketim & Oturum Geçmişi") {
            data class SessionRow(val timestamp: Long, val title: String, val detail: String)
            val rows = buildList {
                summary?.recentChargeSessions.orEmpty().forEach { session ->
                    val gain = ((session.endLevelPercent ?: 0) - (session.startLevelPercent ?: 0)).coerceAtLeast(0)
                    add(SessionRow(session.startedAtEpochMillis, sessionDateLabel(session.startedAtEpochMillis), "+%$gain · ${formatCompactDuration(session.durationMillis)}"))
                }
                summary?.recentDischargeSessions.orEmpty().forEach { session ->
                    val loss = ((session.startLevelPercent ?: 0) - (session.endLevelPercent ?: 0)).coerceAtLeast(0)
                    add(SessionRow(session.startedAtEpochMillis, sessionDateLabel(session.startedAtEpochMillis), "−%$loss · ${formatCompactDuration(session.durationMillis)}"))
                }
            }.sortedByDescending { it.timestamp }.take(3)
            if (rows.isEmpty()) {
                Text("Henüz doğrulanmış şarj veya deşarj oturumu yok.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .glassSurface(shape = VortixGlassShapes.Inner, fill = VortixGlassColors.InnerFill)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(row.detail, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            summary?.let { value ->
                value.screenOffDrainAnomaly?.let { anomaly ->
                    Text("Ekran kapalı tüketim kişisel tabanın ${String.format(Locale.getDefault(), "%.0f%%", anomaly.increasePercent)} üzerinde.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // 5. Network usage split into two compact tiles.
        InsightCard(title = "Ağ Kullanımı") {
            val network = state.networkUsage
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetworkValueTile(
                    label = "WI-FI",
                    value = network?.wifiBytes?.let(::formatBytes) ?: "—",
                    modifier = Modifier.weight(1f)
                )
                NetworkValueTile(
                    label = "MOBİL",
                    value = network?.mobileBytes?.let(::formatBytes) ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
            if (!state.hasUsageAccess) {
                Text("Ağ ve uygulama kullanım ayrıntıları için Android Kullanım Erişimi gerekir.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 6. Dormant apps; the list opens on demand so the reference screen stays compact.
        InsightCard(title = "Kullanılmayan Uygulamalar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Son 30 günde açılmayan", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(state.dormantApps.size.toString(), style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            GlassPillButton(
                text = if (showDormantList) "Listeyi Gizle" else "Listeyi Gör",
                onClick = { showDormantList = !showDormantList },
                modifier = Modifier.fillMaxWidth(),
                accent = showDormantList,
            )
            if (showDormantList) {
                if (!state.hasUsageAccess) {
                    Text("Uygulama geçmişi için kullanım erişimini açın.", style = MaterialTheme.typography.bodySmall)
                    UsageAccessSettingsButton(context, "Kullanım erişimini aç")
                } else if (state.dormantApps.isEmpty()) {
                    Text(state.dormantAppsErrorMessage ?: "Kullanılmayan uygulama bulunamadı.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.dormantApps.take(8).forEach { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(app.daysSinceLastUse?.let { "$it gündür kullanılmadı" } ?: "Son kullanım bilinmiyor",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GlassPillButton(
                                "Ayarlar",
                                {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                        })
                                    }
                                },
                            )
                        }
                    }
                }
                if (controlsState.topApps.isNotEmpty()) {
                    Text("YÜKSEK KULLANIM · UYGULAMA AKSİYONLARI", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    controlsState.topApps.take(5).forEach { app ->
                        UsageAppRow(
                            app = app,
                            showShizukuActions = controlsState.wakelockAccessState == WakelockAccessState.READY && isThirdPartyPackage(context, app.packageName),
                            isBusy = controlsState.busyPackageName == app.packageName,
                            onForceStop = { controlsViewModel.forceStopPackage(app.packageName) },
                            onRestrictBackground = { controlsViewModel.restrictBackground(app.packageName) },
                            onOpenSettings = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                    })
                                }
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPillButton(
                        if (state.isLoadingDormantApps) "Taranıyor…" else "Listeyi Yenile",
                        viewModel::refreshDormantApps, Modifier.weight(1f), enabled = !state.isLoadingDormantApps,
                    )
                    GlassPillButton("Radar Verisini Yenile", controlsViewModel::refreshUsageApps, Modifier.weight(1f))
                }
            }
        }

        // 7. Export action.
        InsightCard(title = "CSV Dışa Aktarma") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("TÜM ÖLÇÜMLER", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    exportMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary) }
                }
                GlassPillButton(
                    text = "Dışa Aktar",
                    onClick = {
                        scope.launch {
                            val csvResult = runCatching { viewModel.loadCsv() }
                            pendingCsv = csvResult.getOrNull()
                            if (pendingCsv != null) csvLauncher.launch("vortix-battery-history.csv")
                            else exportMessage = "CSV hazırlanamadı: ${csvResult.exceptionOrNull()?.localizedMessage ?: "bilinmeyen hata"}"
                        }
                    },
                    accent = true,
                )
            }
        }
    }
}

@Composable
private fun NetworkValueTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .glassSurface(shape = VortixGlassShapes.Inner, fill = VortixGlassColors.InnerFill)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Normal)
    }
}

private fun sessionDateLabel(timestamp: Long): String {
    val session = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    return when {
        session.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
            session.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> "Bugün $time"
        session.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
            session.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR) -> "Dün $time"
        else -> java.text.SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatCompactDuration(milliseconds: Long): String {
    val minutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    return if (hours > 0L) "$hours sa" else "$minutes dk"
}

@Composable
private fun UsageAccessSettingsButton(context: Context, label: String) {
    GlassPillButton(
        text = label,
        onClick = { openUsageAccessSettings(context) },
        modifier = Modifier.fillMaxWidth(),
        accent = true,
    )
}

private fun isThirdPartyPackage(context: Context, packageName: String): Boolean {
    val info = runCatching { context.packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.MATCH_ALL) }.getOrNull()
        ?: return false
    val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    return (info.flags and systemFlags) == 0 && packageName != context.packageName
}

private fun openUsageAccessSettings(context: Context) {
    if (!UsageAccessSettingsLauncher.open(context)) {
        Toast.makeText(context, "Kullanım erişimi ayarları açılamadı.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun InsightCard(title: String, content: @Composable () -> Unit) {
    com.vortix.app.feature.dashboard.GlassContainer(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    val value = bytes.coerceAtLeast(0L)
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    return when {
        value >= gib -> String.format(Locale.getDefault(), "%.2f GB", value / gib)
        value >= mib -> String.format(Locale.getDefault(), "%.1f MB", value / mib)
        value >= kib -> String.format(Locale.getDefault(), "%.1f KB", value / kib)
        else -> "$value B"
    }
}

private fun confidenceLabel(confidence: EvidenceConfidence): String = when (confidence) {
    EvidenceConfidence.INSUFFICIENT -> "Yetersiz"
    EvidenceConfidence.LEARNING -> "Öğreniyor"
    EvidenceConfidence.LOW -> "Düşük"
    EvidenceConfidence.MEDIUM -> "Orta"
    EvidenceConfidence.HIGH -> "Yüksek"
}

private fun formatMah(value: Double): String = String.format(Locale.getDefault(), "%.0f", value)

private fun formatDuration(milliseconds: Long): String {
    val minutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        days > 0 -> "$days gün ${(hours % 24)} sa"
        hours > 0 -> "$hours sa ${minutes % 60} dk"
        else -> "$minutes dk"
    }
}

