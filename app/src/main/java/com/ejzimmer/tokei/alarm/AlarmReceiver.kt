package com.ejzimmer.tokei.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.TimerData
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus
import com.ejzimmer.tokei.data.cycleCompleted
import com.ejzimmer.tokei.data.isWorkTimer
import com.ejzimmer.tokei.data.localDateOf
import com.ejzimmer.tokei.data.markFinished
import com.ejzimmer.tokei.data.setRemainingMs

/** Fired by AlarmManager at the exact moment a timer is due. Marks the timer
 * ringing in persisted storage (the app may not be running to see this any
 * other way) and starts the foreground service that actually rings. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return

        val repository = TimerRepository(context)
        // loadRaw(), not load(): load()'s own catch-up pass would see this
        // same timer (endAt <= now, since the alarm is firing right now) and
        // flip it to RINGING before we get a look, making the check below
        // wrongly think someone already handled it.
        val timers = repository.loadRaw()
        val timer = timers.find { it.id == timerId } ?: return
        // Already paused/reset/deleted since this alarm was scheduled.
        if (timer.status != TimerStatus.RUNNING) return

        if (timer.isWorkTimer) {
            rollWorkCycle(context, repository, timers, timer)
            return
        }

        val now = System.currentTimeMillis()
        val finishedAt = timer.endAtEpochMs ?: now
        timer.markFinished(finishedAt)
        repository.save(timers)

        CountdownNotifier.cancel(context, timerId)
        AlarmService.start(context, timerId, timer.name, timer.soundId)
        AlarmEvents.notifyFinished(timerId, finishedAt)
    }

    /**
     * The work timer doesn't ring and stop -- it books the finished 7.5 hours
     * against whichever day it was counting for, then immediately starts the
     * next day's cycle and keeps running.
     *
     * The loop exists for the case where the device was asleep (or off) long
     * enough that several boundaries went by: each completed cycle is booked
     * against the day it actually fell on, and the new end time is derived
     * from the old one rather than from now, so no time is lost to the delay.
     */
    private fun rollWorkCycle(
        context: Context,
        repository: TimerRepository,
        timers: List<TimerData>,
        timer: TimerData,
    ) {
        val now = System.currentTimeMillis()
        var endAt = timer.endAtEpochMs ?: now
        var state = repository.loadWorkState()
        var finishedAt = endAt
        var completed = 0

        do {
            finishedAt = endAt
            state = state.cycleCompleted(localDateOf(endAt))
            endAt += state.headRemainingMs
            completed++
        } while (endAt <= now && completed < MAX_CATCH_UP_CYCLES)

        timer.endAtEpochMs = endAt
        timer.lastFinishedAtEpochMs = finishedAt
        timer.setRemainingMs(state.headRemainingMs)
        repository.save(timers)
        repository.saveWorkState(state)

        AlarmScheduler.schedule(context, timer.id, endAt)
        CountdownNotifier.show(context, timer.id, timer.name, endAt)
        WorkNotifier.notifyCycleComplete(context, state, completed)
        AlarmEvents.notifyWorkChanged()
    }

    private companion object {
        /** A week of back-to-back cycles is already absurd; past that, stop
         * rather than spin. */
        const val MAX_CATCH_UP_CYCLES = 32
    }
}
