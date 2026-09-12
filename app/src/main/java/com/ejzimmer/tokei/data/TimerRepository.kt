package com.ejzimmer.tokei.data

import android.content.Context
import com.ejzimmer.tokei.audio.SOUNDS

private const val PREFS_NAME = "tokei_timers"
private const val KEY_TIMERS = "timers_json"
private const val KEY_WORK_STATE = "work_state_json"

class TimerRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var nextSoundIndex = 0

    /** Persisted timers exactly as stored, with no "catch up anything that
     * finished while we weren't looking" pass. AlarmReceiver needs this: it
     * IS the thing responsible for performing that exact transition when its
     * alarm fires, and since the alarm fires at-or-after endAt, [load]'s
     * catch-up would otherwise flip the timer to RINGING first and make
     * AlarmReceiver think someone else already handled it. */
    fun loadRaw(): MutableList<TimerData> {
        val raw = prefs.getString(KEY_TIMERS, null)
        val timers = if (raw == null) {
            mutableListOf(createTimer("Timer 1"))
        } else {
            runCatching { parseTimerList(raw).toMutableList() }
                .getOrElse { mutableListOf(createTimer("Timer 1")) }
        }
        if (timers.isEmpty()) timers.add(createTimer("Timer 1"))
        return ensureWorkTimer(timers)
    }

    /** The work timer always exists and always sits at the top of the list. */
    private fun ensureWorkTimer(timers: MutableList<TimerData>): MutableList<TimerData> {
        val index = timers.indexOfFirst { it.id == WORK_TIMER_ID }
        when {
            index == -1 -> timers.add(0, createWorkTimer())
            index != 0 -> timers.add(0, timers.removeAt(index))
        }
        return timers
    }

    private fun createWorkTimer(): TimerData {
        val timer = TimerData(
            id = WORK_TIMER_ID,
            name = WORK_TIMER_NAME,
            soundId = SOUNDS.first().id,
        )
        timer.setRemainingMs(WorkSchedule.CYCLE_MS)
        return timer
    }

    /** Loads persisted timers, catching up any that finished while the app
     * was completely killed (no live code was around to notice at the time). */
    fun load(): MutableList<TimerData> {
        val timers = loadRaw()
        val now = System.currentTimeMillis()
        for (timer in timers) {
            // The work timer deliberately isn't caught up here: it doesn't
            // stop at zero, it rolls into the next day's cycle, which needs
            // the ledger too. AlarmReceiver (or the ViewModel on open) does
            // that properly.
            if (timer.isWorkTimer) continue
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

    fun loadWorkState(): WorkState {
        val raw = prefs.getString(KEY_WORK_STATE, null) ?: return WorkState()
        return WorkState.parse(raw)
    }

    fun saveWorkState(state: WorkState) {
        prefs.edit().putString(KEY_WORK_STATE, state.toJson().toString()).apply()
    }

    fun createTimer(name: String, isPomodoro: Boolean = false): TimerData {
        val sound = SOUNDS[nextSoundIndex % SOUNDS.size]
        nextSoundIndex++
        return if (isPomodoro) {
            // Classic pomodoro defaults: 25 minutes work, 5 minutes rest.
            TimerData(
                name = name,
                soundId = sound.id,
                hours = 0,
                minutes = 25,
                seconds = 0,
                isPomodoro = true,
                restHours = 0,
                restMinutes = 5,
                restSeconds = 0,
            )
        } else {
            TimerData(name = name, soundId = sound.id)
        }
    }
}
