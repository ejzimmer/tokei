import "./style.css"
import { SOUNDS } from "./sounds"
import { createTimer, loadTimers, saveTimers, selectedDurationMs, type TimerState } from "./timers"
import {
  closeTimerNotification,
  getAudioContext,
  markTimerStarted,
  markTimerStopped,
  notifyTimerFinished,
  previewSound,
  resumeAudioIfNeeded,
  startAlarmLoop,
  stopAlarmLoop,
} from "./audio"

const TICK_MS = 250

const timers: TimerState[] = loadTimers()

interface CardRefs {
  root: HTMLElement
  nameInput: HTMLInputElement
  display: HTMLInputElement
  lastFinished: HTMLDivElement
  soundSelect: HTMLSelectElement
  startBtn: HTMLButtonElement
  resetBtn: HTMLButtonElement
  ringingBanner: HTMLDivElement
  finishedTime: HTMLSpanElement
}

const cardRefs = new Map<string, CardRefs>()

const app = document.querySelector<HTMLDivElement>("#app")!
app.innerHTML = `
  <header class="header">
    <h1>時計 Tokei</h1>
  </header>
  <main id="timer-list" class="timer-list"></main>
  <footer class="footer">
    <button class="btn btn-secondary" id="add-timer-btn">+ Add timer</button>
    <div class="status" id="global-status"></div>
    <details class="tips">
      <summary>Reliability tips</summary>
      <ul>
        <li>Keep this tab (or installed app) open — don't swipe it away from recent apps.</li>
        <li>Works best on Chrome for Android. Firefox for Android is less reliable — it can unload background tabs more aggressively.</li>
        <li>On a laptop, just don't let it go to sleep (locking the screen alone is fine).</li>
        <li>Install to your home screen for the best experience.</li>
      </ul>
    </details>
  </footer>
`

const listEl = document.querySelector<HTMLDivElement>("#timer-list")!
const addTimerBtn = document.querySelector<HTMLButtonElement>("#add-timer-btn")!
const globalStatus = document.querySelector<HTMLDivElement>("#global-status")!

function pad(n: number): string {
  return n.toString().padStart(2, "0")
}

function formatMs(ms: number): string {
  const totalSeconds = Math.max(0, Math.round(ms / 1000))
  const h = Math.floor(totalSeconds / 3600)
  const m = Math.floor((totalSeconds % 3600) / 60)
  const s = totalSeconds % 60
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

function formatClock(ts: number): string {
  return new Date(ts).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })
}

function soundOptions(selectedId: string): string {
  return SOUNDS.map(
    (s) => `<option value="${s.id}" ${s.id === selectedId ? "selected" : ""}>${s.label}</option>`,
  ).join("")
}

function persist() {
  saveTimers(timers)
}

function clamp(value: number, min: number, max: number): number {
  if (Number.isNaN(value)) return min
  return Math.min(max, Math.max(min, value))
}

// Digit-shift entry, like typing on a microwave: the last two digits typed
// are always seconds, everything before that is minutes. No colon needed,
// so it works cleanly with a numeric-only mobile keyboard.
function parseDigitsToDuration(digits: string): { minutes: number; seconds: number } {
  if (!digits) return { minutes: 0, seconds: 0 }
  const n = parseInt(digits, 10) || 0
  const rawSeconds = n % 100
  const minutesFromDigits = Math.floor(n / 100)
  return {
    minutes: clamp(minutesFromDigits + Math.floor(rawSeconds / 60), 0, 999),
    seconds: rawSeconds % 60,
  }
}

function mountTimerCard(timer: TimerState) {
  const card = document.createElement("section")
  card.className = "timer-card"
  card.innerHTML = `
    <div class="timer-card-header">
      <input class="timer-name" value="${timer.name.replace(/"/g, "&quot;")}" maxlength="40" />
      <button class="icon-btn" data-role="delete" title="Delete timer">&times;</button>
    </div>
    <div class="clock">
      <input
        class="clock-display"
        data-role="display"
        inputmode="numeric"
        autocomplete="off"
        value="${formatMs(selectedDurationMs(timer))}"
      />
    </div>
    <div class="last-finished" data-role="last-finished"></div>
    <div class="sound-row">
      <select class="sound-select" data-role="sound-select">${soundOptions(timer.soundId)}</select>
      <button class="icon-btn" data-role="preview" title="Preview sound">&#9658;</button>
    </div>
    <div class="controls">
      <button class="btn btn-secondary" data-role="reset">Reset</button>
      <button class="btn btn-primary" data-role="start">Start</button>
    </div>
    <div class="ringing-banner" data-role="ringing-banner" hidden>
      <strong>Time's up!</strong>
      <span>Finished at <span data-role="finished-time"></span></span>
      <button class="btn btn-secondary" data-role="stop">Stop</button>
    </div>
  `

  const refs: CardRefs = {
    root: card,
    nameInput: card.querySelector(".timer-name")!,
    display: card.querySelector('[data-role="display"]')!,
    lastFinished: card.querySelector('[data-role="last-finished"]')!,
    soundSelect: card.querySelector('[data-role="sound-select"]')!,
    startBtn: card.querySelector('[data-role="start"]')!,
    resetBtn: card.querySelector('[data-role="reset"]')!,
    ringingBanner: card.querySelector('[data-role="ringing-banner"]')!,
    finishedTime: card.querySelector('[data-role="finished-time"]')!,
  }
  cardRefs.set(timer.id, refs)

  refs.nameInput.addEventListener("change", () => {
    timer.name = refs.nameInput.value.trim() || "Timer"
    persist()
  })

  card.querySelector('[data-role="delete"]')!.addEventListener("click", () => deleteTimer(timer.id))
  refs.startBtn.addEventListener("click", () => onStartPauseClick(timer))
  refs.resetBtn.addEventListener("click", () => resetTimer(timer))
  card.querySelector('[data-role="stop"]')!.addEventListener("click", () => stopAlarm(timer))
  card.querySelector('[data-role="preview"]')!.addEventListener("click", () => previewSound(timer.soundId))

  refs.soundSelect.addEventListener("change", () => {
    timer.soundId = refs.soundSelect.value
    persist()
  })

  refs.display.addEventListener("focus", () => {
    if (timer.status !== "idle") return
    refs.display.select()
  })

  refs.display.addEventListener("input", () => {
    if (timer.status !== "idle") return
    const digits = refs.display.value.replace(/\D/g, "")
    const { minutes, seconds } = parseDigitsToDuration(digits)
    timer.minutes = minutes
    timer.seconds = seconds
    persist()
    refs.display.value = formatMs(selectedDurationMs(timer))
    const end = refs.display.value.length
    refs.display.setSelectionRange(end, end)
  })

  refs.display.addEventListener("keydown", (e) => {
    if (e.key === "Enter") refs.display.blur()
  })

  refs.display.addEventListener("blur", () => updateCardUI(timer))

  listEl.appendChild(card)
  updateCardUI(timer)
}

function updateCardUI(timer: TimerState) {
  const refs = cardRefs.get(timer.id)
  if (!refs) return

  refs.root.dataset.status = timer.status
  refs.ringingBanner.hidden = timer.status !== "ringing"
  refs.display.classList.toggle("running", timer.status === "running")
  refs.display.readOnly = timer.status !== "idle"

  if (timer.status === "idle") {
    refs.display.value = formatMs(selectedDurationMs(timer))
    refs.startBtn.textContent = "Start"
    refs.startBtn.disabled = false
  } else if (timer.status === "paused") {
    refs.display.value = formatMs(timer.pausedRemainingMs ?? 0)
    refs.startBtn.textContent = "Resume"
    refs.startBtn.disabled = false
  } else if (timer.status === "running" && timer.endAt !== null) {
    refs.display.value = formatMs(timer.endAt - Date.now())
    refs.startBtn.textContent = "Pause"
    refs.startBtn.disabled = false
  } else if (timer.status === "ringing") {
    refs.display.value = "00:00"
    refs.startBtn.disabled = true
    if (timer.finishedAt !== null) refs.finishedTime.textContent = formatClock(timer.finishedAt)
  }

  refs.lastFinished.textContent =
    timer.status !== "ringing" && timer.lastFinishedAt
      ? `Last finished at ${formatClock(timer.lastFinishedAt)}`
      : ""
}

function onStartPauseClick(timer: TimerState) {
  if (timer.status === "idle" || timer.status === "paused") {
    startTimer(timer)
  } else if (timer.status === "running") {
    pauseTimer(timer)
  }
}

function startTimer(timer: TimerState) {
  const durationMs = timer.pausedRemainingMs ?? selectedDurationMs(timer)
  if (durationMs <= 0) return
  // Must happen inside this click handler: browsers only allow starting or
  // resuming an AudioContext in direct response to a user gesture.
  getAudioContext()

  timer.endAt = Date.now() + durationMs
  timer.pausedRemainingMs = null
  timer.lastFinishedAt = null
  timer.status = "running"
  markTimerStarted()
  persist()
  updateCardUI(timer)
}

function pauseTimer(timer: TimerState) {
  if (timer.endAt === null) return
  timer.pausedRemainingMs = Math.max(0, timer.endAt - Date.now())
  timer.endAt = null
  timer.status = "paused"
  markTimerStopped()
  persist()
  updateCardUI(timer)
}

function resetTimer(timer: TimerState) {
  if (timer.status === "running") markTimerStopped()
  if (timer.status === "ringing") {
    stopAlarmLoop(timer.id)
    void closeTimerNotification(timer.id)
  }
  timer.status = "idle"
  timer.endAt = null
  timer.pausedRemainingMs = null
  timer.finishedAt = null
  persist()
  updateCardUI(timer)
}

function stopAlarm(timer: TimerState) {
  if (timer.status !== "ringing") return
  stopAlarmLoop(timer.id)
  void closeTimerNotification(timer.id)
  timer.status = "idle"
  timer.finishedAt = null
  persist()
  updateCardUI(timer)
}

function handleTimerFinished(timer: TimerState) {
  timer.status = "ringing"
  timer.finishedAt = timer.endAt
  timer.lastFinishedAt = timer.endAt
  timer.endAt = null
  markTimerStopped()
  startAlarmLoop(timer.id, timer.soundId)
  void notifyTimerFinished(timer.id, timer.name)
  persist()
  updateCardUI(timer)
}

function deleteTimer(id: string) {
  const index = timers.findIndex((t) => t.id === id)
  if (index === -1) return
  const timer = timers[index]
  if (timer.status === "running") markTimerStopped()
  if (timer.status === "ringing") {
    stopAlarmLoop(timer.id)
    void closeTimerNotification(timer.id)
  }
  timers.splice(index, 1)
  cardRefs.get(id)?.root.remove()
  cardRefs.delete(id)
  persist()
}

addTimerBtn.addEventListener("click", () => {
  const timer = createTimer(`Timer ${timers.length + 1}`)
  timers.push(timer)
  mountTimerCard(timer)
  persist()
})

function globalTick() {
  const now = Date.now()
  for (const timer of timers) {
    if (timer.status === "running" && timer.endAt !== null) {
      if (timer.endAt <= now) {
        handleTimerFinished(timer)
      } else {
        updateCardUI(timer)
      }
    }
  }
}

setInterval(globalTick, TICK_MS)

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState !== "visible") return
  resumeAudioIfNeeded()
  globalTick()
})

// Autoplay policies can leave the AudioContext suspended until any gesture
// happens on the page — cheap safety net so restored/ringing timers regain
// sound as soon as the phone is touched.
document.addEventListener("pointerdown", () => resumeAudioIfNeeded())

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.addEventListener("message", (event) => {
    if (event.data?.type !== "stop-alarm") return
    const timer = timers.find((t) => t.id === event.data.timerId)
    if (timer) stopAlarm(timer)
  })
}

function renderStatus() {
  if ("Notification" in window && Notification.permission === "default") {
    globalStatus.innerHTML = `<button id="enable-notifications">Enable notifications</button> for alerts you can dismiss from the lock screen.`
    document.querySelector("#enable-notifications")!.addEventListener("click", () => {
      void Notification.requestPermission().then(renderStatus)
    })
  } else if ("Notification" in window && Notification.permission === "denied") {
    globalStatus.textContent = "Notifications are blocked — you'll still get sound/vibration while the tab is open."
  } else {
    globalStatus.textContent = ""
  }
}

for (const timer of timers) {
  mountTimerCard(timer)
  if (timer.status === "running") markTimerStarted()
  if (timer.status === "ringing") startAlarmLoop(timer.id, timer.soundId)
}
renderStatus()
