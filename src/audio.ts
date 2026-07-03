import { soundById } from "./sounds"

// A near-silent, low-frequency Web Audio tone — not music, not a media file.
// Browsers exempt tabs that are audibly playing something from the
// background-tab freezing that would otherwise stop our countdown timers
// while the screen is off/locked. Kept quiet and low-pitched (most phone
// speakers barely reproduce it) so in practice it's inaudible.
const KEEP_ALIVE_HZ = 20
const KEEP_ALIVE_GAIN = 0.05

const VIBRATE_PATTERN = [500, 200, 500, 200, 500]

let audioCtx: AudioContext | null = null
let keepAliveOsc: OscillatorNode | null = null
let keepAliveGain: GainNode | null = null
let runningCount = 0

const alarmLoops = new Map<string, ReturnType<typeof setInterval>>()

export function getAudioContext(): AudioContext {
  if (!audioCtx) {
    audioCtx = new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)()
  }
  if (audioCtx.state === "suspended") void audioCtx.resume()
  return audioCtx
}

export function resumeAudioIfNeeded() {
  if (audioCtx?.state === "suspended") void audioCtx.resume()
}

export function markTimerStarted() {
  runningCount += 1
  if (runningCount === 1) startKeepAliveTone()
}

export function markTimerStopped() {
  runningCount = Math.max(0, runningCount - 1)
  if (runningCount === 0) stopKeepAliveTone()
}

function startKeepAliveTone() {
  const ctx = getAudioContext()
  stopKeepAliveTone()
  keepAliveOsc = ctx.createOscillator()
  keepAliveGain = ctx.createGain()
  keepAliveOsc.frequency.value = KEEP_ALIVE_HZ
  keepAliveGain.gain.value = KEEP_ALIVE_GAIN
  keepAliveOsc.connect(keepAliveGain).connect(ctx.destination)
  keepAliveOsc.start()
}

function stopKeepAliveTone() {
  keepAliveOsc?.stop()
  keepAliveOsc?.disconnect()
  keepAliveGain?.disconnect()
  keepAliveOsc = null
  keepAliveGain = null
}

export function previewSound(soundId: string) {
  const ctx = getAudioContext()
  soundById(soundId).play(ctx, ctx.destination)
}

export function startAlarmLoop(timerId: string, soundId: string) {
  stopAlarmLoop(timerId)
  const ctx = getAudioContext()
  const sound = soundById(soundId)
  sound.play(ctx, ctx.destination)
  const handle = setInterval(() => sound.play(ctx, ctx.destination), sound.periodMs)
  alarmLoops.set(timerId, handle)
  if (navigator.vibrate) navigator.vibrate(VIBRATE_PATTERN)
}

export function stopAlarmLoop(timerId: string) {
  const handle = alarmLoops.get(timerId)
  if (handle) clearInterval(handle)
  alarmLoops.delete(timerId)
}

export async function notifyTimerFinished(timerId: string, name: string) {
  if (Notification.permission !== "granted" || !("serviceWorker" in navigator)) return
  try {
    const reg = await navigator.serviceWorker.ready
    await reg.showNotification(`${name} — time's up!`, {
      body: "Tap Stop to silence the alarm.",
      tag: `tokei-${timerId}`,
      requireInteraction: true,
      vibrate: VIBRATE_PATTERN,
      data: { timerId },
      actions: [{ action: "stop", title: "Stop" }],
    } as NotificationOptions)
  } catch {
    // Notifications are a bonus signal; the on-screen alarm still works.
  }
}

export async function closeTimerNotification(timerId: string) {
  if (!("serviceWorker" in navigator)) return
  try {
    const reg = await navigator.serviceWorker.ready
    const notifications = await reg.getNotifications({ tag: `tokei-${timerId}` })
    notifications.forEach((n) => n.close())
  } catch {
    // Best-effort cleanup.
  }
}
