package com.ejzimmer.tokei.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val SAMPLE_RATE = 44100

enum class Waveform { SINE, TRIANGLE }

data class NoteEvent(
    val atMs: Int,
    val freq: Double,
    val durationMs: Int,
    val gain: Float,
    val waveform: Waveform = Waveform.SINE,
)

data class ChimeSound(
    val id: String,
    val label: String,
    val notes: List<NoteEvent>,
    val previewNotes: List<NoteEvent>,
    // Whether an alarm using this sound repeats it until Stop is pressed.
    // false plays the phrase once and then falls silent on its own -- for
    // situations (e.g. exercising) where fumbling for the phone to silence a
    // looping alarm isn't practical.
    val loop: Boolean = true,
)

private object Note {
    const val G4 = 392.00
    const val C5 = 523.25
    const val E5 = 659.25
    const val G5 = 783.99
    const val C6 = 1046.50
}

private fun sequence(
    freqs: List<Double>,
    spacingMs: Int,
    durationMs: Int,
    gain: Float,
    waveform: Waveform = Waveform.SINE,
    offsetMs: Int = 0,
): List<NoteEvent> = freqs.mapIndexed { i, freq ->
    NoteEvent(offsetMs + i * spacingMs, freq, durationMs, gain, waveform)
}

private fun repeatedRiff(
    freqs: List<Double>,
    spacingMs: Int,
    durationMs: Int,
    gain: Float,
    waveform: Waveform,
    riffPeriodMs: Int,
    repeats: Int,
): List<NoteEvent> = (0 until repeats).flatMap { r ->
    sequence(freqs, spacingMs, durationMs, gain, waveform, offsetMs = r * riffPeriodMs)
}

private fun bellStrikes(
    fundamentals: List<Double>,
    spacingMs: Int,
    ringMs: Int,
): List<NoteEvent> =
    fundamentals.flatMapIndexed { i, fundamental ->
        listOf(1.0, 2.4, 3.8).mapIndexed { j, mult ->
            NoteEvent(
                atMs = i * spacingMs,
                freq = fundamental * mult,
                durationMs = ringMs - j * ringMs / 6,
                gain = 0.5f / (j + 1),
            )
        }
    }

private val marimbaNotes = repeatedRiff(
    listOf(Note.C5, Note.E5, Note.G5, Note.C6, Note.G5, Note.E5, Note.C5, Note.G4),
    spacingMs = 350, durationMs = 320, gain = 0.55f, waveform = Waveform.TRIANGLE,
    riffPeriodMs = 3000, repeats = 4,
)

private val tripleBeepNotes = sequence(
    listOf(Note.C6, Note.C6, Note.C6),
    spacingMs = 450, durationMs = 280, gain = 0.6f,
)

// A quick rising arpeggio ending on a held top note -- a different contour
// and rhythm from the triple beep, so the two are easy to tell apart.
private val risingNotes = sequence(
    listOf(Note.C5, Note.E5, Note.G5),
    spacingMs = 140, durationMs = 220, gain = 0.5f, waveform = Waveform.TRIANGLE,
) + NoteEvent(atMs = 420, freq = Note.C6, durationMs = 700, gain = 0.5f, waveform = Waveform.TRIANGLE)

// Two short bell strikes, high then low, like a doorbell.
private val dingDongNotes = bellStrikes(listOf(Note.E5, Note.C5), spacingMs = 650, ringMs = 1400)

/** Short sounds play once and stop on their own; Marimba is the one looping
 * alarm left, for when it needs to keep going until Stop is pressed. */
val SOUNDS: List<ChimeSound> = listOf(
    ChimeSound("triple_beep", "Triple beep", tripleBeepNotes, previewNotes = tripleBeepNotes, loop = false),
    ChimeSound("rising", "Rising", risingNotes, previewNotes = risingNotes, loop = false),
    ChimeSound("ding_dong", "Ding dong", dingDongNotes, previewNotes = dingDongNotes, loop = false),
    ChimeSound("marimba", "Marimba", marimbaNotes, previewNotes = marimbaNotes.take(8)),
)

/** What new timers get, and what any timer still pointing at a removed sound
 * falls back to. */
val DEFAULT_SOUND: ChimeSound = SOUNDS.first { it.id == "triple_beep" }

fun soundById(id: String): ChimeSound = SOUNDS.find { it.id == id } ?: DEFAULT_SOUND

private fun waveSample(waveform: Waveform, phase: Double): Double = when (waveform) {
    Waveform.SINE -> sin(phase)
    Waveform.TRIANGLE -> {
        // A few odd harmonics rather than a true bandlimited triangle --
        // plenty clean at these frequencies and much simpler to compute.
        val t = ((phase / (2 * PI)) % 1.0 + 1.0) % 1.0
        (8.0 / (PI * PI)) * (
            sin(2 * PI * t) - sin(2 * PI * 3 * t) / 9.0 + sin(2 * PI * 5 * t) / 25.0
        )
    }
}

/** A little trailing silence, so looping the buffer doesn't feel abrupt. */
private const val PHRASE_TAIL_MS = 400

/** How long [notes] take to play through once, tail included -- which is also
 * how long a non-looping alarm makes any noise for. */
fun phraseDurationMs(notes: List<NoteEvent>): Int =
    notes.maxOf { it.atMs + it.durationMs } + PHRASE_TAIL_MS

/**
 * Renders a phrase to 16-bit PCM mono samples at [SAMPLE_RATE], with a little
 * trailing silence so looping the buffer doesn't feel abrupt.
 */
fun renderPhrase(notes: List<NoteEvent>): ShortArray {
    val totalMs = phraseDurationMs(notes)
    val totalSamples = totalMs * SAMPLE_RATE / 1000
    val buffer = FloatArray(totalSamples)

    for (note in notes) {
        val startSample = note.atMs * SAMPLE_RATE / 1000
        val durationSamples = note.durationMs * SAMPLE_RATE / 1000
        val attackSamples = (0.012 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val decaySamples = (durationSamples - attackSamples).coerceAtLeast(1)
        for (i in 0 until durationSamples) {
            val sampleIndex = startSample + i
            if (sampleIndex >= totalSamples) break
            val envelope = if (i < attackSamples) {
                i.toDouble() / attackSamples
            } else {
                val decayT = (i - attackSamples).toDouble() / decaySamples
                exp(decayT * ln(0.0001))
            }
            val t = i.toDouble() / SAMPLE_RATE
            val phase = 2 * PI * note.freq * t
            val sample = waveSample(note.waveform, phase) * envelope * note.gain.toDouble()
            buffer[sampleIndex] = buffer[sampleIndex] + sample.toFloat()
        }
    }

    val shorts = ShortArray(totalSamples)
    for (i in buffer.indices) {
        val clamped = max(-1.0f, min(1.0f, buffer[i]))
        shorts[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
    }
    return shorts
}
