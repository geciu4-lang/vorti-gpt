# VORTIX 7.1.4 — UI reference parity & badge hotfix

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
| Version | `7.1.4` (`versionCode` 20) |

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

This 7.1.4 patch is based on the user-reported, CI- and Pixel 5/API 30-validated 7.1.2 UI integration and the subsequent 7.1.3 badge hotfix. It preserves the existing thermal, Shizuku, webhook, charger-profile, telemetry and Radar features. The three user-provided UI reference screenshots are included under `docs/ui-reference/` and used to keep the Overview, Controls and Radar layout consistent.

For 7.1.4, `BatteryAlertEvaluatorTest` contains 15 targeted cases. All 15 passed using a local JUnit4-API-compatible stub runner; a separate Kotlin/JVM smoke harness passed 6/6 alert-rule assertions. The Gradle wrapper test task was attempted, but Gradle 9.6.1 could not bootstrap because DNS resolution for `services.gradle.org` failed. Therefore Compose/Android compilation, the complete test suite, APK assembly and Firebase Test Lab for this exact 7.1.4 revision remain unverified. The user-reported 7.1.2 CI and Pixel 5/API 30 results remain the previous device-validated baseline only. Do not publish this candidate until GitHub Actions succeeds and the badge states are rechecked on device.

## VORTIX 7.1.4 — UI reference and badge corrections

- The dashboard title block explicitly uses `Modifier.weight(1f).widthIn(min = 0.dp)`; the right-side badge reserves 88–104 dp and its label uses `wrapContentWidth()`, `maxLines = 1`, `softWrap = false` and ellipsis overflow.
- Long thermal/wear text stays on one ellipsized line; the optional active-alert reason appears on a separate line below the header.
- The red `UYARI` badge is shown only for a measured battery temperature at/above the enabled thermal threshold, a discharging battery below the enabled low-battery threshold, or Android system thermal status `MODERATE` through `SHUTDOWN`. `NONE` and `LIGHT` are not warnings.
- Charge-target completion and stale OEM `BATTERY_HEALTH_OVERHEAT` flags do not independently trigger the red badge. Invalid persisted threshold values are clamped to the controls' supported ranges (36–48°C and 10–40%).
- Added two regression tests for stale threshold values and out-of-range thermal status. Existing 7.1.3 UI structure is retained; reference screenshots are bundled under `docs/ui-reference/`.
- The original `.github/workflows/build.yml`, AIDL `void destroy();` declaration, and `UsageAccessSettingsLauncher` import remain unchanged.
- Local targeted tests: 15/15 passed with a JUnit4-API-compatible stub runner; Kotlin/JVM alert smoke checks: 6/6 passed. Gradle 9.6.1 could not be downloaded in this environment because `services.gradle.org` DNS resolution failed; full Android/Compose compilation, full suite, APK and Firebase tests for this exact patch remain unverified. See `docs/UI_REFERENCE_PARITY_7.1.4.md`.


## VORTIX 7.1.5 — Dark Frosted Glass Integration

- Entegre edilen koyu obsidyen, ambient mesh ve specular kenarlı Dark Frosted Glass tasarım sistemi.
- Özet, Kontrol ve Radar sekmeleri mevcut ViewModel state ve aksiyonlarını koruyarak cam yüzeyler ve pill etkileşimleriyle yeniden stillendi.
- Webhook GET/POST seçimi ve Test Et aksiyonu sürekli görünür hâle getirildi.
- Rozet tek satır sınırlandırıldı; gerçek anomali yokken NORMAL durumu yeşil gösterilir.
- Önceki Hilt, Room/migration, Shizuku/AIDL, WorkManager ve GitHub Actions akışları korunmuştur.
- Android derlemesi ve tüm Gradle testleri teslim anında çalıştırılamadı; doğrulama durumu teknik raporda açıklanmıştır.
