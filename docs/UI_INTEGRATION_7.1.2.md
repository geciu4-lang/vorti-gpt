# VORTIX 7.1.2 — Compose UI Integration & Validation

## Scope

This revision integrates the 7.1.1 domain, repository and ViewModel features into the visible Dashboard, Controls and Radar destinations without replacing the current Ambient Glassmorphism design.

## Dashboard / Özet

- The thermal wear indicator remains informational and distinguishes normal, accelerated and high-stress ranges.
- The status capsule displays `NORMAL` in green when a snapshot is available and no alert rule is active, `UYARI` in red when an active reason exists, and `ÖLÇÜLÜYOR` until the first snapshot arrives.
- Alert reasons are exposed in the UI so a 25°C reading can be distinguished from other legitimate triggers (Android `OVERHEAT`, MODERATE+ system thermal status, enabled low-battery threshold, or charge target reached).

## Controls / Kontrol

- The Live Notification Telemetry card is placed near the top of the page. Android 13+ permission request state is correctly set for both switch and permission-button flows.
- The Charging Benchmark card and Charging Profile card are moved near the top so they are not buried below legacy settings.
- Charge-state polling runs every five seconds only while the Controls page is RESUMED; it refreshes both benchmark availability and profile charging state.
- Webhook URL, GET/POST selector and Test button are always visible in the Charge Target card. The automation switch only controls automatic target-triggering, not manual configuration or connection testing.
- Legacy thermal, low-battery, design-capacity, monitoring, Eco Guard and system-shortcut controls remain in place.

## Radar

- Night Standby Analysis remains the first Radar card. When monitoring is disabled, it offers a direct action to open Controls and enable background sampling.
- Shizuku package actions, dormant-app list, network usage, health analysis and CSV export remain connected to the existing ViewModels.

## Test updates

- Added two alert-evaluator unit tests: 25°C + thermal NONE produces no active alert reason; a reached charge target exposes an explicit reason.
- `ChargingProfileRepositoryImplTest` keeps explicit JUnit4-compatible `: Unit = runBlocking { ... }` return types.

## Protected files

- `BatteryInsightsPanel.kt` retains `import com.vortix.app.platform.UsageAccessSettingsLauncher`.
- `IWakelockDiagnosticsService.aidl` is unchanged, including transaction-ID-free `void destroy();`.
- `.github/workflows/build.yml` is unchanged.

## Validation performed in this environment

- Alert-evaluator JVM smoke test: 4/4 assertions passed using the installed Kotlin compiler.
- Manifest XML parsing, Compose wiring/source checks, explicit JUnit return-type check, protected-file hash checks and ZIP integrity checks are performed during packaging.
- Full `./gradlew :app:testDebugUnitTest :app:assembleDebug` could not start because Gradle 9.6.1 is not cached and `services.gradle.org` DNS resolution is unavailable in this environment. No APK or Firebase Test Lab success is claimed for 7.1.2. The 7.1.1 CI/device results supplied by the project owner remain the verified baseline only.

## Device retest checklist

1. On a normal device at approximately 25°C, confirm green `NORMAL`; raise the device to a legitimate thermal event or set low threshold/charge target to verify red `UYARI` and its reason.
2. In Controls, verify live telemetry switch is visible and Android 13+ notification permission flow starts the foreground service.
3. Verify Webhook URL and Test controls are visible before the automation switch is enabled; use a test endpoint that is safe to call.
4. While charging, verify the benchmark starts, counts down from 30 seconds and reports the result; unplugging cancels the run.
5. While charging, save an adapter profile; unplug/replug while Controls is open and confirm the availability state refreshes.
6. In Radar, verify the night card is visible and the CTA routes to Controls while sampling is disabled.
7. Re-run Firebase Test Lab on Pixel 5 / API 30 and a modern API 35+ device.
