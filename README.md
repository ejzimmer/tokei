# Tokei (時計)

A native Android timer app, rewritten from an earlier PWA version once it
became clear a web app couldn't reliably ring through a locked screen.

## Why native fixes it

The web version kept a tab alive by playing an inaudible tone, hoping the
browser wouldn't freeze its background timers. That's inherently a best
effort. This version instead uses:

- **`AlarmManager.setExactAndAllowWhileIdle`** to schedule each timer as a
  real OS-level alarm — it fires at the exact moment even if the app has
  been killed, the screen is locked, or the device is in Doze.
- **A `BroadcastReceiver`** (`AlarmReceiver`) that the OS wakes at that exact
  moment, which persists the "finished" state and starts...
- **A foreground `Service`** (`AlarmService`) that actually plays the alarm
  (synthesized tones via `AudioTrack`, looped natively — no re-scheduling
  logic that could leave sound playing after Stop) and shows a notification
  with a **Stop** action.
- **A boot receiver** that reschedules any still-running timer's alarm after
  a reboot, since `AlarmManager` alarms don't survive one.

The sounds, duration-entry UX (three digit-shift hours/minutes/seconds
fields), and daily run count are carried over from the web version's design.

## Before you build: two permissions to grant on your phone

The app will prompt for both on first launch, but they're worth knowing
about up front:

1. **Notifications** — a normal runtime permission (Android 13+). Without
   it you still get sound + vibration, just no lock-screen notification or
   Stop button there.
2. **Alarms & reminders** ("exact alarms", Android 12+) — this one can't be
   granted from a permission dialog; the app links you to
   Settings → Apps → Tokei → Alarms & reminders. Without it, timers still
   fire, just via an inexact alarm that the OS can delay by a few minutes
   under battery optimization.

## Building it

This was written entirely by hand in this sandboxed session, which has no
access to the Android SDK or Google's Maven repository (the network policy
here blocks `dl.google.com`), so **it could not be compiled or run here** —
no emulator, no `./gradlew build`. Every file was written and reviewed
carefully, but Android Studio's first sync is the real first compile.

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. Open this folder as a project. Studio will download the Android SDK,
   Gradle distribution, and dependencies automatically on first sync.
3. Connect your phone over USB with
   [USB debugging](https://developer.android.com/studio/debug/dev-options)
   enabled, and hit Run — this installs and launches it directly.

If the first sync flags a dependency version as unavailable (`gradle/libs.versions.toml`
pins specific AGP/Kotlin/Compose BOM versions as of late 2024), Studio's
"Upgrade Assistant" will offer current ones — accept it, that's expected
and not a sign anything else is wrong.

## Installing without Android Studio (just a `.apk`)

If you'd rather not set up Studio at all:

```
./gradlew assembleDebug
```

produces `app/build/outputs/apk/debug/app-debug.apk`. Copy that file to your
phone (email, cloud drive, USB, whatever's easiest), enable "Install unknown
apps" for whichever app you used to open it (Android will prompt you the
first time), and tap the file to install. No Play Store, no signing service,
no review — this is exactly the same sideloading path any personal APK uses.
