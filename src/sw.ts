/// <reference lib="webworker" />
import { cleanupOutdatedCaches, precacheAndRoute } from "workbox-precaching"
import { clientsClaim } from "workbox-core"

declare const self: ServiceWorkerGlobalScope

self.skipWaiting()
clientsClaim()
cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)

// Lets the "Stop" action on a finished-timer notification silence the alarm
// without needing to unlock the phone and switch to the app: if the page is
// still alive in the background (the whole point of the keep-alive trick),
// we can message it directly. Only if the page has been fully evicted do we
// fall back to opening a window.
self.addEventListener("notificationclick", (event: NotificationEvent) => {
  const timerId = event.notification.data?.timerId as string | undefined
  event.notification.close()

  event.waitUntil(
    (async () => {
      const clientList = await self.clients.matchAll({ type: "window", includeUncontrolled: true })
      if (clientList.length > 0) {
        for (const client of clientList) {
          client.postMessage({ type: "stop-alarm", timerId })
        }
        if (event.action !== "stop") {
          const focusable = clientList.find(
            (c): c is WindowClient => "focus" in c,
          )
          await focusable?.focus()
        }
      } else {
        await self.clients.openWindow("/")
      }
    })(),
  )
})
