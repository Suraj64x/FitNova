# Fit Nova rebuild: M1 design

Approved in chat, 2026-11-07. A rebuild of the ChatGPT-made Fit Nova 0.9.0 (`in.fitnova.app`) as a light, minimal Android app.

## Goal
A smooth, professional fitness app that also runs well on low-end phones. It has:
- a dashboard
- workout choices
- a camera skeleton that says whether your posture is good
- offline-first sync
- leaderboards

It uses the existing Supabase project, so existing accounts keep working.

## Milestones
- **M1 (this spec):** design system, dashboard, workouts, camera form check, offline storage, login, sync, global leaderboard.
- **M2:** custom groups, communities, group and community leaderboards, badges.
- **M3:** alarm challenges, sleep log, nutrition (ported from the old app).

## Decisions
| Area | Choice | Why |
|---|---|---|
| UI | Compose + Material 3 with a custom flat theme: one accent (#4F7DF3), hairline borders, no glow or shadow, system font | Professional look, small app |
| Icons | ~15 hand-picked vector drawables | The old app bundled a 41 MB icon pack |
| Package | `app.fitnova` (new id) | Installs next to the old app, and `in` is a Kotlin keyword |
| Pose | ML Kit pose (base model) + CameraX `MlKitAnalyzer` in view coordinates, 640x480, drops stale frames | Fast on low-end phones; the overlay draws the points as they come |
| Form rules | Pure Kotlin (`pose/Coach.kt`): joint angles, rep counting that only counts a rep after a full down-and-up, body-relative hip line | Unit-testable; works in any phone orientation |
| Storage | Platform SQLite, no Room | No annotation processing; schema is tiny |
| Network | `HttpURLConnection` + `org.json` | Both built into Android, so no extra libraries |
| Background sync | Platform `JobScheduler` with a network constraint | No WorkManager dependency |
| Tokens | Encrypted with an Android Keystore AES-GCM key; `allowBackup=false` | Tokens stay on the device |
| APK size | Separate APK per CPU type (arm64, armv7, x86_64), plus code and resource shrinking in release builds | 146 MB → about 25 MB per device |

## Backend contract
- Accounts: `POST /functions/v1/nova-auth` with `{action: login|register, username, password, confirm_password}`, which returns `{access_token, refresh_token, user{id}, recovery_code?}`. Tokens are refreshed through `/auth/v1/token?grant_type=refresh_token`. This contract was recovered from the old APK.
- New tables, added without touching existing ones (`supabase/migrations/20261107000000_fitnova_m1.sql`):
  - `fn_workouts`: each user can only read and write their own rows.
  - `fn_profiles`: display names.
  - `fn_points()` and `fn_leaderboard(period, tz)`, which returns the top 100.
- Points = reps × form%. Plank scores 1 point per 2 seconds. Manual workouts count half. The same formula exists in the app (`domain/Workout.kt`) and in SQL (`fn_points`).

## Sync flow
1. Save the workout locally.
2. Upload pending rows (upsert on `id`).
3. Pull the account's history.

Sync runs on resume (if there's something to upload or the data is stale), after saving, and from the "Sync now" button. If the phone is offline or the server returns an error, `SyncJob` retries once the network is back.

## Testing
- JVM unit tests: `CoachTest` (rep counting, partial reps, sagging or piked hips, plank timing, jumping jacks) and `StatsTest` (streak, week chart, points formula).
- Manual run on the Pixel_10a emulator. The skeleton needs a webcam or a real phone.

## Scope update (same day, at the user's request)
- **27 exercises** grouped into 4 categories.
  - Each one has a stick-figure icon drawn in code (no image files).
  - Posture rules come from a small set of shared rule families (`Rule` in `domain/Workout.kt`).
  - Moves without a reliable camera signal (burpees, crunches, calf raises, jump rope) are counted manually.
- **Nutrition** (pulled forward from M3):
  - Targets from the Mifflin–St Jeor formula.
  - Meal log stored on the phone (SQLite, schema v2).
  - Researched recipes in `assets/recipes.json`, filterable by meal type, veg only and high protein.
  - A recipe pop-up with a macro donut chart, ingredients, steps and the source link.
- **Dashboard:**
  - Today's points ring against a daily goal (set in Profile).
  - Streak and best-streak cards.
  - Week bars or a 30-day line chart.
  - A 14-day form-score trend.
  - Exercise mix.
- **UI:** larger type scale, pop-up messages (snackbar) for confirmations, a leaderboard podium, and a fifth "Food" tab.
- **Points:** now based on reps (holds store `reps = 0`), so the server needs no list of exercise ids. The `exercise` column is checked against a pattern instead.

## Not in M1 (deliberately)
Room, navigation library, DI framework, WorkManager, images or custom fonts, pull-to-refresh, server-side anti-cheat on reported reps.
