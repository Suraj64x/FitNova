# Fit Nova

A minimal, fast fitness app for Android. It runs well on low-end phones.

- **Camera form check:** a live skeleton overlay counts your reps and tells you whether your posture is good ("Hips sagging", "Squat deeper", …). Pose detection runs on the device with ML Kit.
- **27 exercises** across upper body, lower body, core and cardio, each with a stick-figure icon. 23 of them have camera tracking.
- **Dashboard:** today's points ring, streaks, weekly bars, a 30-day trend, a form-score trend and your exercise mix.
- **Nutrition:** calorie and macro targets (Mifflin–St Jeor formula), a meal log, and researched recipes, mostly Indian plus some fitness staples.
- **Leaderboard:** global rankings by week, month or all time, with a podium for the top three.
- **Offline-first:** workouts save on the phone and sync to Supabase in the background.

## Build

Requirements: Android Studio 2026.2 or newer (JDK 17+), and Android SDK 37.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The build produces one APK per CPU type (arm64-v8a, armeabi-v7a, x86_64), in `app/build/outputs/apk/`. Release builds are about 25–30 MB.

## Backend (Supabase)

Accounts use the existing `nova-auth` edge function, so usernames from the old app keep working.

Workouts and the leaderboard need the tables in `supabase/migrations/20261107000000_fitnova_m1.sql`. The migration only adds new `fn_*` tables and functions; it doesn't touch existing ones. To apply it, either:
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
