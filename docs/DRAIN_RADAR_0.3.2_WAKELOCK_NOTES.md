# VORTIX 7.0.4 — Drain Radar 0.3.2 Wakelock Diagnostics

## Delivered in this slice

- Optional Shizuku/Sui bridge using the supported UserService + AIDL pattern.
- Read-only `dumpsys -t 15 batterystats --charged --checkin` collection.
- Only `wl` and `awl` check-in rows are returned across Binder; output is capped below the Binder transaction buffer size.
- Version-aware parser for check-in formats v18, v19–20, v21 and v22+; v21 actual duration is parsed without assuming v22 background fields.
- Per-UID/tag partial wakelock duration and count, plus background partial duration when available. Aggregate UID rows preserve an unknown count as unavailable, not zero.
- UI separates Shizuku availability/permission from the standard UsageStats/NetworkStats radar.
- No `batterystats --reset`, no app process termination, and no wakelock release operation.
- `BatteryInsightsPanel` retains the verified `76.dp * Float` expression.
- Canonical `.github/workflows/build.yml` runs unit tests and debug assembly.

## Evidence and limits

Android protects `BATTERY_STATS` as a signature/privileged/development permission, so VORTIX does not request it as an ordinary runtime permission. The optional Shizuku bridge requires a compatible server (permission APIs from v11), and the user explicitly grants VORTIX Shizuku access. The UserService runs with shell or root identity depending on how Shizuku/Sui was started. Shell capability can vary by Android version/OEM; access failures remain visible and do not disable the standard radar. Android check-in `wl` rows are versioned; newer formats expose actual partial duration separately from apportioned partial time. The app labels these as durations, not mAh.

## Verification status

Parser tests cover the legacy v18, v19–20, v21 and v22+ row layouts, transport row filtering, malformed/non-charged data, quoted tags, aggregation, and `awl` fallback. The UserService and Shizuku repository also passed local Kotlin syntax/API-shape checks with test stubs; these checks do not replace the Android Gradle build. Local Gradle/Android SDK execution is not available in this build container; run the CI workflow before treating this source update as build-verified.
