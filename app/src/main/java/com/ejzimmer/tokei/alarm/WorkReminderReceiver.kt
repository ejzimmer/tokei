package com.ejzimmer.tokei.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus
import com.ejzimmer.tokei.data.WORK_TIMER_ID
import com.ejzimmer.tokei.data.WorkSchedule
import com.ejzimmer.tokei.data.reconciled
import java.time.LocalDate

/** Fires at 8:30 on work days and at 18:30 daily -- see WorkReminderScheduler. */
class WorkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val repository = TimerRepository(context)
        val timer = repository.loadRaw().find { it.id == WORK_TIMER_ID }
        val isRunning = timer?.status == TimerStatus.RUNNING
        val today = LocalDate.now()
        val state = repository.loadWorkState().reconciled(today)

        when (action) {
            WorkReminderScheduler.ACTION_MORNING ->
                // Started and already stopped again still counts as started.
                if (WorkSchedule.isWorkDay(today) && !isRunning && !state.startedOn(today)) {
                    WorkNotifier.notifyMorningReminder(context, state)
                }
            // Only ever speaks up when the timer is genuinely still counting,
            // so a day off stays quiet without needing a day-of-week check.
            WorkReminderScheduler.ACTION_EVENING ->
                if (isRunning) {
                    val remainingMs = (timer?.endAtEpochMs ?: 0L) - System.currentTimeMillis()
                    WorkNotifier.notifyEveningReminder(context, state, remainingMs.coerceAtLeast(0L))
                }
        }

        WorkReminderScheduler.scheduleNext(context, action)
    }
}
