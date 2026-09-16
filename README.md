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

## The Work timer

A fixed timer pinned to the top of the list, for tracking a Tuesday-to-Friday
work week of 7.5-hour days. Unlike the other timers it doesn't ring and stop
at zero -- it books the finished day and rolls straight into the next one, so
it can run continuously through a long day.

- **It counts toward a specific day.** The card always says which one
  ("Counting down for Wednesday"). Pass 7.5 hours on Tuesday and the
  overflow starts counting toward Wednesday; stop 2.5 hours short and
  Wednesday starts by finishing Tuesday off before Wednesday's own 7.5
  begins. You can be several days ahead or behind either way.
- **A day you never start it is a day off.** Days only start owing hours when
  the timer is actually started on them, so a skipped day costs nothing and
  the running total simply carries over -- 5 hours on Tuesday and nothing on
  Wednesday leaves 10 hours to work on Thursday, not 17.5.
- **Stop and start freely.** Stopping banks what's left of the cycle rather
  than discarding it. While stopped the duration is editable, so forgetting
  to start or stop is fixed by correcting the number. An adjustment applies
  to the cycle you're in; every later cycle is a full 7.5 hours again.
- **Two nudges.** Not started by 8:30 on a Tuesday-to-Friday sends a
  reminder; the weekend and Monday are skipped outright. Still running at
  18:30 asks whether you forgot to stop -- that one has no day filter
  because it only speaks up when the timer is genuinely counting, so a day
  off stays quiet on its own.

The schedule lives in `data/WorkSchedule.kt` (work days, cycle length, both
reminder times) and the day-by-day ledger in `data/WorkState.kt`.

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
