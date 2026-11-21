# VORTIX 7.0.1 — Technical audit

**Status: source-level remediation applied; Android build and device behavior remain uncertified.**

## Architecture

- Kotlin, Jetpack Compose, Material 3.
- MVVM presentation and Clean Architecture domain/data boundaries.
- Hilt dependency injection.
- Room for battery samples; Preferences DataStore for controls and baseline markers.
- WorkManager for deferrable periodic sampling.

## Remediation performed

1. Restored the missing EnergySettingsRepository contract and DataStore persistence for charge limit, low-battery alerts/threshold, thermal alerts/threshold, alert latches and Eco Guard preference.
2. Added DAO/repository recent-sample observation and explicit history clearing, used by dashboard and chart.
3. Removed the non-existent live battery stream from DashboardViewModel; Room remains the source of truth for persisted samples, with explicit refresh for a new sensor read.
4. Connected the previously orphaned BatteryInsightsViewModel to a Smart Health/Radar UI destination.
5. Implemented low-battery notification delivery, user-toggle gating and configurable thermal threshold handling in the worker.
6. Changed Eco Guard into an earlier Power Advisor battery-saver suggestion threshold; it does not modify system settings.
7. Updated test fakes for repository/DAO contract changes.

## Measurement and platform limits

- BatteryManager properties are device-dependent; null/unavailable fields must remain unavailable.
- Capacity is inferred from paired charge-counter and state-of-charge changes with plausibility and evidence gates; it is not a laboratory health test.
- Android's generic third-party APIs do not provide a universal hardware charge cutoff. Charge-target behavior is notification-only.
- WorkManager is deferrable and cannot guarantee exact 15-minute sampling.
- UsageStats and NetworkStats require user-controlled Usage Access; network traffic is not mAh attribution.
- Dormant-app observations do not imply force-stop, uninstall or true hibernation capability.

## Remaining high-risk validation

- Full Android build, KSP/Hilt/Room generation and all unit tests.
- Room v1→v2 migration against a real pre-existing database and generated schema.
- Worker notification latches, permission-denied behavior, process death, reboot, Doze and OEM battery restrictions.
- Battery reporting against Android system settings across API 26+ and multiple OEMs.
- Widget and Quick Settings Tile update/launch behavior.
- Release build signing, artifact integrity and same-revision CI.

**Release decision: NOT APPROVED.** Source fixes do not replace executable build, test and device evidence.

## 7.0.3 — Drain Radar 0.3.1 incremental review

- Reused the existing `AndroidUsageStatsDataSource` / `UsageStatsRepository` / `EnergyControlsViewModel` flow; no new Hilt binding, database table, worker or permission was added.
- Combined current-day foreground usage with mobile/Wi-Fi UID traffic from a rolling 24-hour query interval. Network details are queried on `Dispatchers.IO`.
- UID byte aggregation guards negative counters and `Long` overflow. A successful query with no matching UID bucket is represented as zero; a failed/unavailable transport remains `null`.
- Shared visible package UIDs are explicitly marked in the UI; their network totals are not divided or falsely attributed to individual packages.
- `UsageStats.appLaunchCount` was deliberately excluded because it is not a public third-party SDK API.
- Historical network buckets are hour-scale and do not interpolate partial boundary buckets. They are not live traffic or battery measurements.
- Shizuku/wakelock diagnostics are not part of this increment; a later opt-in feature requires capability and permission checks before use.

**Verification:** pure Kotlin smoke checks passed for the accumulator and domain model. Full Gradle tests, Android compile, APK assembly and device validation were not run because the Gradle distribution host could not be resolved and no Android SDK is installed in the authoring environment.
