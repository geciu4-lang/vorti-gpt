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

## AGP 9 built-in Kotlin migration (follow-up revision)

The previous archive still applied `org.jetbrains.kotlin.android` while using AGP 9.3.1. That plugin is redundant/incompatible with AGP 9 built-in Kotlin and caused the reported configuration failure. This revision:

- Removes `alias(libs.plugins.kotlin.android)` from the app and root plugin blocks.
- Removes the `kotlin-android` plugin alias from `gradle/libs.versions.toml`.
- Removes the `android.builtInKotlin=false` and `android.newDsl=false` opt-out flags from `gradle.properties` so AGP 9 defaults are active.
- Keeps `org.jetbrains.kotlin.plugin.compose`; it is the Compose compiler plugin, not the removed Android Kotlin plugin. Its version remains aligned to Kotlin 2.4.21.
- Keeps KSP 2.3.12 and Hilt 2.60.1, which meet the current Android AGP 9 migration skill's minimum versions (KSP 2.3.6+, Hilt 2.59.2+).

Static follow-up checks:

- Confirmed there are no remaining `kotlin.android`, `kotlin-android`, `android.builtInKotlin`, or `android.newDsl` references in Gradle build files, properties, or the version catalog.
- Confirmed the wrapper is Gradle 9.6.1; this satisfies AGP 9.3's Gradle 9.5 minimum and remains within Kotlin 2.4.21's supported Gradle range.
- Confirmed the app continues to configure JVM target 17 using `kotlin.compilerOptions`.

Not verified in this environment: Gradle configuration, Android Studio sync, Kotlin/Compose compilation, KSP/Hilt/Room code generation, unit tests, APK assembly or device execution. The Android SDK is not installed here. The next required user-side checks remain `:app:testDebugUnitTest` and `:app:assembleDebug`.
