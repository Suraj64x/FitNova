# Fit Nova

A minimal, fast fitness app for Android. It runs well on low-end phones.

- **Camera form check:** a live skeleton overlay counts your reps and tells you whether your posture is good ("Hips sagging", "Squat deeper", …). Pose detection runs on the device with ML Kit.
- **27 exercises** across upper body, lower body, core and cardio, each with a stick-figure icon. 23 of them have camera tracking.
- **Dashboard:** today's points ring, streaks, weekly bars, a 30-day trend, a form-score trend and your exercise mix.
- **Nutrition:** calorie and macro targets (Mifflin–St Jeor formula), a meal log, and researched recipes, mostly Indian plus some fitness staples.
- **Leaderboards:** global rankings, plus private **groups** (6-character invite codes) and public **clubs**, each with its own board and a top-3 podium.
- **Badges:** 12 achievements (streaks, reps, perfect form, early bird…) worked out from your history.
- **Offline-first:** workouts save on the phone and sync to Supabase in the background.

## Download

Get the latest APK from [Releases](https://github.com/Suraj64x/FitNova/releases/latest):

| File | For |
|---|---|
| `FitNova-1.4-arm64.apk` | Most phones (2017+) — recommended |
| `FitNova-1.4-armv7.apk` | Older / low-end phones |
| `FitNova-1.4-universal.apk` | Any phone, if unsure (bigger) |

Requires Android 8.0+. Allow "install unknown apps" when prompted. Installs alongside the old Fit Nova app.

## Build

Requirements: Android Studio 2026.2 or newer (JDK 17+), and Android SDK 37.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The build produces one APK per CPU type (arm64-v8a, armeabi-v7a, x86_64) plus a universal APK, in `app/build/outputs/apk/`. Release builds are about 27–33 MB per CPU type.

## Backend (Supabase)

Accounts use the existing `nova-auth` edge function, so usernames from the old app keep working.

Run both migrations in order: `20261107000000_fitnova_m1.sql` (workouts, global leaderboard), then `20261108000000_fitnova_m2.sql` (groups, clubs, group boards). They only add new `fn_*` tables and functions and don't touch existing ones. To apply them, either:
- paste it into the Supabase SQL editor, or
- run `supabase db push`.

## Layout

```
app/src/main/java/app/fitnova/
  domain/   exercises, points, stats, nutrition targets (pure Kotlin)
  pose/     posture rules and rep counting (Coach.kt), camera and skeleton overlay
  data/     SQLite store, Supabase API, encrypted session, background sync
  ui/       Compose screens, charts, stick-figure exercise art
```

Posture rules and stats are plain Kotlin and are covered by JVM unit tests (`app/src/test`).
