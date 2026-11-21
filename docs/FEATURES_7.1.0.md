# VORTIX 7.1.0 – Live Telemetry, Charging Benchmark & Night Standby

## Included

- Dynamic dashboard `UYARI` state: only active battery-health, configured thermal/low-battery, or reached charge-target conditions show the red badge.
- Optional persistent live telemetry foreground service. It uses a silent, low-importance notification channel and refreshes battery-side power/current/temperature/charge state every five seconds. Android 13+ POST_NOTIFICATIONS is requested before enabling it. Android 14+ declares the `specialUse` foreground-service subtype.
- 30-second charging benchmark with one-second battery-side current/voltage/temperature sampling, estimated peak battery power, average current, voltage variation, temperature delta and heuristic A+/A/B/C grade.
- Night standby analysis from recorded samples: requires screen-off, unplugged discharge observations over at least four hours and breaks sessions with gaps over 45 minutes. It reports percent/hour and charge-counter mAh when available; configured design capacity is only used as an explicitly marked estimate.
- UsageStats may show the most-used app during a detected standby window. This is a usage signal, not proof of application-specific battery drain or mAh attribution.

## Measurement limitations

Android's public battery APIs expose battery-side current and terminal voltage on supported devices. They do not provide universal USB adapter input power, USB-PD negotiation details, direct cable resistance, or reliable per-application battery mAh attribution. Benchmark grades are device-side heuristics for comparing repeat tests on the same device, not laboratory adapter certification.

Night standby depends on WorkManager-backed battery history sampling. Android may defer background work during Doze or under OEM battery restrictions; long sample gaps are therefore not treated as continuous standby.

## Permissions and runtime

- `POST_NOTIFICATIONS` is required by the UI to enable the live notification on Android 13+.
- The ongoing telemetry service uses `FOREGROUND_SERVICE` and the Android 14+ `FOREGROUND_SERVICE_SPECIAL_USE` subtype. Store distribution may require the developer to declare and justify this foreground-service use in Play Console.
- The optional app activity signal uses the existing Usage Access permission and is labelled as foreground usage, not battery consumption.

## CI

The existing GitHub Actions job structure is retained: unit tests, debug APK assembly, APK artifact upload and unit-test report upload.
