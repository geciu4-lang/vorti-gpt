# VORTIX 7.0.6 — follow-up video review remediation report

This revision addresses the six issues the user reported as still visible after testing VORTIX 7.0.5 on Firebase Test Lab Pixel 5 / API 30. The user also reported that 7.0.5's unit tests and Debug APK build passed on GitHub Actions after changing the AIDL declaration to `void destroy();`. That AIDL form is retained in this archive.

## Changes

1. **Usage Access intent:** Controls and Radar permission buttons now use one shared `UsageAccessSettingsLauncher`. It creates only `Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)` and has no Battery Saver fallback. If Android cannot open that action, VORTIX shows an error message instead of silently opening a different settings screen.
2. **Bottom dock opacity and scroll clearance:** The entire bottom-bar width, including the navigation-bar inset, now has an opaque VORTIX background. Scaffold's bottom-bar inset is applied outside the scroll modifier, so the scroll viewport ends above the dock; a small scrollable tail remains after the final card.
3. **Radar permission actions:** Both Network Usage and Dormant Apps cards always show a Usage Access settings button. The label changes between enabling and managing the permission. Radar refreshes permission and data state again on `ON_RESUME`.
4. **Design-capacity confirmation:** Every capacity selection updates the selected capacity, card-local confirmation text and the top Controls banner in the same ViewModel state update. Writes remain serialized and stale pending writes are skipped. The rapid-selection regression test now also checks the global banner text.
5. **Hero Radar navigation:** The `Tüketim Radarı` hero button and bottom dock share the same tab-selection callback. Tab content is keyed by the selected tab so changing the selection replaces the visible branch immediately.
6. **History labels and alert badge:** The chart displays prominent first/last timestamps and separate minimum/maximum percentage chips. The top-right `UYARI` badge is rendered only while a configured alert or overheat condition is active; no passive badge is shown otherwise.

## Previously addressed formatting retained

- Signed current values that round to zero are normalized to `0 mA`; negative zero is never emitted.
- The Compose `Dp` multiplication fix remains `76.dp * ratio` in `BatteryInsightsPanel.kt`.

## Preserved

- Optional Shizuku/Sui Wakelock UserService and AIDL integration.
- `.github/workflows/build.yml` and its `:app:testDebugUnitTest` plus `:app:assembleDebug` steps; only the uploaded artifact name follows the new version.
- The existing MVVM, Repository, Room, WorkManager, Hilt and analytics architecture.
- The dedicated Android Battery Saver shortcut remains separate and still opens Battery Saver only when the user explicitly selects that shortcut.

## Tests and verification

- `DashboardDisplayFormattersTest` continues to cover zero/rounded-negative-zero current, signed values, unavailable current and chronological chart min/max/time labels.
- `EnergyControlsViewModelTest.rapidDesignCapacitySelectionsKeepLatestValueAndConfirmationInSync` also asserts that the top banner matches the final saved capacity after rapid selections.
- `UsageAccessSettingsLauncherTest` asserts the exact `android.settings.USAGE_ACCESS_SETTINGS` action contract.
- Local Kotlin harness: 12 dashboard formatter/chart assertions passed; Usage Access intent action contract passed.
- Static checks: Android manifest XML, Gradle version-catalog TOML, GitHub Actions YAML and source invariants passed; ZIP integrity and SHA-256 are checked at packaging.

Full Android Gradle compilation, the complete JUnit suite, KSP/Hilt/Room generation, APK assembly and Firebase Test Lab were not run for this exact 7.0.6 revision. The local environment has JDK 21 and `kotlinc`, but no Android SDK or cached Gradle distribution; the Gradle wrapper could not resolve `services.gradle.org` because DNS/network access is unavailable. The user-reported 7.0.5 green CI and Pixel 5/API 30 result do not by themselves validate this 7.0.6 source. Run CI and then repeat the same device/video checks before release.
