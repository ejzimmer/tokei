export interface ChimeSound {
  id: string
  label: string
  /** How long to wait before repeating this chime while a timer is ringing. */
  periodMs: number
  /** The full 10-15s alarm phrase, used while a timer is ringing. Returns the
   * oscillators it scheduled so the alarm can be cancelled mid-phrase. */
  play: (ctx: AudioContext, destination: AudioNode) => OscillatorNode[]
  /** A single short ding, used for the "preview sound" button. */
  preview: (ctx: AudioContext, destination: AudioNode) => OscillatorNode[]
}

interface NoteEvent {
  atMs: number
  freq: number
  durationMs: number
  gain: number
  type: OscillatorType
}

function tone(
  ctx: AudioContext,
  destination: AudioNode,
  freq: number,
  startTime: number,
  duration: number,
  opts: { type?: OscillatorType; gain?: number; attack?: number } = {},
): OscillatorNode {
  const { type = "sine", gain = 0.3, attack = 0.012 } = opts
  const osc = ctx.createOscillator()
  const env = ctx.createGain()
  osc.type = type
  osc.frequency.value = freq
  env.gain.setValueAtTime(0, startTime)
  env.gain.linearRampToValueAtTime(gain, startTime + attack)
  env.gain.exponentialRampToValueAtTime(0.0001, startTime + duration)
  osc.connect(env).connect(destination)
  osc.start(startTime)
  osc.stop(startTime + duration + 0.05)
  return osc
}

function scheduleNotes(ctx: AudioContext, dest: AudioNode, notes: NoteEvent[]): OscillatorNode[] {
  const t0 = ctx.currentTime
  return notes.map((n) => tone(ctx, dest, n.freq, t0 + n.atMs / 1000, n.durationMs / 1000, { gain: n.gain, type: n.type }))
}

function spanMs(notes: NoteEvent[]): number {
  return Math.max(...notes.map((n) => n.atMs + n.durationMs))
}

function sequence(
  freqs: number[],
  opts: { spacingMs: number; durationMs: number; gain: number; type?: OscillatorType; offsetMs?: number },
): NoteEvent[] {
  const { spacingMs, durationMs, gain, type = "sine", offsetMs = 0 } = opts
  return freqs.map((freq, i) => ({ atMs: offsetMs + i * spacingMs, freq, durationMs, gain, type }))
}

// A short riff repeated a few times (with a small gap at the end of each
// repeat) so the pattern reads as one longer, evolving phrase rather than a
// single note looping forever.
function repeatedRiff(
  freqs: number[],
  opts: { spacingMs: number; durationMs: number; gain: number; type?: OscillatorType; riffPeriodMs: number; repeats: number },
): NoteEvent[] {
  const { riffPeriodMs, repeats, ...seqOpts } = opts
  const notes: NoteEvent[] = []
  for (let r = 0; r < repeats; r++) {
    notes.push(...sequence(freqs, { ...seqOpts, offsetMs: r * riffPeriodMs }))
  }
  return notes
}

function bellStrikes(fundamentals: number[], spacingMs: number): NoteEvent[] {
  const notes: NoteEvent[] = []
  fundamentals.forEach((fundamental, i) => {
    ;[1, 2.4, 3.8].forEach((mult, j) => {
      notes.push({
        atMs: i * spacingMs,
        freq: fundamental * mult,
        durationMs: 2000 - j * 350,
        gain: 0.22 / (j + 1),
        type: "sine",
      })
    })
  })
  return notes
}

const NOTE = {
  G4: 392.0,
  A4: 440.0,
  C5: 523.25,
  D5: 587.33,
  E5: 659.25,
  G5: 783.99,
  A5: 880.0,
  C6: 1046.5,
  E6: 1318.51,
  G6: 1567.98,
}

// A gentle up-and-down arpeggio, sine, slow enough that the ~11s phrase
// clearly has a beginning, a peak, and a resolution back to the root.
const chimeNotes = sequence([NOTE.C5, NOTE.E5, NOTE.G5, NOTE.C6, NOTE.G5, NOTE.E5, NOTE.C5], {
  spacingMs: 1600,
  durationMs: 1400,
  gain: 0.24,
})

// Four widely-spaced strikes on a descending-then-returning set of
// fundamentals, each with a long overtone decay — reads as a slow,
// deliberate bell rather than a quick repeating ding.
const bellNotes = bellStrikes([NOTE.E5, NOTE.C5, NOTE.G4, NOTE.C5], 3000)

// A bouncy 8-note riff repeated a few times.
const marimbaNotes = repeatedRiff(
  [NOTE.C5, NOTE.E5, NOTE.G5, NOTE.C6, NOTE.G5, NOTE.E5, NOTE.C5, NOTE.G4],
  { spacingMs: 350, durationMs: 320, gain: 0.3, type: "triangle", riffPeriodMs: 3000, repeats: 4 },
)

// A brighter, quicker riff repeated more often to match its faster tempo.
const xylophoneNotes = repeatedRiff([NOTE.G5, NOTE.C6, NOTE.E6, NOTE.G6, NOTE.E6, NOTE.C6], {
  spacingMs: 220,
  durationMs: 200,
  gain: 0.28,
  riffPeriodMs: 1500,
  repeats: 8,
})

function makeSound(id: string, label: string, notes: NoteEvent[]): ChimeSound {
  const previewNotes = notes.slice(0, 2)
  const periodMs = spanMs(notes) + 400
  return {
    id,
    label,
    periodMs,
    play: (ctx, dest) => scheduleNotes(ctx, dest, notes),
    preview: (ctx, dest) => scheduleNotes(ctx, dest, previewNotes),
  }
}

export const SOUNDS: ChimeSound[] = [
  makeSound("chime", "Chime", chimeNotes),
  makeSound("bell", "Bell", bellNotes),
  makeSound("marimba", "Marimba", marimbaNotes),
  makeSound("xylophone", "Xylophone", xylophoneNotes),
]

export function soundById(id: string): ChimeSound {
  return SOUNDS.find((s) => s.id === id) ?? SOUNDS[0]
}
