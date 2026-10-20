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
| **Category limits** | One daily limit shared by every app in a category (e.g. Social 1h). Whichever runs out first, the app's own limit or its category's, shows the limit screen; "5 more minutes" applies to the whole category |

## Editions

| | Lite | Full |
| --- | --- | --- |
| Today, Trends, App detail, Insights, backups | ✓ | ✓ |
| Soft locks (pause, limits, focus hours) | | ✓ |
| Notification counting | | ✓ |
| Install from a phone browser | ✓ | Blocked in some regions* |

\* Play Protect blocks browser- or chat-downloaded APKs that request Accessibility or notification-listener access in some regions, including India. Install Full from a computer with `adb install gromp-<version>-full.apk`, or through an app store.

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
./gradlew testFullDebugUnitTest   # parser, focus schedule and lock decision tests
./gradlew assembleFullDebug       # app/build/outputs/apk/full/debug/app-full-debug.apk
./gradlew assembleLiteRelease     # app/build/outputs/apk/lite/release/app-lite-release.apk
```

Every push to `main` runs the GitHub Actions workflow in `.github/workflows/android.yml`. It runs the tests, builds debug and release APKs, checks that the release APK has no `INTERNET` permission, and uploads both APKs as the `gromp-apk` artifact.

**Releases:** bump `versionName` (and `versionCode`) in `app/build.gradle.kts` and push to `main`. The Release workflow builds the APK, tags `v<version>` and publishes it on the GitHub Releases page. If that version already has a release, nothing happens.

**Signing:** add these repository secrets so every release is signed with the same key and updates install over each other: `GROMP_KEYSTORE_BASE64` (the .jks file, base64-encoded), `GROMP_KEYSTORE_PASSWORD`, `GROMP_KEY_ALIAS`, `GROMP_KEY_PASSWORD`. Without them, builds use the development key committed in `signing/dev.keystore`, so updates install over each other. That key is public, so set up your own before distributing Gromp to other people.

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
