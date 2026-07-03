# Tokei (時計)

A timer PWA built to keep ringing even when your Android phone is locked.

## Why this needs tricks at all

Browsers freeze JavaScript in backgrounded tabs to save battery. The one
reliable exemption (documented for Chrome, and present but less consistent
in Firefox) is that a tab **actively playing audio** doesn't get frozen. So
while any timer is running, this app plays a low-frequency, very-low-gain
Web Audio tone in the background — quiet enough that you shouldn't notice
it, but enough to keep the tab (and its timers) alive while the screen is
off. All countdowns are tracked by absolute end timestamp (`Date.now() +
duration`), not by counting ticks, so even if a tick gets delayed the alarm
still fires at the right time once the interval runs again.

When a timer finishes it plays a repeating chime (synthesized in-browser —
no bundled audio files, no licensing to track), vibrates the phone, and
shows a system notification with a **Stop** action. Tapping Stop messages
the page directly via the service worker, so you can silence the alarm from
the lock screen without unlocking the phone.

## Reliability notes

- **Chrome for Android** is the best-supported target — this is where the
  audio-exemption behavior is most consistently documented.
- **Firefox for Android** implements a similar exemption, but is also more
  aggressive about fully unloading backgrounded tabs, which no in-page trick
  can survive. Less reliable for anything time-critical.
- **Desktop** (macOS/Windows/Linux) doesn't need any of this — background
  tabs aren't frozen the same way. The only thing that stops a timer there
  is the machine actually going to sleep (e.g. a laptop lid closing).
- No web technique is airtight against OEM-specific aggressive battery
  managers (some Android skins kill backgrounded browsers regardless).
  Installing to the home screen and exempting the browser from battery
  optimization in Android settings improves reliability further.

## Development

```bash
npm install
npm run dev       # dev server (service worker is disabled in dev mode)
npm run build     # typecheck + production build to build/
npm run preview   # serve the production build locally, service worker included
```

The service worker is only active in the production build — use
`npm run preview` if you need to test notifications or offline behavior.
