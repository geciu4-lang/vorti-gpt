package com.vortix.app.data.battery

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import com.vortix.app.domain.model.AppUsageItem
import com.vortix.app.domain.model.DormantAppItem
import com.vortix.app.domain.repository.UsageStatsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidUsageStatsDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : UsageStatsRepository {

    override fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun loadTodayUsage(): List<AppUsageItem> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyList()

        val usageManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return@withContext emptyList()
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val networkStart = (now - LAST_24_HOURS_MILLIS).coerceAtLeast(0L)

        val usageByPackage = runCatching {
            usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            ).orEmpty()
                .asSequence()
                .filter { it.packageName != context.packageName }
                .groupBy { it.packageName }
        }.getOrDefault(emptyMap())

        val mobileBytesByUid: Map<Int, Long>?
        val wifiBytesByUid: Map<Int, Long>?
        val networkManager = context.getSystemService(NetworkStatsManager::class.java)
        if (networkManager != null) {
            mobileBytesByUid = readUidTraffic(
                networkManager,
                ConnectivityManager.TYPE_MOBILE,
                networkStart,
                now
            )
            wifiBytesByUid = readUidTraffic(
                networkManager,
                ConnectivityManager.TYPE_WIFI,
                networkStart,
                now
            )
        } else {
            mobileBytesByUid = null
            wifiBytesByUid = null
        }

        val appInfoByPackage = loadLaunchableApps().toMutableMap()
        // UsageStats can include packages without a launcher activity. Resolve them only
        // when PackageManager visibility permits; otherwise keep usage time and omit UID data.
        usageByPackage.keys.forEach { packageName ->
            if (packageName !in appInfoByPackage) {
                runCatching {
                    context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
                }.getOrNull()?.let { appInfoByPackage[packageName] = it }
            }
        }

        val packagesByUid = appInfoByPackage.values
            .distinctBy { it.packageName }
            .groupBy { it.uid }

        val candidatePackages = linkedSetOf<String>()
        usageByPackage.forEach { (packageName, records) ->
            val foreground = records.fold(0L) { total, item ->
                saturatingAdd(total, item.totalTimeInForeground.coerceAtLeast(0L))
            }
            if (foreground > 0L) candidatePackages += packageName
        }

        // Include launchable apps that generated background network traffic even when
        // UsageStats reports no foreground session for today.
        appInfoByPackage.forEach { (packageName, info) ->
            if (packageName == context.packageName) return@forEach
            val mobile = mobileBytesByUid?.get(info.uid) ?: 0L
            val wifi = wifiBytesByUid?.get(info.uid) ?: 0L
            if (mobile > 0L || wifi > 0L) candidatePackages += packageName
        }

        val items = candidatePackages.mapNotNull { packageName ->
            if (packageName == context.packageName) return@mapNotNull null
            val records = usageByPackage[packageName].orEmpty()
            val foreground = records.fold(0L) { total, item ->
                saturatingAdd(total, item.totalTimeInForeground.coerceAtLeast(0L))
            }
            val lastUsed = records.maxOfOrNull { it.lastTimeUsed } ?: 0L
            val info = appInfoByPackage[packageName]
            val uid = info?.uid
            // A successful transport query with no bucket for this UID means 0 B;
            // a null map means that transport could not be queried and must stay unknown.
            val mobile = uid?.let { appUid ->
                mobileBytesByUid?.let { totals -> totals[appUid] ?: 0L }
            }
            val wifi = uid?.let { appUid ->
                wifiBytesByUid?.let { totals -> totals[appUid] ?: 0L }
            }
            val totalTraffic = safeTotal(mobile, wifi)

            // Do not surface packages that have neither foreground use nor attributed traffic.
            if (foreground <= 0L && (totalTraffic == null || totalTraffic <= 0L)) {
                return@mapNotNull null
            }

            val label = info?.let(::loadApplicationLabel)
                ?.takeIf { it.isNotBlank() }
                ?: packageName
            val sharedUidCount = uid?.let { packagesByUid[it]?.size } ?: 1

            AppUsageItem(
                packageName = packageName,
                displayName = label,
                foregroundTimeMillis = foreground,
                lastTimeUsedMillis = lastUsed,
                mobileBytes = mobile,
                wifiBytes = wifi,
                sharedUidPackageCount = (sharedUidCount ?: 1).coerceAtLeast(1)
            )
        }

        // Show both the most-used apps and the highest-network-traffic apps. This keeps
        // background network activity visible without inventing a combined battery score.
        val mostUsed = items.asSequence()
            .filter { it.foregroundTimeMillis > 0L }
            .sortedWith(
                compareByDescending<AppUsageItem> { it.foregroundTimeMillis }
                    .thenByDescending { it.totalNetworkBytes ?: 0L }
            )
            .take(MAX_FOREGROUND_APPS)
            .toList()
        val mostNetworkActive = items.asSequence()
            .filter { (it.totalNetworkBytes ?: 0L) > 0L }
            .sortedByDescending { it.totalNetworkBytes ?: 0L }
            .take(MAX_NETWORK_APPS)
            .toList()

        (mostUsed + mostNetworkActive)
            .distinctBy { it.packageName }
            .take(MAX_RADAR_APPS)
    }

    override suspend fun loadDormantApps(minimumUnusedDays: Int): List<DormantAppItem> =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess()) return@withContext emptyList()
            val usageManager = context.getSystemService(UsageStatsManager::class.java)
                ?: return@withContext emptyList()
            val now = System.currentTimeMillis()
            val days = minimumUnusedDays.coerceIn(7, 180)
            val lookback = 90L * 24L * 60L * 60L * 1000L
            val usageByPackage = runCatching {
                usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - lookback, now)
                    .orEmpty().groupBy { it.packageName }
            }.getOrElse { emptyMap() }
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchableApps = runCatching {
                context.packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            }.getOrDefault(emptyList())
            launchableApps.asSequence()
                .mapNotNull { resolveInfo ->
                    val info = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                    if (info.packageName == context.packageName) return@mapNotNull null
                    val installed = runCatching {
                        context.packageManager.getPackageInfo(info.packageName, 0).firstInstallTime
                    }.getOrDefault(0L)
                    val lastUsed = usageByPackage[info.packageName]
                        ?.maxOfOrNull { it.lastTimeUsed }
                        ?.takeIf { it > 0L }
                    // Missing usage records are not proof that an app has never been used.
                    val daysUnused = lastUsed?.let {
                        ((now - it).coerceAtLeast(0L) / MILLIS_PER_DAY)
                    }
                    val label = runCatching {
                        context.packageManager.getApplicationLabel(info).toString()
                    }.getOrDefault(info.packageName)
                    DormantAppItem(
                        packageName = info.packageName,
                        displayName = label,
                        lastUsedEpochMillis = lastUsed,
                        firstInstalledEpochMillis = installed,
                        daysSinceLastUse = daysUnused,
                        observedAsDormant = daysUnused != null && daysUnused >= days
                    )
                }
                .filter { it.observedAsDormant }
                .distinctBy { it.packageName }
                .sortedByDescending { it.daysSinceLastUse ?: 0L }
                .take(50)
                .toList()
        }

    private fun readUidTraffic(
        manager: NetworkStatsManager,
        networkType: Int,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Map<Int, Long>? {
        val stats = runCatching {
            manager.queryDetails(networkType, null, startEpochMillis, endEpochMillis)
        }.getOrNull() ?: return null

        return try {
            val accumulator = UidTrafficAccumulator()
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                if (!stats.getNextBucket(bucket)) break
                accumulator.add(
                    uid = bucket.uid,
                    rxBytes = bucket.rxBytes,
                    txBytes = bucket.txBytes
                )
            }
            accumulator.snapshot()
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            runCatching { stats.close() }
        }
    }

    private fun loadLaunchableApps(): Map<String, ApplicationInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            context.packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.applicationInfo }
                .distinctBy { it.packageName }
                .associateBy { it.packageName }
        }.getOrDefault(emptyMap())
    }

    private fun loadApplicationLabel(info: ApplicationInfo): String =
        runCatching { context.packageManager.getApplicationLabel(info).toString() }
            .getOrDefault(info.packageName)

    private fun safeTotal(first: Long?, second: Long?): Long? {
        if (first == null && second == null) return null
        val a = first?.coerceAtLeast(0L) ?: 0L
        val b = second?.coerceAtLeast(0L) ?: 0L
        return saturatingAdd(a, b)
    }

    private fun saturatingAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val LAST_24_HOURS_MILLIS = 24L * 60L * 60L * 1000L
        private const val MAX_FOREGROUND_APPS = 8
        private const val MAX_NETWORK_APPS = 5
        private const val MAX_RADAR_APPS = 13
    }
}
