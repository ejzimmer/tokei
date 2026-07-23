package com.ejzimmer.tokei.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus

/** Handles the notification's "Stop" action: silences that timer's alarm
 * and persists it back to idle, then tells any live app UI directly. */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return

        val repository = TimerRepository(context)
        val timers = repository.load()
        val timer = timers.find { it.id == timerId }
        if (timer != null && timer.status == TimerStatus.RINGING) {
            timer.status = TimerStatus.IDLE
            timer.finishedAtEpochMs = null
            repository.save(timers)
        }

        AlarmService.stop(context, timerId)
        AlarmEvents.notifyStopped(timerId)
    }
}
