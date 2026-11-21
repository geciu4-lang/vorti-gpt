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

## VORTIX 7.0.3 — Drain Radar 0.3.1 update

- The source update adds UID-grouped mobile/Wi-Fi traffic to the existing UsageStats-backed radar and preserves the existing `UsageStatsRepository` Hilt binding.
- Foreground duration is current-local-day UsageStats data. Network traffic comes from a rolling 24-hour `NetworkStatsManager` query and Android hour-scale history buckets; partial boundary buckets may be omitted.
- Shared visible package UIDs are flagged; their network bytes are shared totals and are not split between packages.
- No app-level mAh estimate, battery-drain ranking, wakelock claim, force-stop or hibernation action is introduced.
- Pure Kotlin smoke checks for UID byte aggregation, null-vs-zero network totals, shared UID marking and overflow saturation passed with the local Kotlin compiler.
- The full Gradle test task was attempted but could not start: the Gradle bootstrap could not resolve `services.gradle.org`. The workspace has JDK 21 and `kotlinc`, but no Android SDK, Android Studio, `adb` or cached Gradle distribution.

Still not verified for this exact 7.0.3 source: Android/Kotlin/Compose compilation, KSP/Hilt/Room generation, the complete JUnit suite, APK assembly, API 26–37 behavior or physical-device/OEM behavior. The source is a build candidate, not a certified release.


## VORTIX 7.0.4 — Drain Radar 0.3.2 Wakelock diagnostics (candidate)

### Source changes

- Added an optional Shizuku/Sui integration using the UserService + AIDL pattern.
- The user-service executes only `/system/bin/dumpsys -t 15 batterystats --charged --checkin`, filters `wl`/`awl` rows, caps the Binder payload, and returns read-only data.
- Added a version-aware parser for check-in v18, v19–20, v21 and v22+ rows, including quoted tags, UID/tag aggregation and aggregate fallback.
- Added UI states for missing Shizuku, old Shizuku version, permission required, ready, loading and failure. The normal UsageStats/NetworkStats radar remains usable without Shizuku.
- Added ViewModel tests for the ready and unavailable paths and parser tests for supported row formats.
- Preserved `.github/workflows/build.yml` in this candidate and the reported `76.dp * Float` correction in `BatteryInsightsPanel.kt`.

### Checks in this workspace

- Pure Kotlin parser tests: all 8 `BatteryStatsWakelockParserTest` cases passed with local `kotlinc` (JDK 21), including Shizuku transport row filtering, v18, v19–20, v21, v22+, quoted tags, duplicate aggregation and aggregate fallback.
- Kotlin syntax/API-shape checks: Shizuku UserService and repository compiled locally against minimal Android/Shizuku test stubs; this catches Kotlin syntax and the modeled API contract, not Android SDK or generated AIDL integration.
- Source inspection: reviewed AIDL, manifest provider declaration, dependency coordinates, Hilt binding, ViewModel injection, permission-state handling, output bounds and read-only command.
- Gradle/JUnit/Android assembly: not executed. This workspace has no Android SDK or installed/cached Gradle distribution and cannot resolve `services.gradle.org`.
- Shizuku runtime check: not executed; requires a real device with Shizuku/Sui set up and permission approved.

### CI baseline caveat

The available 7.0.3 archive contained `.github/workflows/android-ci.yml`, not the user-reported `.github/workflows/build.yml`, and carried the pre-fix `Float * Dp` expression. This 7.0.4 candidate reconstructs a single `.github/workflows/build.yml` from the existing workflow and retains the reported Dp correction. The exact workflow file and repository state from the user's successful run `44911935628` were not supplied in this workspace; compare/merge this candidate with that canonical GitHub branch before replacing the user's repository.

### User-reported baseline evidence (7.0.3 only)

The user reported GitHub Actions run `44911935628` passed `:app:testDebugUnitTest` and `:app:assembleDebug`, and Firebase Test Lab Pixel 5/API 30 passed the Summary, Controls and Radar flows with safe Usage Access denial behavior. These results apply to the user's 7.0.3 repository revision, not this 7.0.4 Shizuku candidate.

### Required validation for this candidate

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

On a representative Android 11/API 30 device and a recent Android release, test Shizuku unavailable, binder missing/reconnected, permission denied/granted, API version too old, empty stats, v18/v19/v21/v22+ output, large output, binder disconnect and service timeout. Verify the standard radar still works when Shizuku is absent.


## VORTIX 7.0.5 — Seven video-review remediations

- Corrected/locked the Controls Radar permission action to `Settings.ACTION_USAGE_ACCESS_SETTINGS`; the separate Battery Saver action remains explicitly `Settings.ACTION_BATTERY_SAVER_SETTINGS`.
- Made the bottom navigation capsule fully opaque and replaced fixed scroll clearance with Scaffold bottom-bar padding plus a small safety gap.
- Added Usage Access buttons to both Radar-tab Network Usage and Dormant Apps cards and refreshes both data sources on activity resume.
- Added signed-current formatting that normalizes values rounding to zero to `0 mA`.
- Serialized design-capacity writes, ignored stale pending writes, and connected selected value plus confirmation message to the same ViewModel state.
- Added start/end timestamps and min/max percentages to the battery history chart.
- Replaced the passive `CANLI` status label with `NORMAL`; `UYARI` appears only while an alert is active.
- Added six pure formatter/chart tests and a rapid-capacity-selection regression test.
- Bumped the application version to 7.0.5 / versionCode 14 and retained `.github/workflows/build.yml`, the 76.dp multiplier correction and the 7.0.4 Shizuku sources.

Not run in this workspace: full Gradle/Android compilation, complete JUnit suite, APK assembly and Firebase Test Lab for this exact 7.0.5 archive. The exact revision still needs CI and device verification.
