# VORTIX — Battery Analytics Unit Test Fix

## Reported failure

`BatteryAnalyticsEngineTest.recordsObservedDischargeSessionsWithScreenStateCoverage`
failed because `observedScreenOnMillis` was `0` instead of `3,600,000` ms.

## Root cause

`findDischargeSessions()` counted an interval only when both endpoint snapshots had the same `screenInteractive` value. The test's first interval spans a screen-state transition (`true` at the beginning and `false` at the end), so the entire one-hour interval was discarded.

## Change

Discharge-session screen coverage now attributes each valid observed interval to the screen state recorded by its starting snapshot:

- `true`: add the interval to `observedScreenOnMillis`.
- `false`: add the interval to `observedScreenOffMillis`.
- `null`: do not attribute the interval to either category.

The existing maximum interval guard remains in place, so gaps larger than `MAX_DRAIN_INTERVAL_MILLIS` are still excluded. A regression-test comment documents the interval-attribution rule.

## Validation status

- Source-level inspection: completed.
- Gradle wrapper script syntax: previously checked for the AGP9 archive; this patch does not modify Gradle configuration.
- Attempted `bash gradlew :app:testDebugUnitTest :app:assembleDebug`: **not run to completion** because the environment could not resolve `services.gradle.org` (`curl: (6) Could not resolve host`).
- Therefore the unit test and APK build are **not claimed as passing** in this environment. Please rerun both tasks in Android Studio or an environment with the Gradle distribution available.
