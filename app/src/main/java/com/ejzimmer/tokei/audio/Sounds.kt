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
)

private object Note {
    const val G4 = 392.00
    const val C5 = 523.25
    const val D5 = 587.33
    const val E5 = 659.25
    const val G5 = 783.99
    const val C6 = 1046.50
    const val E6 = 1318.51
    const val G6 = 1567.98
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

private fun bellStrikes(fundamentals: List<Double>, spacingMs: Int): List<NoteEvent> =
    fundamentals.flatMapIndexed { i, fundamental ->
        listOf(1.0, 2.4, 3.8).mapIndexed { j, mult ->
            NoteEvent(
                atMs = i * spacingMs,
                freq = fundamental * mult,
                durationMs = 2000 - j * 350,
                gain = 0.5f / (j + 1),
            )
        }
    }

private val chimeNotes = sequence(
    listOf(Note.C5, Note.E5, Note.G5, Note.C6, Note.G5, Note.E5, Note.C5),
    spacingMs = 1600, durationMs = 1400, gain = 0.5f,
)

private val bellNotes = bellStrikes(listOf(Note.E5, Note.C5, Note.G4, Note.C5), spacingMs = 3000)

private val marimbaNotes = repeatedRiff(
    listOf(Note.C5, Note.E5, Note.G5, Note.C6, Note.G5, Note.E5, Note.C5, Note.G4),
    spacingMs = 350, durationMs = 320, gain = 0.55f, waveform = Waveform.TRIANGLE,
    riffPeriodMs = 3000, repeats = 4,
)

private val xylophoneNotes = repeatedRiff(
    listOf(Note.G5, Note.C6, Note.E6, Note.G6, Note.E6, Note.C6),
    spacingMs = 220, durationMs = 200, gain = 0.5f, waveform = Waveform.SINE,
    riffPeriodMs = 1500, repeats = 8,
)

val SOUNDS: List<ChimeSound> = listOf(
    ChimeSound("chime", "Chime", chimeNotes, previewNotes = chimeNotes.take(2)),
    ChimeSound("bell", "Bell", bellNotes, previewNotes = bellNotes.take(3)),
    ChimeSound("marimba", "Marimba", marimbaNotes, previewNotes = marimbaNotes.take(8)),
    ChimeSound("xylophone", "Xylophone", xylophoneNotes, previewNotes = xylophoneNotes.take(6)),
)

fun soundById(id: String): ChimeSound = SOUNDS.find { it.id == id } ?: SOUNDS[0]

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

/**
 * Renders a phrase to 16-bit PCM mono samples at [SAMPLE_RATE], with a little
 * trailing silence so looping the buffer doesn't feel abrupt.
 */
fun renderPhrase(notes: List<NoteEvent>): ShortArray {
    val spanMs = notes.maxOf { it.atMs + it.durationMs }
    val totalMs = spanMs + 400
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
