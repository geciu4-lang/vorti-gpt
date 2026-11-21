# VORTIX 7.0.5 — Video review remediation report

This revision addresses the seven issues reported from the VORTIX 7.0.3 device/emulator test video.

## Changes

1. **Usage Access intent:** The Controls → Vortex Drain Radar permission action now uses an explicit `Settings.ACTION_USAGE_ACCESS_SETTINGS` helper. It remains separate from the dedicated Battery Saver shortcut, which continues to use `Settings.ACTION_BATTERY_SAVER_SETTINGS`.
2. **Bottom dock opacity and scroll clearance:** The bottom navigation capsule now uses a fully opaque dark surface. The dashboard's scroll content reserves the actual Scaffold bottom-bar inset plus an additional gap, replacing the previous fixed 110 dp spacing.
3. **Radar permission actions:** The Network Usage and Dormant Apps cards now each provide a direct Usage Access Settings button when the access is missing. The Radar refreshes its permission/data state again when the activity resumes from Android Settings.
4. **Negative zero current:** Current formatting rounds to whole mA before rendering and explicitly normalizes values rounding to zero to `0 mA`; signed non-zero currents remain signed.
5. **Design-capacity selection synchronization:** The selected value is reflected optimistically in a single ViewModel state, persistence writes are serialized, stale not-yet-started selections are skipped, and the confirmation/error message is shown inside the same capacity card. A regression test covers rapid chip taps.
6. **History chart labels:** The chart now shows the timestamp of its first and last valid points and observed minimum/maximum battery percentages. It continues to use at most the latest 24 ordered samples and ignores missing levels.
7. **Alert badge:** The top-right badge now says `UYARI` only when an alert condition is active; otherwise it says `NORMAL`.

## Tests added

- `DashboardDisplayFormattersTest`: zero/rounded-negative-zero current, signed values, unavailable current, chronological chart labels, min/max and clamped levels.
- `EnergyControlsViewModelTest.rapidDesignCapacitySelectionsKeepLatestValueAndConfirmationInSync`: verifies that rapid selections persist only the latest pending value and that the selected state and confirmation text agree.

## Preserved

- Existing 7.0.4 Drain Radar / optional Shizuku Wakelock implementation.
- `.github/workflows/build.yml` and its unit-test + Debug APK steps.
- The corrected Compose expression `76.dp * ratio` in `BatteryInsightsPanel.kt`.
- Existing ViewModel, Repository, Room, WorkManager, Hilt and analytics architecture.

## Verification status

Source-level review and archive integrity checks are performed for this ZIP. Full Android Gradle compilation, KSP/Hilt/Room generation, the complete unit-test task, APK assembly and Firebase Test Lab must still be run on GitHub Actions / an Android SDK-equipped environment for this exact 7.0.5 revision. The previously user-reported 7.0.3 CI run and Pixel 5/API 30 result do not prove the new 7.0.5 build.
