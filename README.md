# VORTIX 7.0.1 — Android energy intelligence candidate

VORTIX is an on-device Android battery monitoring and analysis application built with Kotlin, Jetpack Compose, Material 3, MVVM and Clean Architecture. This archive is a **working source candidate**, not a release-certified build.

## Implemented modules in this source tree

- **Energy Core:** Android BatteryManager/PowerManager sampling, Room history, dashboard, home-screen widget and Quick Settings tile.
- **Smart Health:** evidence-gated battery capacity estimation, confidence levels, design-capacity calibration, replacement baseline and charge-session analysis.
- **VORTIX Radar:** screen-on/off discharge rates, personal screen-off anomaly detection, thermal exposure, network-usage context and dormant-app observations.
- **Energy Controls:** user-configured charge-target, low-battery and thermal notifications; optional background sampling; Usage Access handoff.
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
| Version | `7.0.1` (`versionCode` 11) |

AGP 9 built-in Kotlin and the new DSL are explicitly disabled in `gradle.properties` while the project uses the pinned external Kotlin Gradle Plugin. Treat this as a compatibility bridge until the full Android build confirms the selected toolchain.

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
- Network usage is traffic context, not battery drain or mAh attribution.
- Capacity and health are estimates. Health percentage is withheld until the configured design reference and sufficient measurement confidence are available.
- Unsupported Android/device metrics remain unavailable rather than being fabricated.
- Battery history and settings are stored locally. `PACKAGE_USAGE_STATS` is an optional special access controlled by the user in Android Settings.

## Release gate

This archive has not yet passed a verified Gradle Sync, Kotlin/Compose compilation, KSP/Hilt/Room generation, JUnit execution, APK assembly, signed Release build or physical-device/OEM matrix. Do not publish it to Google Play until those checks pass on this exact source revision. See `BUILD_VALIDATION.md`, `docs/TECHNICAL_AUDIT.md`, `docs/TEST_REPORT.md` and `docs/FINALIZATION_AUDIT.md`.
