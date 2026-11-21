# VORTIX 7.1.3 — Dashboard badge UI hotfix

VORTIX is an on-device Android battery monitoring and analysis application built with Kotlin, Jetpack Compose, Material 3, MVVM and Clean Architecture. This archive is a **working source candidate**, not a release-certified build.

## Implemented modules in this source tree

- **Energy Core:** Android BatteryManager/PowerManager sampling, Room history, dashboard, home-screen widget and Quick Settings tile.
- **Smart Health:** evidence-gated battery capacity estimation, confidence levels, design-capacity calibration, replacement baseline and charge-session analysis.
- **VORTIX Radar:** screen-on/off discharge rates, personal screen-off anomaly detection, thermal exposure, device-wide network context and dormant-app observations.
- **Drain Radar 0.3.1 core:** today's foreground-use time plus mobile/Wi-Fi traffic from Android's rolling 24-hour query interval, grouped by UID. Launcher apps with observed traffic can appear even without today's foreground use. Shared UIDs are explicitly marked; network traffic is not converted into app-level mAh or battery percentages.
- **Drain Radar 0.3.2 Wakelock diagnostics:** optional Shizuku/Sui UserService reads Android Batterystats `wl`/`awl` check-in rows. The UI shows UID/tag partial-wakelock durations/counts; separate, explicitly user-triggered package actions can force-stop or restrict background execution for eligible third-party packages. No energy/mAh attribution is inferred.
- **Energy Controls:** user-configured charge-target, low-battery and thermal notifications; optional background sampling; Usage Access handoff.
- **Live Charging Telemetry:** optional ongoing, silent low-priority notification with battery-side power, current, temperature and charge-target state; Android 13+ notification permission is requested.
- **Charging Benchmark:** 30-second battery-side current/voltage/temperature sampling with a heuristic A+/A/B/C comparison score. This is not USB adapter output or cable certification.
- **Night Standby / Vampire Drain:** screen-off, unplugged discharge sessions of at least four hours, with hourly percentage loss and measured or clearly marked estimated mAh; UsageStats foreground use is shown only as a contextual signal.
- **Power Advisor:** evidence-threshold recommendations from the pure Kotlin analytics engine.
- **History:** local CSV export, change markers and a non-destructive new-battery baseline.

## Technology baseline

| Component | Version / setting |
|---|---:|
| Android Gradle Plugin | 9.3.1 |
| Gradle | 9.6.1 |
| Kotlin / Compose plugin | 2.4.21 |
| KSP | 2.3.12 |
| Hilt | 2.60.1 |
| Room | 2.8.5 |
| Compose BOM | 2026.09.00 |
| WorkManager | 2.12.0 |
| Preferences DataStore | 1.2.1 |
| Compile SDK | 37 |
| Target SDK | 36 |
| Minimum SDK | 26 |
| JVM target | 17 |
| Application ID | `com.vortix.app` |
| Shizuku API / Provider | 13.1.5 (optional advanced diagnostics) |
| Version | `7.1.3` (`versionCode` 19) |

The app module uses AGP 9 built-in Kotlin; the external `org.jetbrains.kotlin.android` plugin is not applied. The Compose compiler plugin remains pinned to the same Kotlin version.

## Build and test

Requirements: JDK 21, Android SDK Platform 37 and Build Tools 37.0.0. The checked-in `gradlew` bootstrap downloads Gradle 9.6.1 and verifies its SHA-256 before execution.

```bash
./gradlew --version
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK output, if the build succeeds:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs the unit tests and creates a Debug APK artifact on pushes to `main`/`master`, pull requests and manual workflow dispatch. A successful CI run is required before treating the source as build-verified.

## Platform and measurement boundaries

- WorkManager periodic execution has a 15-minute minimum interval and is deferrable; it is not real-time telemetry. Doze and OEM power management can delay work.
- Charge-target protection sends a notification. It does **not** disconnect charging or control charging hardware.
- Eco Guard applies an earlier battery-saver recommendation threshold in Power Advisor; it does not silently change Android system settings.
- Dormant-app detection is an observation based on UsageStats and launcher visibility. It does not force-stop, uninstall or hibernate apps.
- Network usage is traffic context, not battery drain or mAh attribution. Per-app entries use Android UID-level counters; packages sharing a UID share the same aggregate. The network query interval is 24 hours, but Android returns discrete hour-scale buckets and does not interpolate partially overlapping boundary buckets. Foreground time covers the current local day.
- Wakelock diagnostics and package actions are optional and require a running compatible Shizuku/Sui service plus explicit user approval. Availability is checked at runtime. The UserService runs a bounded `dumpsys batterystats --charged --checkin` query and returns only `wl`/`awl` rows; output size and execution time are bounded. Separate package operations validate the package identifier and execute an explicit force-stop or `RUN_ANY_IN_BACKGROUND` AppOps restriction. The app never resets batterystats.
- Wakelock duration and count are operating-system diagnostics, not battery-energy measurements. `partialTimeMillis` is apportioned time; check-in v21+ may provide actual total duration, and v22+ may provide background partial duration. Aggregate `awl` rows do not contain a lock count; the UI marks that value unavailable rather than displaying zero.
- Capacity and health are estimates. Health percentage is withheld until the configured design reference and sufficient measurement confidence are available.
- Unsupported Android/device metrics remain unavailable rather than being fabricated.
- Battery history and settings are stored locally. `PACKAGE_USAGE_STATS` is an optional special access controlled by the user in Android Settings.

## Release gate

This 7.1.3 hotfix is based on the user-reported, CI- and Pixel 5/API 30-validated 7.1.2 UI integration and retains its thermal, Shizuku, webhook, charger-profile and telemetry features. The 7.1.3 changes are limited to the dashboard header layout and alert evaluation. The full Gradle test task, Kotlin/Compose compilation, KSP/Hilt/Room generation, APK assembly and Firebase Test Lab run have **not** been completed for this exact 7.1.3 revision because the Gradle wrapper distribution could not be downloaded in the current environment. Eleven `BatteryAlertEvaluatorTest` methods compiled and passed through a local JUnit4-API-compatible stub runner; a separate Kotlin/JVM smoke harness passed 7/7 alert/thermal assertions. Do not publish this candidate until GitHub Actions succeeds and representative device tests pass. See `docs/UI_BADGE_HOTFIX_7.1.3.md`, `docs/UI_INTEGRATION_7.1.2.md`, `docs/FEATURES_7.1.0.md`, `BUILD_VALIDATION.md`, `docs/VIDEO_REMEDIATION_REPORT.md`, `docs/DRAIN_RADAR_0.3.2_WAKELOCK_NOTES.md` and `docs/SHIZUKU_SETUP.md`.


## VORTIX 7.1.3 — thermal badge layout hotfix

- The dashboard title block now uses `Modifier.weight(1f)` so it yields horizontal space to the status badge.
- Thermal/wear copy and the right-side status label use `maxLines = 1`, `softWrap = false` and ellipsis overflow; the badge cannot break into vertically stacked letters. Alert reasons render below the title row instead of squeezing the badge.
- `BatteryAlertEvaluator` trusts a valid reported battery temperature and Android thermal status when available. An OEM `BATTERY_HEALTH_OVERHEAT` flag by itself no longer leaves a red badge after the reported temperature has fallen below the configured threshold; it remains a fallback warning when temperature is unavailable. Other active alert conditions (MODERATE+ thermal status, configured temperature threshold, low-battery threshold and reached charge target) remain intact.
- Added targeted JUnit cases for 33.6°C + `THERMAL_NONE`, the missing-temperature overheat fallback and existing alert triggers.
- The source ZIP, AIDL declaration, `UsageAccessSettingsLauncher` import and `.github/workflows/build.yml` are retained. Version is 7.1.3 / versionCode 19.
- Local Kotlin/JVM alert-rule smoke checks: 7/7 passed. The Gradle wrapper test task was attempted but could not bootstrap Gradle 9.6.1 because `services.gradle.org` DNS resolution failed. Android/Compose compilation, the full JUnit suite, APK assembly and Firebase execution for this exact 7.1.3 source remain unverified. The user-reported 7.1.2 CI and Pixel 5/API 30 results are the previous validated baseline only.
