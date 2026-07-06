import { SOUNDS } from "./sounds"

export type TimerStatus = "idle" | "running" | "paused" | "ringing"

export interface TimerState {
  id: string
  name: string
  hours: number
  minutes: number
  seconds: number
  soundId: string
  status: TimerStatus
  /** Absolute ms timestamp this timer is due to finish. Only set while running. */
  endAt: number | null
  /** Remaining ms, captured when paused. */
  pausedRemainingMs: number | null
  /** Absolute ms timestamp this timer finished, while status is "ringing". */
  finishedAt: number | null
  /** Kept after Stop so the card can still show when it last went off. */
  lastFinishedAt: number | null
}

const STORAGE_KEY = "tokei.timers.v1"

export function selectedDurationMs(timer: TimerState): number {
  return ((timer.hours * 60 + timer.minutes) * 60 + timer.seconds) * 1000
}

/** Carries overflowing seconds into minutes, and overflowing minutes into hours. */
export function normalizeDuration(timer: Pick<TimerState, "hours" | "minutes" | "seconds">) {
  if (timer.seconds >= 60) {
    timer.minutes += Math.floor(timer.seconds / 60)
    timer.seconds = timer.seconds % 60
  }
  if (timer.minutes >= 60) {
    timer.hours += Math.floor(timer.minutes / 60)
    timer.minutes = timer.minutes % 60
  }
}

let nextSoundIndex = 0

export function createTimer(name: string): TimerState {
  const sound = SOUNDS[nextSoundIndex % SOUNDS.length]
  nextSoundIndex += 1
  return {
    id: crypto.randomUUID(),
    name,
    hours: 0,
    minutes: 5,
    seconds: 0,
    soundId: sound.id,
    status: "idle",
    endAt: null,
    pausedRemainingMs: null,
    finishedAt: null,
    lastFinishedAt: null,
  }
}

export function loadTimers(): TimerState[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return [createTimer("Timer 1")]
    const parsed = JSON.parse(raw) as TimerState[]
    if (!Array.isArray(parsed) || parsed.length === 0) return [createTimer("Timer 1")]

    // A timer left "running" while the page was fully closed/evicted has no
    // live JS to notice it elapsed. Catch it up now, on load.
    const now = Date.now()
    return parsed.map((timer) => {
      const withHours: TimerState = { ...timer, hours: timer.hours ?? 0 }
      if (withHours.status === "running" && withHours.endAt !== null && withHours.endAt <= now) {
        return { ...withHours, status: "ringing" as const, finishedAt: withHours.endAt }
      }
      return withHours
    })
  } catch {
    return [createTimer("Timer 1")]
  }
}

export function saveTimers(timers: TimerState[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(timers))
}
