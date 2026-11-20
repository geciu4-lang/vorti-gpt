# VORTIX 7.0.1 — Source review and remediation report

## Executive result

The uploaded archive was not a coherent build candidate. It mixed a newer dashboard/controls layer with older repository contracts and stale project documentation. Several compile-blocking source mismatches were visible before any Android build could be attempted. This revision reconciles those seams and connects previously orphaned analytics to the UI.

**Result: source remediation candidate. Not a verified Android build or release.**

## Critical findings found in the uploaded archive

1. `EnergyControlsViewModel` and its panel referenced charge-limit, low-battery, thermal-threshold and Eco Guard settings that did not exist in `EnergySettingsRepository` or `EnergySettingsRepositoryImpl`.
2. `DashboardViewModel` called `observeRecentSnapshots`, `observeLiveBattery` and `clearHistory`, while the repository contract did not provide those operations. The `observeLiveBattery` collector had no corresponding implementation.
3. `DashboardScreen` used several removed `BatterySnapshot` properties/extensions (`powerWatts`, estimated remaining time/capacity, health score, charger-quality and wear-risk helpers). The current domain model did not define them.
4. `MainActivity` passed `insightsViewModel` to `DashboardScreen`, but the screen signature did not accept it. The Insights ViewModel existed but was not connected to a visible destination.
5. Dashboard tests used an obsolete ViewModel constructor; the UsageStats test expected data without calling the refresh method.
6. README and audit documents described different generations (0.1.1, 1.0.0-rc1 and 7.0.x), and the app Gradle identity did not match the accepted 7.0.1 candidate.

## Changes applied

### Architecture and integration

- Reconciled the settings interface, DataStore implementation and controls ViewModel.
- Added persisted preferences and bounds for charge-target alerts, low-battery alerts, thermal alerts and Eco Guard preference.
- Added low-battery notification delivery and made all background alerts respect their individual toggles and the master monitoring switch.
- Added recent-sample observation and clear-history operations to Room DAO/repository; recent samples are presented chronologically.
- Removed the undeclared live-stream collector. Room is the persisted source of truth; a fresh hardware sample is taken on explicit refresh or scheduled worker execution.
- Connected the existing Insights ViewModel to a third app destination.
- Replaced missing dashboard model extensions with values from `BatterySnapshot` and the evidence-gated analytics engine.
- Connected `PowerAdvisorEngine` to the dashboard so its recommendations are actually surfaced.
- Added Smart Health, drain-rate, discharge-session, thermal, network-context, dormant-app and CSV-export sections to Insights.

### Accuracy, privacy and UX

- Health percentage is sourced from the analytics confidence gate; it is not recomputed using a second dashboard-only formula.
- Battery-side power uses the model's signed current × voltage derivation, not a nonexistent `powerWatts` property.
- Remaining discharge time is shown only when Android provides a discharge prediction; no unsupported charge ETA is invented.
- Removed the unused hidden-API `PowerProfile` reader and removed automatic design-capacity guessing. Users choose the nominal design capacity themselves.
- Charge-target behavior is described as notification-only; it does not stop charging hardware.
- Eco Guard now changes the Power Advisor suggestion threshold only; it does not change system settings.
- Dormant-app entries open Android App Info; VORTIX does not force-stop or hibernate apps.
- UsageStats is refreshed only while the Controls screen is active and rechecked when the user returns from Android Settings.
- Destructive history deletion now requires confirmation and preserves settings/change markers.
- Updated misleading “live stream” wording because WorkManager sampling is deferrable and not continuous.

### Project metadata and documentation

- Aligned app identity to `7.0.1`, `versionCode 11`, `compileSdk 37`, `targetSdk 36`, `minSdk 26`.
- Updated CI SDK packages and Debug artifact name.
- Rewrote README and aligned build, technical, test, finalization and APK delivery notes.
- Marked UI follow-ups honestly where analytics data exists but richer charts/workflows are not yet presented.

## Verification evidence

Passed in this workspace:

- ZIP archive integrity check on the supplied archive.
- TOML version-catalog parsing.
- XML parsing for Android manifest/resources.
- YAML parsing for GitHub Actions workflow.
- Gradle version-catalog alias checks.
- Static repository-interface / implementation and test-fake contract checks.
- Shell syntax check for the custom `gradlew` bootstrap.
- Standalone Kotlin compilation of pure domain models, `BatteryAnalyticsEngine` and `PowerAdvisorEngine`.

Not run / not verified:

- Android Studio Gradle Sync and dependency resolution.
- Full Android/Kotlin/Compose compilation.
- KSP, Hilt and Room generated-code compilation.
- JUnit execution (`:app:testDebugUnitTest`).
- Debug APK assembly or installation.
- Runtime permission flows, notification delivery, WorkManager/Doze/OEM behavior, widget/tile behavior or physical-device battery accuracy.
- Release signing and Google Play readiness.

The workspace has JDK 21 and standalone `kotlinc`, but no Android SDK, Android Studio, `adb` or cached Gradle 9.6.1 distribution. The Gradle bootstrap download is blocked by unavailable external DNS/network access. Therefore this report does not claim an Android build or test pass.

## Required next gate

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` on a machine or CI runner with Android SDK Platform 37 and Build Tools 37.0.0, then test the exact same revision on API 26+ devices and at least Pixel/AOSP-like, Samsung One UI and Xiaomi/HyperOS background policies. Do not publish until those results, Room migration validation and signed Release artifact checks are available.
