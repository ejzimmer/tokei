package com.ejzimmer.tokei.alarm

import android.content.Context
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus

/**
 * Persists a ringing timer back to idle and tells any live UI, for the two
 * callers that end an alarm without the card being involved: the
 * notification's Stop action, and a non-looping sound reaching the end of its
 * phrase. Shared rather than written twice -- the last thing to own this
 * transition privately was StopAlarmReceiver, which quietly missed the
 * pomodoro phase handover the card was doing.
 *
 * A pomodoro's handover already happened when it rang, so there's none to do
 * here; the guard is what keeps a late call (the alarm stopped some other way
 * first) from resetting a timer that has since been started again.
 */
fun clearRingingTimer(context: Context, timerId: String) {
    val repository = TimerRepository(context)
    // loadRaw(), not load(): silencing one alarm has no business running the
    // catch-up pass over the others. It would mark a timer that is merely due
    // as ringing and save that here, and AlarmReceiver skips a timer it finds
    // already ringing -- so that timer's alarm would never sound at all.
    val timers = repository.loadRaw()
    val timer = timers.find { it.id == timerId }
    if (timer != null && timer.status == TimerStatus.RINGING) {
        timer.status = TimerStatus.IDLE
        timer.finishedAtEpochMs = null
        repository.save(timers)
    }
    AlarmEvents.notifyStopped(timerId)
}
