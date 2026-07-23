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

## Getting the APK — no Android Studio needed

`.github/workflows/build-apk.yml` builds the app on every push to `main`
(GitHub's own runners have the Android SDK preinstalled, so this needs
nothing from you). To get the file:

1. Open the repo on GitHub → **Actions** tab → the latest **Build debug
   APK** run.
2. Scroll to **Artifacts** and download `tokei-debug-apk` (a zip containing
   `app-debug.apk`).
3. Unzip it, copy `app-debug.apk` to your phone (email, cloud drive, USB —
   whatever's easiest), and tap it to install. Android will ask you to
   enable "install unknown apps" for whichever app you opened it with the
   first time; that's expected.

No Play Store, no signing service, no review — this is exactly the same
sideloading path any personal APK uses. If you'd rather trigger a build
manually instead of pushing a change, the Actions tab has a "Run workflow"
button on this workflow (`workflow_dispatch`).

## If you do want to build it yourself

This was written entirely by hand in a sandboxed session with no access to
the Android SDK or Google's Maven repository, so it was never compiled
until the GitHub Actions workflow above ran it for the first time. If you
have (or want) Android Studio locally:

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. Open this folder as a project — Studio downloads the SDK, Gradle, and
   dependencies automatically on first sync.
3. Connect your phone over USB with
   [USB debugging](https://developer.android.com/studio/debug/dev-options)
   enabled, and hit Run.

Or, with just a JDK and the Android command-line tools (no full IDE):
`./gradlew assembleDebug` produces the same
`app/build/outputs/apk/debug/app-debug.apk` that the CI workflow builds.
