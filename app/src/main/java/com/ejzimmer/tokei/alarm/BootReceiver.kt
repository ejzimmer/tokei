package com.ejzimmer.tokei.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus

/** AlarmManager alarms don't survive a reboot, so anything still running
 * needs to be rescheduled once the device is back up. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val repository = TimerRepository(context)
        val timers = repository.load()
        for (timer in timers) {
            val endAt = timer.endAtEpochMs
            val stopwatchStartAt = timer.stopwatchStartAtEpochMs
            if (timer.status == TimerStatus.RUNNING && endAt != null) {
                AlarmScheduler.schedule(context, timer.id, endAt)
                CountdownNotifier.show(context, timer.id, timer.name, endAt)
            } else if (timer.status == TimerStatus.RUNNING && timer.isStopwatch && stopwatchStartAt != null) {
                // Nothing to reschedule -- a stopwatch has no alarm -- but its
                // ongoing notification was cleared along with everything else.
                CountdownNotifier.showStopwatch(context, timer.id, timer.name, stopwatchStartAt)
            }
        }
        repository.save(timers)

        // The work timer's daily nudges are ordinary alarms too, so they
        // don't survive a reboot either.
        WorkReminderScheduler.scheduleAll(context)
    }
}
