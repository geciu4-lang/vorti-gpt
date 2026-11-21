# VORTIX 7.1.0 – Feature Patch Notes

## Added

- Dynamic dashboard warning evaluation for overheat, configured temperature/low-battery thresholds and reached charge target. In a normal state the red `UYARI` badge is hidden.
- Optional ongoing silent live-telemetry foreground service with a low-importance notification channel and five-second battery sampling. The switch requests `POST_NOTIFICATIONS` on Android 13+.
- 30-second charging benchmark: one-second battery-side samples, peak power, average current, voltage coefficient of variation, temperature delta and heuristic A+/A/B/C grade.
- Night standby detection from historical screen-off and unplugged discharge samples. Reports %/hour and mAh where charge-counter data exists, otherwise uses marked design-capacity estimation. Optional UsageStats foreground-use context is not labelled as app-specific battery drain.

## Preserved

- MVVM + Clean Architecture, Hilt, Room, DataStore and WorkManager.
- `BatteryInsightsPanel.kt` import `com.vortix.app.platform.UsageAccessSettingsLauncher`.
- Existing GitHub Actions job sequence: unit tests, debug APK assembly, artifact upload and test report upload. Only the uploaded APK artifact name changes to `VORTIX-7.1.0-Debug`.
- Package ID `com.vortix.app`, minimum SDK 26 and target SDK 36.

## Verification status

- 13 isolated pure Kotlin feature test methods passed using a temporary test harness. This is not equivalent to the project's Gradle/JUnit task.
- The charging benchmark ViewModel source was compiled separately with lightweight lifecycle/Hilt stubs.
- `AndroidManifest.xml` parsed as well-formed XML.
- Full Gradle `:app:testDebugUnitTest` and `:app:assembleDebug` could not be run: wrapper download failed because `services.gradle.org` DNS/network access was unavailable. No APK was produced or claimed.
- Firebase Test Lab has not been run for this new revision.

## Measurement boundaries

Android public battery APIs provide battery-side current and terminal voltage, not universal USB adapter input power, PD negotiation, cable resistance or reliable per-app mAh. Benchmark grades are same-device heuristics, not adapter certification. Night-session app context is UsageStats foreground time only.
