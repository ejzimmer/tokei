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
            if (timer.status == TimerStatus.RUNNING && endAt != null) {
                AlarmScheduler.schedule(context, timer.id, endAt)
            }
        }
        repository.save(timers)
    }
}
