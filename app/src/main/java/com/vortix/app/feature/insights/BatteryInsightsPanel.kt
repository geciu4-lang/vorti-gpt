package com.vortix.app.feature.insights
import com.vortix.app.platform.UsageAccessSettingsLauncher

import android.net.Uri
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortix.app.domain.model.EvidenceConfidence
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun BatteryInsightsPanel(viewModel: BatteryInsightsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
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
    }

    // Re-check app-op permission after the user returns from Android Settings.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNetworkUsage()
                viewModel.refreshDormantApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SMART HEALTH & VORTIX RADAR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                "Pil sağlığı, tüketim eğilimleri ve cihaz kullanımına ilişkin gözlemlenebilir veriler.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.summary?.let { summary ->
            InsightCard(title = "Smart Health · Pil kapasitesi") {
                val capacity = summary.capacity
                Text(
                    capacity.healthPercent?.let { "%$it tahmini sağlık" } ?: "Sağlık yüzdesi henüz hesaplanamıyor",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    capacity.estimatedFullCapacityMicroAh?.let { "Tahmini tam kapasite: ${formatMah(it / 1000.0)} mAh" }
                        ?: "Kapasite tahmini için yeterli ölçüm birikmedi.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Kanıt düzeyi: ${confidenceLabel(capacity.confidence)} · Kabul edilen aralık: ${capacity.acceptedIntervals}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MetricLine(
                    "Tasarım kapasitesi",
                    capacity.designCapacityMicroAh?.let { "${formatMah(it / 1000.0)} mAh" } ?: "Ayarlanmadı"
                )
                MetricLine(
                    "Tahmin yayılımı",
                    capacity.relativeSpreadPercent?.let { String.format(Locale.getDefault(), "%.1f%%", it) } ?: "Yeterli veri yok"
                )
                val latestBatterySample = state.history.maxByOrNull { it.capturedAtEpochMillis }
                MetricLine("Döngü sayısı", latestBatterySample?.cycleCount?.toString() ?: "Cihaz tarafından sunulmuyor")
                MetricLine(
                    "Android pil durumu",
                    latestBatterySample?.health?.let { health ->
                        when (health.name) {
                            "GOOD" -> "Normal"
                            "OVERHEAT" -> "Aşırı sıcak"
                            "DEAD" -> "Pil arızalı"
                            "OVER_VOLTAGE" -> "Aşırı voltaj"
                            "UNSPECIFIED_FAILURE" -> "Belirtilmeyen hata"
                            "COLD" -> "Çok soğuk"
                            else -> "Bilinmiyor"
                        }
                    } ?: "Sunulmadı"
                )
                if (capacity.healthPercent == null) {
                    Text(
                        "Sağlık yüzdesi, yeterli ölçüm güveni oluşmadan gösterilmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            InsightCard(title = "Şarj geçmişi · Oturum grafiği") {
                val chargeGains = summary.recentChargeSessions
                    .asReversed()
                    .takeLast(6)
                    .mapNotNull { session ->
                        val start = session.startLevelPercent ?: return@mapNotNull null
                        val end = session.endLevelPercent ?: return@mapNotNull null
                        (end - start).takeIf { it > 0 }
                    }
                if (chargeGains.isEmpty()) {
                    Text("Grafik için henüz yeterli şarj yüzdesi ölçümü yok.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val maxGain = chargeGains.maxOrNull()?.coerceAtLeast(1) ?: 1
                    Row(
                        modifier = Modifier.fillMaxWidth().height(116.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        chargeGains.forEachIndexed { index, gain ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text("+$gain%", style = MaterialTheme.typography.labelSmall)
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(76.dp * (gain.toFloat() / maxGain.toFloat()).coerceIn(0.08f, 1f))
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        "Sütunlar kaydedilen oturumların gerçek pil yüzdesi artışını gösterir; eksik ölçümler tahmin edilmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            InsightCard(title = "VORTIX RADAR · Tüketim") {
                MetricLine("Ekran açık tüketim", summary.drain.screenOnPercentPerHour?.let { String.format(Locale.getDefault(), "%.2f%% / saat", it) } ?: "Yeterli veri yok")
                MetricLine("Ekran kapalı tüketim", summary.drain.screenOffPercentPerHour?.let { String.format(Locale.getDefault(), "%.2f%% / saat", it) } ?: "Yeterli veri yok")
                MetricLine("Tahmini kalan süre", summary.drain.estimatedRuntimeMinutesAtCurrentLevel?.let(::formatDuration) ?: "Hesaplanamıyor")
                summary.screenOffDrainAnomaly?.let { anomaly ->
                    Text(
                        "Kişisel tabana göre ekran kapalı tüketim artışı: ${String.format(Locale.getDefault(), "%.0f%%", anomaly.increasePercent)}",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            InsightCard(title = "Ölçüm güvenilirliği & termal geçmiş") {
                MetricLine("Örnek sayısı", summary.sampleCount.toString())
                MetricLine("Ölçüm kapsamı", summary.samplingCoveragePercent?.let { "%$it" } ?: "Hesaplanamıyor")
                MetricLine("En uzun ölçüm aralığı", summary.longestSamplingGapMillis?.let(::formatDuration) ?: "—")
                MetricLine("Gözlenen en yüksek sıcaklık", summary.thermalExposure.highestObservedCelsius?.let { String.format(Locale.getDefault(), "%.1f°C", it) } ?: "—")
                MetricLine("40°C üzerindeki gözlem", "${summary.thermalExposure.observedMinutesAtOrAbove40C} dk")
                Text(summary.thermalExposure.coverageNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            InsightCard(title = "Şarj oturumları") {
                if (summary.recentChargeSessions.isEmpty()) {
                    Text("Henüz yeterli şarj oturumu kaydedilmedi.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    summary.recentChargeSessions.takeLast(5).reversed().forEach { session ->
                        MetricLine(
                            "${session.startLevelPercent?.let { "%$it" } ?: "—"} → ${session.endLevelPercent?.let { "%$it" } ?: "—"}",
                            session.addedChargeMilliAmpHours?.let { "+${formatMah(it)} mAh" } ?: formatDuration(session.durationMillis)
                        )
                    }
                }
            }
        } ?: InsightCard(title = "Smart Health öğrenme aşamasında") {
            Text("Pil kapasitesi ve tüketim analizleri için ölçüm geçmişi biriktiriliyor.", style = MaterialTheme.typography.bodyMedium)
        }

        state.summary?.let { summary ->
            InsightCard(title = "Deşarj günlüğü · Gözlenen oturumlar") {
                if (summary.recentDischargeSessions.isEmpty()) {
                    Text("Henüz yeterli ve kesintisiz deşarj gözlemi yok.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    summary.recentDischargeSessions.takeLast(7).reversed().forEach { session ->
                        val levels = "${session.startLevelPercent?.let { "%$it" } ?: "—"} → ${session.endLevelPercent?.let { "%$it" } ?: "—"}"
                        MetricLine(levels, formatDuration(session.durationMillis))
                        Text(
                            "Ekran açık ${formatDuration(session.observedScreenOnMillis)} · ekran kapalı ${formatDuration(session.observedScreenOffMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        InsightCard(title = "Ağ kullanımı · Son 24 saat") {
            if (!state.hasUsageAccess) {
                Text(
                    "Uygulama bazlı ağ istatistikleri için Android Kullanım erişimi izni gerekir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.hasUsageAccess) {
                val network = state.networkUsage
                if (network != null) {
                    MetricLine("Mobil veri", network.mobileBytes?.let(::formatBytes) ?: "Kullanılamıyor")
                    MetricLine("Wi-Fi", network.wifiBytes?.let(::formatBytes) ?: "Kullanılamıyor")
                    MetricLine("Mobil zaman kovası", network.mobileHistoryBuckets?.size?.toString() ?: "Sunulmadı")
                    MetricLine("Wi-Fi zaman kovası", network.wifiHistoryBuckets?.size?.toString() ?: "Sunulmadı")
                    Text(network.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(state.networkErrorMessage ?: "Ağ özeti henüz yüklenmedi.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            UsageAccessSettingsButton(
                context = context,
                label = if (state.hasUsageAccess) "Kullanım erişimi ayarlarını yönet" else "Ağ kullanımı için izni aç"
            )
            OutlinedButton(onClick = viewModel::refreshNetworkUsage, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isLoadingNetwork) "Yükleniyor…" else "Ağ verisini yenile")
            }
        }

        InsightCard(title = "Kullanılmayan uygulamalar") {
            if (!state.hasUsageAccess) {
                Text("Uygulama kullanım geçmişi için Android Kullanım erişimi gerekir.", style = MaterialTheme.typography.bodyMedium)
            } else if (state.dormantApps.isEmpty()) {
                Text(state.dormantAppsErrorMessage ?: "14 gün veya daha uzun süredir kullanılmadığı doğrulanan uygulama bulunamadı.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.dormantApps.take(8).forEach { app ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(app.daysSinceLastUse?.let { "$it gündür kullanılmadı" } ?: "Kullanım verisi yok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                    }
                                )
                            }
                        }) { Text("Uygulama ayarları") }
                    }
                }
            }
            UsageAccessSettingsButton(
                context = context,
                label = if (state.hasUsageAccess) "Kullanım erişimi ayarlarını yönet" else "Kullanılmayan uygulamalar için izni aç"
            )
            Text("Bu liste kullanım geçmişine dayalı bir gözlemdir; VORTIX uygulamaları otomatik durdurmaz veya gerçek Android hibernation uygulamaz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = viewModel::refreshDormantApps, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isLoadingDormantApps) "Taranıyor…" else "Uygulamaları yeniden tara")
            }
        }

        InsightCard(title = "Veri dışa aktarma") {
            Text("Cihazda hâlen saklanan tüm pil örneklerini CSV dosyası olarak dışa aktar. Dosya konumunu Android'in kaydetme penceresinde sen seçersin.", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    scope.launch {
                        val csvResult = runCatching { viewModel.loadCsv() }
                        pendingCsv = csvResult.getOrNull()
                        if (pendingCsv != null) {
                            csvLauncher.launch("vortix-battery-history.csv")
                        } else {
                            exportMessage = "CSV hazırlanamadı: ${csvResult.exceptionOrNull()?.localizedMessage ?: "bilinmeyen hata"}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pil geçmişini CSV dışa aktar") }
            exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        }

        InsightCard(title = "Pil değişimi / yeni başlangıç") {
            Text(
                "Yeni pil takıldığında yeni bir öğrenme başlangıcı oluşturur. Önceki ölçüm geçmişi silinmez.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = viewModel::markBatteryReplaced, modifier = Modifier.fillMaxWidth()) {
                Text("Yeni pil takıldı olarak işaretle")
            }
            state.changeMarkers.take(5).forEach { marker ->
                MetricLine(marker.title, DateFormat.getDateTimeInstance().format(Date(marker.atEpochMillis)))
                state.changeComparisons[marker.atEpochMillis]?.let { comparison ->
                    val before = comparison.beforeScreenOffPercentPerHour?.let { String.format(Locale.getDefault(), "%.1f%%/sa", it) } ?: "—"
                    val after = comparison.afterScreenOffPercentPerHour?.let { String.format(Locale.getDefault(), "%.1f%%/sa", it) } ?: "—"
                    Text("Ekran kapalı tüketim: önce $before · sonra $after", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UsageAccessSettingsButton(context: Context, label: String) {
    OutlinedButton(
        onClick = { openUsageAccessSettings(context) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
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

private fun formatBytes(bytes: Long): String {
    val mib = bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
    return if (mib >= 1024.0) String.format(Locale.getDefault(), "%.2f GB", mib / 1024.0)
    else String.format(Locale.getDefault(), "%.1f MB", mib)
}
