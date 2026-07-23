package com.ejzimmer.tokei.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus

/** Fired by AlarmManager at the exact moment a timer is due. Marks the timer
 * ringing in persisted storage (the app may not be running to see this any
 * other way) and starts the foreground service that actually rings. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return

        val repository = TimerRepository(context)
        val timers = repository.load()
        val timer = timers.find { it.id == timerId } ?: return
        // Already paused/reset/deleted since this alarm was scheduled.
        if (timer.status != TimerStatus.RUNNING) return

        val now = System.currentTimeMillis()
        val finishedAt = timer.endAtEpochMs ?: now
        timer.status = TimerStatus.RINGING
        timer.finishedAtEpochMs = finishedAt
        timer.lastFinishedAtEpochMs = finishedAt
        timer.endAtEpochMs = null
        repository.save(timers)

        AlarmService.start(context, timerId, timer.name, timer.soundId)
        AlarmEvents.notifyFinished(timerId, finishedAt)
    }
}
