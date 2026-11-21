# VORTIX 7.1.1 — Thermal, Shizuku, Webhook & Charger Profiles

## Release metadata

- `versionName`: `7.1.1`
- `versionCode`: `17`
- `minSdk`: `26`
- `targetSdk`: `36`
- Package: `com.vortix.app`
- UI: Jetpack Compose / Material 3, existing Ambient Glassmorphism palette

## Implemented

### 1. Dynamic thermal alert and wear indicator

- Reads `PowerManager.getCurrentThermalStatus()` only on Android Q/API 29+; older devices return a null thermal status and retain normal app behavior.
- Thermal `MODERATE` and above are treated as an active system thermal warning.
- Dashboard alert evaluation also considers battery overheat health, configured battery-temperature threshold, configured low-battery threshold, and the configured charging target.
- Added an indicative temperature multiplier: `<30°C = 1.0x`, `30–<35°C = 1.2x`, `35–<40°C = 1.5x`, `>=40°C = 2.4x`.
- The multiplier is a heuristic presentation signal, not a laboratory-validated battery ageing model or a prediction of remaining battery life.

### 2. Shizuku package actions

- Extended `IWakelockDiagnosticsService.aidl` by appending `forceStopPackage(String)` and `restrictBackground(String)` after the existing `void destroy();` declaration. No explicit transaction IDs were introduced; existing method order/signature remains intact.
- Commands are passed as process argument arrays (`argv`) and never concatenated into `sh -c` shell text.
- Package names are validated; the repository rejects this app, missing packages, system apps, and updated-system apps.
- Radar exposes `Durdur` and `Arka Planı Sınırla` while Shizuku is authorized. When unavailable, it offers Android's per-app details settings screen.
- `RUN_ANY_IN_BACKGROUND=ignore` can affect notifications, scheduled jobs, and background sync for the selected app. Users should apply it selectively.

### 3. Charge-target webhook

- Added optional URL, GET/POST choice, and a test action under the charge-target controls.
- Only HTTPS URLs are accepted to avoid transmitting webhook tokens over cleartext connections. A local Home Assistant instance exposed only over HTTP needs an HTTPS reverse proxy / TLS endpoint before it can be used.
- Requests use finite connect/read timeouts, do not follow redirects, and POST sends a small JSON event payload.
- Target detection is coordinated between WorkManager and the live telemetry service with a persisted one-shot latch, avoiding duplicate requests during the same connected charging cycle. A failed request is recorded and is not retried repeatedly in that same cycle.
- The test button contacts the real configured endpoint and may trigger the automation; the UI warns about this.
- WorkManager periodic execution is not real-time: Android documents a 15-minute minimum interval for periodic work and allows further delay due to power optimizations. The optional live telemetry foreground service polls at approximately five-second intervals while enabled and allowed.

### 4. Adapter / cable comparison profiles

- Added a Room `charging_profiles` table with migration `2 -> 3`, DAO, repository, Hilt binding, ViewModel, and Controls UI.
- A profile can only begin while the device reports active charging and a known plugged source.
- Records battery-side average/peak power, start/end temperature, delta temperature, sample count, and a heuristic thermal-efficiency grade (A+, A, B, C).
- The comparison panel shows the latest two named profiles side by side.
- Android's standard battery telemetry estimates power from battery current and battery terminal voltage. It does **not** directly measure USB adapter output power, USB-PD negotiation, cable resistance, or wall-side efficiency. Grades should be used for repeatable same-device comparisons, not as certified charger wattage claims.
- Background sampling frequency depends on source: approximately five seconds while live telemetry is enabled; otherwise WorkManager may provide much coarser samples.

## Compatibility invariants preserved

- `BatteryInsightsPanel.kt` still includes the exact import:
  `import com.vortix.app.platform.UsageAccessSettingsLauncher`
- `IWakelockDiagnosticsService.aidl` retains the no-transaction-ID declaration `void destroy();`; the new methods are appended after it.
- `.github/workflows/build.yml` was not modified. Its SHA-256 remains:
  `1a48a371ecfb93eeff06a2e071af078800d0d249de80363503d4ec619c6fbdea`
- `minSdk 26` retained; the thermal API call is guarded by `SDK_INT >= Q`.

## Tests added

36 new `@Test` methods were added across thermal/alert rules, webhook validation and coordinator, Shizuku package validation and command creation, adapter repository and ViewModel, efficiency scoring, and the Controls ViewModel.

## Validation status in the authoring environment

- 23 pure Kotlin contract checks: **passed**.
- `AndroidManifest.xml` XML parse: **passed**.
- Required import check: **passed**.
- AIDL declaration/signature check: **passed**.
- CI workflow unchanged/hash comparison with baseline: **passed**.
- `:app:testDebugUnitTest` and `:app:assembleDebug`: **not run successfully here**. The project wrapper requires Gradle 9.6.1 from `services.gradle.org`; the distribution is not cached in this environment and outbound DNS/network access is unavailable. Therefore no full Android build or Firebase Test Lab result is claimed for 7.1.1.

## Recommended verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then validate on a real device:

1. Check API 26–28 thermal fallback, and API 29+ system thermal status changes.
2. Confirm Shizuku permission flow, third-party package force-stop, background app-op restriction, and settings fallback.
3. Verify the real HTTPS webhook endpoint using `Test Et`, then verify one automatic event at the configured charge target and no duplicate during the same charging cycle.
4. Record two distinct adapter sessions while charging, compare their values, unplug, and ensure each profile closes.
5. Run Firebase Test Lab smoke / Robo test and inspect logs for foreground-service, Room migration, Binder, and notification-permission issues.
