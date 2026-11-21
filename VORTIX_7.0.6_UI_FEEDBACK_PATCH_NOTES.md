# VORTIX 7.0.6 – UI Feedback Patch Candidate

## Scope

This candidate contains only the two fixes requested after the VORTIX 7.0.6 video review.

### 1. Bottom navigation opacity / stacking
- The dock root is explicitly placed above the scroll layer with `zIndex(1f)`.
- The full-width backdrop and capsule Surface explicitly use `alpha = 1f`.
- Existing capsule shape, spacing, labels, selected-state colors, and navigation behavior are retained.

### 2. Capacity confirmation feedback
- `EnergyControlsUiState` now carries a monotonically increasing `designCapacityFeedbackId`.
- `EnergyControlsViewModel.setDesignCapacityMah()` increments it on every chip selection, including selecting the same value again.
- `EnergyControlsPanel` uses the event ID and message as the `AnimatedContent` target so a new selection replays the banner transition.
- The existing Mutex / request-sequence stale-write protection is retained. Only the latest valid selection is persisted when taps supersede pending writes.
- Banner tint distinguishes saving (cyan), success (mint/primary), and persistence failure (error red).
- Regression tests assert that three consecutive selections produce feedback ID `3` and that a failed capacity write results in an error state rather than a success state.

## Files changed

- `app/src/main/java/com/vortix/app/feature/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/vortix/app/feature/controls/EnergyControlsPanel.kt`
- `app/src/main/java/com/vortix/app/feature/controls/EnergyControlsUiState.kt`
- `app/src/main/java/com/vortix/app/feature/controls/EnergyControlsViewModel.kt`
- `app/src/test/java/com/vortix/app/feature/controls/EnergyControlsViewModelTest.kt`

## Validation status

**Not yet build-verified in this environment.** I attempted `./gradlew :app:testDebugUnitTest :app:assembleDebug`, but the Gradle 9.6.1 wrapper distribution was not cached and this runtime could not resolve `services.gradle.org` (DNS/network unavailable). No successful compile or test result is claimed for this candidate.

Run these checks in GitHub Actions or an Android build environment:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then repeat the Firebase Test Lab Pixel 5 / API 30 video checks, especially:
- Scroll through the long content and confirm no underlying text is visible through the dock.
- Select capacities rapidly (e.g. 8000 → 6000 → 9000 mAh) and confirm the banner re-animates for the latest selection.
- Verify a failed persistence operation never produces a success confirmation.

The app's declared version remains `7.0.6` in this candidate; no release/version bump has been made before CI and device validation.
