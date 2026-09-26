# TimeBoxr

A simple Android Pomodoro timer: work in focused blocks, take short breaks, and
keep control of the timer even when your phone is locked.

## Current features

- Default 30 min work / 1 min break cycle.
- Work and break durations are adjustable in-app, and the last values you set
  are remembered (persisted with Jetpack DataStore).
- Timer keeps running in a foreground service, so it survives the app being
  backgrounded or the screen locking.
- An ongoing, lock-screen-visible notification shows the running phase and
  time left, with **Pause / Resume** and **Stop** buttons you can tap without
  unlocking the phone.
- When a work or break timer reaches zero, an alarm sound plays (see
  **Settings** below for break-specific behavior), and — like an alarm
  clock — the screen wakes up and a full-screen Complete/Stop screen pops up
  over the lock screen, so you don't have to notice a silent notification and
  unlock the phone yourself. The alarm auto-stops after 60 seconds if you're
  away from your phone, instead of ringing indefinitely.
- Tapping **Complete** automatically starts the next phase (work → break,
  break → work).
- **Settings screen** (gear icon, top right):
  - Pick a custom alert sound for TimeBoxr from any ringtone/notification
    sound on your phone. This only changes TimeBoxr's own alert — it does not
    touch your phone's system alarm sound.
  - Toggle whether a finished **break** plays that sound at all — off by
    default, so a break ending only vibrates; a finished **work** session
    always alerts.
  - Set a daily goal (in hours, default 8, matching a typical workday).
- **Daily time tracker**: the bottom of the main screen shows today's total
  work + break time against your daily goal, with a progress bar. Resets
  automatically at midnight.

## Ideas for later (not built yet)

- Long break after N pomodoros.
- Home-screen widget.
- Weekly/monthly history (today's tracker only keeps the current day).

(Add more here as they come up — this file is a good place to track them.)

## Why Kotlin + Jetpack Compose

This is a native Android app (Kotlin + Jetpack Compose), not Flutter or React
Native. The reasoning:

- The core feature — lock-screen status with working Pause/Stop controls — is
  really an Android **foreground service + notification actions**. That's a
  first-class, well-documented Android API. On Flutter/RN it means bridging
  through a plugin (e.g. `flutter_foreground_task`) to reach the same native
  APIs anyway, which adds a layer without adding a real benefit since this
  app has no cross-platform (iOS) requirement.
- A countdown that must keep running reliably while the screen is locked is
  exactly the kind of thing that's simplest to get right when you're writing
  directly against Android's `Service`/`Notification` APIs instead of through
  a cross-platform bridge.
- Compose keeps the UI code small and modern without needing XML layouts.

## Project structure

```
app/src/main/java/com/selman/timeboxr/
  TimerService.kt        Foreground service: countdown, notification, alarm sound
  TimerViewModel.kt       Bridges the UI to the service
  SettingsRepository.kt   Persists work/break minutes (DataStore)
  TimerPhase.kt           Enums + the shared timer state shape
  MainActivity.kt         Hosts the Compose UI, requests notification permission
  ui/TimerScreen.kt        The screen itself
  ui/theme/                Material 3 theme
```

## Getting it running on your device

1. Open this folder (`TimeBoxr/`) in Android Studio (Hedgehog or newer).
   Let it sync — Android Studio will use the Gradle wrapper already checked
   into this repo (`gradlew` / `gradle/wrapper/`), no local Gradle install
   needed. If it ever complains the wrapper jar is missing, running
   `./gradlew tasks` once, or a Gradle sync in Android Studio, regenerates it.
2. Connect your physical device over USB with USB debugging enabled, and
   select it as the run target.
3. Run the app. On first launch it will ask for notification permission
   (needed to show the lock-screen timer) — allow it. On Android 14+ you'll
   also see a banner in the app saying screen wake-up is off — tap
   **Enable in Settings** and turn it on there (Android requires this to be
   granted manually per-app; it can't be requested with a normal permission
   prompt).
4. **Recommended:** exempt the app from battery optimization
   (Settings → Apps → TimeBoxr → Battery → Unrestricted). Without this,
   Android may occasionally delay the countdown or kill the service on some
   OEM skins (Xiaomi/Samsung/etc. are the usual offenders).

## Requirements

- Android Studio with the Android SDK (compileSdk/targetSdk 34).
- minSdk 26 (Android 8.0+).
