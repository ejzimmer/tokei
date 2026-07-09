export interface ChimeSound {
  id: string
  label: string
  /** How long to wait before repeating this chime while a timer is ringing. */
  periodMs: number
  /** The full 10-15s alarm phrase, used while a timer is ringing. */
  play: (ctx: AudioContext, destination: AudioNode) => void
  /** A single short ding, used for the "preview sound" button. */
  preview: (ctx: AudioContext, destination: AudioNode) => void
}

function tone(
  ctx: AudioContext,
  destination: AudioNode,
  freq: number,
  startTime: number,
  duration: number,
  opts: { type?: OscillatorType; gain?: number; attack?: number } = {},
) {
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
}

// Repeats a short motif at a steady spacing to fill roughly 10-15 seconds
// of alarm before the outer loop (see audio.ts) repeats the whole thing.
function repeatMotif(
  ctx: AudioContext,
  dest: AudioNode,
  motif: (ctx: AudioContext, dest: AudioNode, t: number) => void,
  count: number,
  spacing: number,
) {
  const t0 = ctx.currentTime
  for (let i = 0; i < count; i++) {
    motif(ctx, dest, t0 + i * spacing)
  }
}

function chimeMotif(ctx: AudioContext, dest: AudioNode, t: number) {
  tone(ctx, dest, 1318.51, t, 0.9, { gain: 0.28 }) // E6
  tone(ctx, dest, 1046.5, t + 0.12, 1.1, { gain: 0.28 }) // C6
}

function bellMotif(ctx: AudioContext, dest: AudioNode, t: number) {
  const fundamental = 880
  ;[1, 2.4, 3.8].forEach((mult, i) =>
    tone(ctx, dest, fundamental * mult, t, 1.5 - i * 0.25, {
      gain: 0.22 / (i + 1),
      attack: 0.005,
    }),
  )
}

function marimbaMotif(ctx: AudioContext, dest: AudioNode, t: number) {
  tone(ctx, dest, 523.25, t, 0.35, { type: "triangle", gain: 0.32 })
  tone(ctx, dest, 659.25, t + 0.18, 0.35, { type: "triangle", gain: 0.32 })
}

function xylophoneMotif(ctx: AudioContext, dest: AudioNode, t: number) {
  tone(ctx, dest, 1567.98, t, 0.22, { gain: 0.3 })
  tone(ctx, dest, 2093, t + 0.1, 0.28, { gain: 0.26 })
}

export const SOUNDS: ChimeSound[] = [
  {
    id: "chime",
    label: "Chime",
    periodMs: 7 * 1800,
    play(ctx, dest) {
      repeatMotif(ctx, dest, chimeMotif, 7, 1.8)
    },
    preview(ctx, dest) {
      chimeMotif(ctx, dest, ctx.currentTime)
    },
  },
  {
    id: "bell",
    label: "Bell",
    periodMs: 6 * 2200,
    play(ctx, dest) {
      repeatMotif(ctx, dest, bellMotif, 6, 2.2)
    },
    preview(ctx, dest) {
      bellMotif(ctx, dest, ctx.currentTime)
    },
  },
  {
    id: "marimba",
    label: "Marimba",
    periodMs: 14 * 900,
    play(ctx, dest) {
      repeatMotif(ctx, dest, marimbaMotif, 14, 0.9)
    },
    preview(ctx, dest) {
      marimbaMotif(ctx, dest, ctx.currentTime)
    },
  },
  {
    id: "xylophone",
    label: "Xylophone",
    periodMs: 18 * 700,
    play(ctx, dest) {
      repeatMotif(ctx, dest, xylophoneMotif, 18, 0.7)
    },
    preview(ctx, dest) {
      xylophoneMotif(ctx, dest, ctx.currentTime)
    },
  },
]

export function soundById(id: string): ChimeSound {
  return SOUNDS.find((s) => s.id === id) ?? SOUNDS[0]
}
