package com.ejzimmer.tokei.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.isBreadWaitingForFridge
import com.ejzimmer.tokei.data.putBreadInFridge

/** Handles the notification's one action: silences that timer's alarm and
 * persists it back to idle, then tells any live app UI directly. On a bread
 * timer waiting for the fridge, the action is "it's in the fridge", which
 * also ends the bake. */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        clearRingingTimer(context, timerId)
        AlarmService.stop(context, timerId)
        putBreadInFridgeIfWaiting(context, timerId)
    }

    private fun putBreadInFridgeIfWaiting(context: Context, timerId: String) {
        val repository = TimerRepository(context)
        val timers = repository.loadRaw()
        val timer = timers.find { it.id == timerId } ?: return
        if (!timer.isBreadWaitingForFridge) return
        timer.putBreadInFridge()
        repository.save(timers)
        BreadReminderScheduler.cancel(context, timerId)
        AlarmEvents.notifyTimersChanged()
    }
}
