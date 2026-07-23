package com.ejzimmer.tokei.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Plays a rendered PCM phrase on the alarm stream. Looping is done natively
 * by AudioTrack (via setLoopPoints), not by re-triggering playback ourselves
 * -- so there's nothing to "catch up" or accidentally leave running past a
 * Stop press the way the original web version's setInterval-based repeat did.
 */
class AlarmPlayer {
    private var track: AudioTrack? = null

    fun playLooping(notes: List<NoteEvent>) {
        play(notes, looping = true)
    }

    fun playOnce(notes: List<NoteEvent>) {
        play(notes, looping = false)
    }

    /** Stops and releases playback immediately. Safe to call repeatedly. */
    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    private fun play(notes: List<NoteEvent>, looping: Boolean) {
        stop()
        val samples = renderPhrase(notes)
        val audioTrack = buildTrack(samples, looping)
        track = audioTrack
        audioTrack.play()
    }

    private fun buildTrack(samples: ShortArray, looping: Boolean): AudioTrack {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val bufferSizeBytes = samples.size * 2
        val audioTrack = AudioTrack(
            attributes,
            format,
            bufferSizeBytes,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        audioTrack.write(samples, 0, samples.size)
        if (looping) {
            audioTrack.setLoopPoints(0, samples.size, -1)
        }
        return audioTrack
    }
}
