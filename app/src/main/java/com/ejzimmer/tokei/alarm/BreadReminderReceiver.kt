package com.ejzimmer.tokei.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus
import com.ejzimmer.tokei.data.alarmSoundId
import com.ejzimmer.tokei.data.isBreadWaitingForNextStep

/** Rings a bread timer again, every minute, until its next step is started
 * or the bread is in the fridge. */
class BreadReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        val repository = TimerRepository(context)
        val timers = repository.loadRaw()
        val timer = timers.find { it.id == timerId } ?: return
        if (!timer.isBreadWaitingForNextStep) return

        BreadReminderScheduler.scheduleNextReminder(context, timerId)
        // Rings even if the timer is already marked ringing: the service may
        // have been killed since, leaving nothing actually making a sound.
        timer.status = TimerStatus.RINGING
        repository.save(timers)
        AlarmService.start(context, timerId, createAlarmText(context, timer), timer.alarmSoundId())
        AlarmEvents.notifyTimersChanged()
    }
}
