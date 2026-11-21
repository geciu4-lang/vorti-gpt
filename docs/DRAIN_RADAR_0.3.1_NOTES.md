# VORTIX 0.3.1 — Drain Radar core

## Included

- Keeps the existing Usage Access handoff and the existing Clean Architecture boundaries.
- Combines today's foreground-use duration from `UsageStatsManager` with mobile and Wi-Fi traffic queried for the rolling last 24 hours through `NetworkStatsManager`.
- Groups received/transmitted bytes by Android UID, ignores negative/synthetic UIDs, clamps negative byte counters, and saturates on `Long` overflow.
- Includes launcher applications with observed UID network traffic even when they have no foreground use in the current day.
- Shows foreground duration, mobile bytes and Wi-Fi bytes. It deliberately avoids hidden/system-only UsageStats fields.
- Marks multiple visible packages that share a UID and warns that their network values are shared aggregates, not per-package attribution. Package visibility may limit how many shared packages can be identified.
- Performs UsageStats and network-stat queries on `Dispatchers.IO`; no new background polling or battery-draining service is introduced.

## Measurement contract

- Foreground duration and launch count: current local calendar day, based on UsageStats.
- Mobile and Wi-Fi bytes: rolling 24-hour query interval, grouped by UID. Android returns discrete hour-scale history buckets and does not interpolate partial boundary buckets, so the displayed total is not a precise to-the-second rolling counter.
- A missing transport result is shown as unavailable (`—`); a successful query with no bucket for a UID is shown as `0 B`.
- Network traffic is not a measurement of battery drain. This increment does not estimate app-level mAh, percentage of battery consumed, or a combined battery-impact score.
- Android stores network history in discrete buckets, typically hour-scale. Only buckets fully/atomically in the requested interval are returned; partial boundary buckets are not interpolated. Treat this as historical context, not a real-time counter.
- Shared UID packages can have identical network totals. The UI makes this limitation visible rather than dividing or assigning those bytes arbitrarily.

## Not included yet

- Wakelock attribution and privileged `dumpsys batterystats`/BatteryStats access.
- Shizuku integration. This requires a distinct opt-in flow, runtime capability checks and device/API-specific permission handling; it must not be silently activated.
- Force-stop, hibernation, or automatic app closure. The Settings action only opens Android's app details page.

## Verification status

- Added unit coverage for UID aggregation, invalid counters, overflow saturation, nullable transport values and shared-UID metadata.
- This source archive has not been verified by a Gradle build in the authoring environment. The Gradle 9.6.1 bootstrap request failed because `services.gradle.org` could not be resolved, and no Android SDK is configured here. Run `:app:testDebugUnitTest` and `:app:assembleDebug` with JDK 21, Android SDK Platform 37 and Build Tools 37.0.0 before promoting it to a device-test candidate.

## Platform references

- [Android `NetworkStatsManager` API](https://developer.android.com/reference/android/app/usage/NetworkStatsManager)
- [Android `BATTERY_STATS` permission](https://developer.android.com/reference/android/Manifest.permission#BATTERY_STATS)
