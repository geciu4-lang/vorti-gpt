# VORTIX 7.0.1 — Finalization audit

## Candidate identity

- Application ID: `com.vortix.app`
- Version name: `7.0.1`
- Version code: `11`
- Compile SDK: `37`
- Target SDK: `36`
- Minimum SDK: `26`

## Review outcome

The source archive contained mismatched generations: EnergyControlsViewModel referenced settings absent from its repository interface, DashboardViewModel referenced missing history/live-stream methods, the dashboard test used an obsolete constructor, and the insights ViewModel was not connected to the visible dashboard. These source-level integration gaps have been addressed in this revision.

The background worker now respects monitoring and alert toggles, supports low-battery notifications, and uses the configured thermal threshold. User-facing wording clarifies that charge-target alerts do not stop hardware charging and Eco Guard does not silently change Android settings.

## Current decision

**Candidate only — not production-approved.** No claim is made that the Android project currently compiles or that its tests pass. The environment lacks Android SDK tooling and cannot download the pinned Gradle distribution.

Release requires a successful build and unit-test run on this exact source revision, generated Room schema validation, signed Release artifact verification, and physical-device/OEM testing.
