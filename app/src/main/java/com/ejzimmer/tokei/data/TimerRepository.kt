package com.ejzimmer.tokei.data

import android.content.Context
import com.ejzimmer.tokei.audio.SOUNDS

private const val PREFS_NAME = "tokei_timers"
private const val KEY_TIMERS = "timers_json"

class TimerRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var nextSoundIndex = 0

    /** Loads persisted timers, catching up any that finished while the app
     * was completely killed (no live code was around to notice at the time). */
    fun load(): MutableList<TimerData> {
        val raw = prefs.getString(KEY_TIMERS, null)
        val timers = if (raw == null) {
            mutableListOf(createTimer("Timer 1"))
        } else {
            runCatching { parseTimerList(raw).toMutableList() }
                .getOrElse { mutableListOf(createTimer("Timer 1")) }
        }
        if (timers.isEmpty()) timers.add(createTimer("Timer 1"))

        val now = System.currentTimeMillis()
        for (timer in timers) {
            val endAt = timer.endAtEpochMs
            if (timer.status == TimerStatus.RUNNING && endAt != null && endAt <= now) {
                timer.status = TimerStatus.RINGING
                timer.finishedAtEpochMs = endAt
                timer.endAtEpochMs = null
            }
        }
        return timers
    }

    fun save(timers: List<TimerData>) {
        prefs.edit().putString(KEY_TIMERS, timers.toJsonString()).apply()
    }

    fun createTimer(name: String): TimerData {
        val sound = SOUNDS[nextSoundIndex % SOUNDS.size]
        nextSoundIndex++
        return TimerData(name = name, soundId = sound.id)
    }
}
