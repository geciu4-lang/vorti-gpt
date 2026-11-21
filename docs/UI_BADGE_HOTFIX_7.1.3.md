# VORTIX 7.1.3 — Dashboard badge UI hotfix

## User-reported 7.1.2 baseline

The project owner reported that `:app:testDebugUnitTest` and `:app:assembleDebug` passed and that Firebase Test Lab Pixel 5 / API 30 completed with zero crashes. The four Controls cards, adapter benchmark/profile flow and Radar night-standby card were visible and exercised. The remaining reported defects were the top-right badge wrapping to one character per line and a red UYARI badge at 33.6°C while Android reported thermal status NONE.

## Source changes

- `DashboardScreen.kt`: the flexible title block uses `Modifier.weight(1f)`; the thermal/wear string and status label are constrained to one line with `softWrap = false` and ellipsis. The badge has a bounded width and the alert reason is moved to a separate line below the header row.
- `BatteryAlertEvaluator.kt`: an OEM `BatteryManager.EXTRA_HEALTH = BATTERY_HEALTH_OVERHEAT` flag is used as a fallback only when battery temperature is unavailable. If a valid sub-threshold temperature is present and `PowerManager` thermal status is NONE/LIGHT, the stale health flag no longer independently paints the dashboard badge red. Low battery, configured battery-temperature threshold, charge target and MODERATE+ system thermal alerts remain active.
- `BatteryAlertEvaluatorTest.kt`: adds a 33.6°C + `THERMAL_NONE` stale-OEM-flag regression test and a missing-temperature fallback test; updates the prior overheat test to reflect the fallback-only rule.
- `app/build.gradle.kts`: version bumped to 7.1.3 / versionCode 19.

## Local checks

- Kotlin/JVM smoke harness for the alert evaluator and thermal wear analyzer: 7/7 passed.
- Source inspection confirms the title row's left block uses `Modifier.weight(1f)`, both thermal text and badge label use `maxLines = 1` and `softWrap = false`, and the alert reason no longer occupies the badge.
- `UsageAccessSettingsLauncher` import remains in `BatteryInsightsPanel.kt`.
- AIDL declaration and `.github/workflows/build.yml` were not edited.
- ZIP integrity and SHA-256 are generated for this archive.

## Not verified

`./gradlew :app:testDebugUnitTest` was attempted but the wrapper could not resolve `services.gradle.org` to download Gradle 9.6.1 (`curl: (6) Could not resolve host`). Therefore full Android/Kotlin/Compose compilation, all JUnit tests, APK assembly and Firebase Test Lab are not claimed for this 7.1.3 candidate. Re-run the two Gradle tasks in GitHub Actions and repeat the Pixel 5/API 30 video check before release.

## Device retest focus

1. At 33.6°C with Android `THERMAL_NONE`, no low-battery/charge-target alert and a thermal threshold above 33.6°C, show the green `NORMAL` badge.
2. Check widths around 320dp and larger: badge text must remain on one line; thermal text may ellipsize, never force the badge to wrap.
3. Verify `MODERATE+`, configured temperature threshold, low-battery threshold and reached charge target still show red `UYARI`.
