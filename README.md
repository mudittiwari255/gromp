# Gromp

A calm, fully offline Android app that shows you where your phone time goes, and helps you take some of it back.

- **No internet permission.** The manifest strips `INTERNET`, and CI fails the build if it ever appears.
- **Everything stays on the phone.** History lives in a local Room database. Backups are a JSON file you save yourself. Cloud backup and device transfer are disabled.
- **Soft locks, not a prison.** Pauses, gentle limits and focus hours add friction. You can always get through with a deliberate press-and-hold.

## What's in v1 and v1.5

| Screen | What it shows |
| --- | --- |
| Onboarding | Privacy promises and the Usage access grant |
| Today | Screen time ring against your daily limit, pace vs your usual day, pickups, first pickup, longest session, notifications, an hour-by-hour chart, top apps with trend vs usual, and a tonight's-pattern insight |
| Trends | Week / Month / Year bars with average line, calmest and heaviest day, pickups per day, a 7×24 "when you reach for it" heatmap, and time by category |
| App detail | Today vs average, opens, average and longest session, visit-length buckets, 30-day sparkline, and the app's locks |
| Insights | Rule-based, explainable patterns (quick checks, evening pull, notification sources, first-pickup trend, calmest day, biggest app) and goal days with no streak guilt |
| Settings | Daily limit, locks status, focus hours, locked apps, permissions, battery tips, export/restore backup, delete everything |
| **Locks (v1.5)** | *Breathe before opening* (5-second pause), *daily limit* screen (hold 3 s for 5 more minutes), and *focus hours* (selected apps rest; hold 5 s to open for 5 minutes) |

## How it works

```
UsageStatsManager events ──► SessionParser (pure Kotlin, unit tested)
                                   │
                    SnapshotWorker (hourly) + on screen open
                                   ▼
                     Room: day_summary · app_day · hour_usage · notif_day · lock_rule
                                   ▼
                     Repository ──► Compose screens

AccessibilityService (window-state events only) ──► LockDecider (pure, unit tested) ──► LockActivity
NotificationListenerService ──► per-app daily counts (content is never read)
```

- Android keeps detailed usage events for only about a week, so `SnapshotWorker` copies each day into Room every hour. Past days are only overwritten with fuller data, never with an emptier read.
- "Usual" comparisons are fair: today so far is compared with the same weekday over the last four weeks, up to the same time of day.
- The accessibility service listens only for `typeWindowStateChanged`, has `canRetrieveWindowContent="false"`, and never locks the dialer, keyboards, the home screen, Settings or Gromp itself.

## Build

Requirements: JDK 17 and the Android SDK (API 36).

```bash
./gradlew testDebugUnitTest   # parser, focus schedule and lock decision tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Every push to `main` runs the GitHub Actions workflow in `.github/workflows/android.yml`. It runs the tests, builds debug and release APKs, checks that the release APK has no `INTERNET` permission, and uploads both APKs as the `gromp-apk` artifact.

The release build is signed with the debug key so CI produces an installable APK. Set up your own keystore before publishing.

## First run on a phone

1. Install the APK and open Gromp.
2. **Usage access:** tap *Grant usage access*, find Gromp, switch it on, come back.
3. **Notification counting (optional):** Settings › Permissions.
4. **Locks:** open any app's page, turn on a lock, read the disclosure, then enable *Gromp locks* in Accessibility settings.
5. **Xiaomi, Oppo, Vivo, Realme, Samsung:** allow background activity / Autostart for Gromp, or history and locks may stop.

## Publishing notes

- Google Play treats Accessibility as sensitive. Gromp shows an in-app prominent disclosure before opening the settings, and you'll need to complete the Accessibility declaration in Play Console, explaining the digital-wellbeing use.
- `PACKAGE_USAGE_STATS` is acceptable for digital wellbeing apps.
- F-Droid is a good fit for a fully offline app.

## Licenses

Manrope typeface © The Manrope Project Authors, [SIL Open Font License 1.1](licenses/Manrope-OFL.txt).
