# VORTIX 7.0.1 — Build validation

## Source-level corrections in this revision

- Reconciled the Energy Controls UI/ViewModel with the DataStore repository contract: charge-target, low-battery, thermal and Eco Guard preferences now have persisted keys and bounded values.
- Added the missing recent-history and clear-history repository/DAO operations used by the dashboard and analytics. Recent samples are emitted in chronological order.
- Removed a dashboard collector for an undeclared live repository stream; the persisted Room Flow is the single history-backed source of truth.
- Connected the existing BatteryInsightsViewModel to a third Smart Health/Radar destination in the app UI and surfaced PowerAdvisor recommendations with the Eco Guard preference.
- Added low-battery notifications and made charge-target/thermal notifications honor their corresponding user toggles. Background work exits when monitoring is disabled.
- Corrected stale dashboard/test fakes for the expanded repository contracts.
- Aligned the application identity to VORTIX 7.0.1 / versionCode 11, compile SDK 37 and target SDK 36.
- Updated README and audit documents to describe the source that is actually present.

## Static checks performed for this archive

- ZIP integrity and source tree were inspected.
- Android manifest, Gradle scripts, version catalog, CI workflow and Kotlin source references were reviewed.
- All known repository implementations and test fakes were updated for the expanded interfaces.

## Not verified

- Gradle dependency resolution and Android Studio sync.
- Kotlin/Compose compilation.
- KSP, Hilt and Room generated code.
- `:app:testDebugUnitTest`.
- `:app:assembleDebug` and APK installation.
- Runtime permissions, WorkManager behavior, Doze, widget, Quick Settings tile and physical-device measurements.
- Release signing and Play distribution.

## Environment limitation

The available workspace has JDK 21 and `kotlinc`, but no installed Android SDK, Android Studio, `adb` or cached Gradle 9.6.1 distribution. The Gradle bootstrap requires downloading from `services.gradle.org`; network/DNS access is unavailable in this execution environment. Therefore no Android build or test pass is claimed.

## Reproducible verification

Run on a machine or CI runner with Android SDK Platform 37 and Build Tools 37.0.0:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

A successful CI build is necessary but not sufficient for release approval; representative physical-device checks remain mandatory.
