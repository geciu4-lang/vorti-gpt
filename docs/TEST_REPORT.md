# VORTIX 7.0.3 — Test report

**Execution status: PARTIAL SOURCE-LEVEL CHECKS ONLY.** A source file or test existing in the archive is not evidence of a passing Gradle/JUnit run.

## Added / updated test coverage

| Test class | Focus | Execution status |
|---|---|---|
| `UidTrafficAccumulatorTest` | UID aggregation across buckets, negative UID/counter handling and overflow saturation | Not run through Gradle/JUnit |
| `AppUsageItemTest` | Available vs unavailable network totals, overflow protection and shared UID marker | Not run through Gradle/JUnit |
| `EnergyControlsViewModelTest` | Usage Access gating and existing controls behavior | Not run through Gradle/JUnit |
| `BatteryAnalyticsEngineTest` | Capacity, drain, sessions, thermal exposure and personal baseline | Not run through Gradle/JUnit |
| `PowerAdvisorEngineTest` | Evidence-gated recommendations, including Eco Guard threshold | Not run through Gradle/JUnit |
| Remaining existing mapper, repository, use-case, dashboard and CSV tests | Existing behavior regression coverage | Not run through Gradle/JUnit |

## Executed check

A standalone Kotlin compiler smoke harness compiled `UidTrafficAccumulator.kt` and `AppUsageItem.kt` and passed assertions for:

- Aggregation of receive/transmit bytes by UID.
- Clamping negative byte counters.
- Zero traffic versus unavailable traffic semantics.
- Shared UID metadata.
- `Long` overflow saturation.

Result: **PASS** for these pure-Kotlin source components only. This does not validate Android framework access or Compose code.

## Gradle / Android validation

Attempted:

```bash
./gradlew :app:testDebugUnitTest
```

The Gradle 9.6.1 bootstrap failed because `services.gradle.org` could not be resolved. The environment has JDK 21 and the Kotlin compiler but no configured Android SDK/Android Studio/adb and no cached Gradle distribution. Consequently the Android build and test suite could not run.

Required on Android Studio or a connected CI runner with JDK 21 and Android SDK Platform 37 / Build Tools 37.0.0:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Device validation still required

- API 26, 29, 30, 31, 33, 34 and target-36/37-compatible devices where available.
- Usage Access grant/deny/revoke behavior.
- Mobile and Wi-Fi data on dual-SIM, Wi-Fi-only, mobile-only and offline devices.
- Empty/unsupported NetworkStats responses and exact zero-byte entries.
- Shared UID package behavior and package visibility differences.
- Performance impact of NetworkStats queries on worker threads.
- Pixel/AOSP-like, Samsung One UI and Xiaomi/HyperOS behavior.
- Existing WorkManager, Room migration, widget, Quick Settings Tile and battery analytics regressions.

**Test verdict: NOT BUILD-CERTIFIED.** The earlier user-reported Firebase Test Lab pass applies to their previous working app revision, not this 7.0.3 archive.
