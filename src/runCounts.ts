// How many times each timer has been (freshly) started today. Intentionally
// in-memory only — resets on reload and never leaves this tab, no need for
// localStorage or any cross-device sync.
const counts = new Map<string, { day: string; count: number }>()

function todayKey(): string {
  const d = new Date()
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
}

export function recordRun(timerId: string) {
  const today = todayKey()
  const existing = counts.get(timerId)
  counts.set(timerId, { day: today, count: existing && existing.day === today ? existing.count + 1 : 1 })
}

export function getRunCount(timerId: string): number {
  const existing = counts.get(timerId)
  return existing && existing.day === todayKey() ? existing.count : 0
}

export function clearRunCount(timerId: string) {
  counts.delete(timerId)
}
