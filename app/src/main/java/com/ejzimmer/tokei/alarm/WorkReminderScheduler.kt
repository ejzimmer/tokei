package com.ejzimmer.tokei.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.WorkSchedule
import java.time.LocalDateTime

/**
 * The work timer's two daily nudges. Each firing reschedules the next one
 * rather than using a repeating alarm, so the morning check can skip
 * non-work days (and so both survive the clock changing under us).
 */
object WorkReminderScheduler {
    const val ACTION_MORNING = "com.ejzimmer.tokei.action.WORK_MORNING_CHECK"
    const val ACTION_EVENING = "com.ejzimmer.tokei.action.WORK_EVENING_CHECK"

    private const val REQUEST_MORNING = 0x776d726e // "wmrn"
    private const val REQUEST_EVENING = 0x77657665 // "weve"

    fun scheduleAll(context: Context) {
        scheduleNext(context, ACTION_MORNING)
        scheduleNext(context, ACTION_EVENING)
    }

    fun scheduleNext(context: Context, action: String) {
        val triggerAt = nextTriggerAt(action, LocalDateTime.now()) ?: return
        AlarmScheduler.setAlarm(context, triggerAt, pendingIntent(context, action))
    }

    private fun nextTriggerAt(action: String, from: LocalDateTime): Long? = when (action) {
        ACTION_MORNING -> WorkSchedule.nextMorningReminderAfter(from)
        ACTION_EVENING -> WorkSchedule.nextEveningReminderAfter(from)
        else -> null
    }

    private fun pendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, WorkReminderReceiver::class.java).setAction(action)
        val requestCode = if (action == ACTION_MORNING) REQUEST_MORNING else REQUEST_EVENING
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
