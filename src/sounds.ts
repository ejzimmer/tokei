export interface ChimeSound {
  id: string
  label: string
  /** How long to wait before repeating this chime while a timer is ringing. */
  periodMs: number
  play: (ctx: AudioContext, destination: AudioNode) => void
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

export const SOUNDS: ChimeSound[] = [
  {
    id: "chime",
    label: "Chime",
    periodMs: 1800,
    play(ctx, dest) {
      const t = ctx.currentTime
      tone(ctx, dest, 1318.51, t, 0.9, { gain: 0.28 }) // E6
      tone(ctx, dest, 1046.5, t + 0.12, 1.1, { gain: 0.28 }) // C6
    },
  },
  {
    id: "bell",
    label: "Bell",
    periodMs: 2200,
    play(ctx, dest) {
      const t = ctx.currentTime
      const fundamental = 880
      ;[1, 2.4, 3.8].forEach((mult, i) =>
        tone(ctx, dest, fundamental * mult, t, 1.5 - i * 0.25, {
          gain: 0.22 / (i + 1),
          attack: 0.005,
        }),
      )
    },
  },
  {
    id: "marimba",
    label: "Marimba",
    periodMs: 900,
    play(ctx, dest) {
      const t = ctx.currentTime
      tone(ctx, dest, 523.25, t, 0.35, { type: "triangle", gain: 0.32 })
      tone(ctx, dest, 659.25, t + 0.18, 0.35, { type: "triangle", gain: 0.32 })
    },
  },
  {
    id: "xylophone",
    label: "Xylophone",
    periodMs: 700,
    play(ctx, dest) {
      const t = ctx.currentTime
      tone(ctx, dest, 1567.98, t, 0.22, { gain: 0.3 })
      tone(ctx, dest, 2093, t + 0.1, 0.28, { gain: 0.26 })
    },
  },
]

export function soundById(id: string): ChimeSound {
  return SOUNDS.find((s) => s.id === id) ?? SOUNDS[0]
}
