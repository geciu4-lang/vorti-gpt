# VORTIX — Optional Shizuku Wakelock Diagnostics

The standard VORTIX Drain Radar does not need Shizuku. This guide is only for the advanced Wakelock diagnostics card.

## Setup

1. Install Shizuku from the official guide: https://shizuku.rikka.app/guide/setup/
2. Start the Shizuku service using the method supported by your device. On non-rooted Android 11+ devices, Shizuku can be started using Wireless debugging; rooted devices can use the root start method.
3. Open VORTIX and navigate to **Kontrol → Gelişmiş Wakelock Tanılaması**.
4. Grant VORTIX permission in the Shizuku prompt.
5. Return to VORTIX and tap **Wakelock kayıtlarını tara**.

## What VORTIX reads

VORTIX starts a short-lived Shizuku UserService and runs a read-only command:

```sh
/system/bin/dumpsys -t 15 batterystats --charged --checkin
```

Only `wl` (wake-lock) and `awl` (aggregate wake-lock) check-in rows are sent back to the app. The result is held in memory and is not sent to a server.

## Important limits

- Shizuku/Sui must be running, and permission must be granted each time the platform requests it.
- The Shizuku server API must support permission requests (v11 or later).
- Android/OEM releases may omit or alter some Batterystats detail. A missing row is not proof that an app never held a wake lock.
- Wakelock duration and count are not mAh or battery-drain measurements.
- VORTIX never resets Batterystats, force-stops apps, or releases their wake locks.
- If the advanced source is unavailable, the standard UsageStats/NetworkStats radar continues to work.
