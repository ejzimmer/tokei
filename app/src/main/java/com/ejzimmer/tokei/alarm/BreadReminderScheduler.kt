package com.ejzimmer.tokei.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ejzimmer.tokei.data.BREAD_REMINDER_INTERVAL_MS

/** The alarm that rings a bread timer again when its next step hasn't been
 * started. One per timer, separate from the timer's own countdown alarm. */
object BreadReminderScheduler {
    // XORed against the timerId hash so this never replaces the countdown
    // alarm AlarmScheduler keeps under the plain hash.
    private const val REQUEST_SALT = 0x62726561 // "brea"

    fun scheduleNextReminder(context: Context, timerId: String, fromEpochMs: Long = System.currentTimeMillis()) {
        AlarmScheduler.setAlarm(context, fromEpochMs + BREAD_REMINDER_INTERVAL_MS, pendingIntent(context, timerId))
    }

    fun cancel(context: Context, timerId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, timerId))
    }

    private fun pendingIntent(context: Context, timerId: String): PendingIntent {
        val intent = Intent(context, BreadReminderReceiver::class.java).apply {
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            context,
            timerId.hashCode() xor REQUEST_SALT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
