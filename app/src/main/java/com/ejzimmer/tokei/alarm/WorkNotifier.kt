package com.ejzimmer.tokei.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ejzimmer.tokei.MainActivity
import com.ejzimmer.tokei.R
import com.ejzimmer.tokei.data.WorkState
import com.ejzimmer.tokei.data.formatWorkDuration
import com.ejzimmer.tokei.data.workDayLabel
import java.time.LocalDate

/**
 * The work timer's three notifications. These are plain system notifications
 * rather than AlarmService's looping ring: the work timer never stops to be
 * silenced -- it rolls straight into the next cycle -- so there'd be nothing
 * for a Stop button to do.
 */
object WorkNotifier {
    private const val CHANNEL_CYCLE = "tokei_work_cycle"
    private const val CHANNEL_REMINDER = "tokei_work_reminder"

    // Fixed ids: each of these should replace its own previous copy rather
    // than stack up.
    private const val ID_CYCLE = 0x776f726b // "work"
    private const val ID_MORNING = ID_CYCLE + 1
    private const val ID_EVENING = ID_CYCLE + 2

    fun notifyCycleComplete(context: Context, state: WorkState, cyclesCompleted: Int) {
        val today = LocalDate.now()
        val nextDay = state.headDay
        val body = when {
            nextDay == null -> context.getString(R.string.work_cycle_body_plain)
            else -> context.getString(
                R.string.work_cycle_body_format,
                workDayLabel(nextDay, today),
                formatWorkDuration(state.totalOwedMs()),
            )
        }
        val title = if (cyclesCompleted > 1) {
            context.getString(R.string.work_cycle_title_multiple, cyclesCompleted)
        } else {
            context.getString(R.string.work_cycle_title)
        }
        notify(context, CHANNEL_CYCLE, ID_CYCLE, title, body)
    }

    fun notifyMorningReminder(context: Context, state: WorkState) {
        val today = LocalDate.now()
        val owed = state.totalOwedMs()
        val body = if (state.cycles.isEmpty() || owed <= 0L) {
            context.getString(R.string.work_morning_body_plain)
        } else {
            context.getString(
                R.string.work_morning_body_format,
                formatWorkDuration(owed),
                workDayLabel(state.headDay ?: today, today),
            )
        }
        notify(context, CHANNEL_REMINDER, ID_MORNING, context.getString(R.string.work_morning_title), body)
    }

    /** [remainingMs] is the live countdown, not the ledger's stored value --
     * the timer is by definition still running when this fires. */
    fun notifyEveningReminder(context: Context, state: WorkState, remainingMs: Long) {
        val today = LocalDate.now()
        val day = state.headDay
        val body = if (day == null) {
            context.getString(R.string.work_evening_body_plain)
        } else {
            context.getString(
                R.string.work_evening_body_format,
                formatWorkDuration(remainingMs),
                workDayLabel(day, today),
            )
        }
        notify(context, CHANNEL_REMINDER, ID_EVENING, context.getString(R.string.work_evening_title), body)
    }

    private fun notify(context: Context, channelId: String, id: Int, title: String, body: String) {
        ensureChannels(context)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // A plain notification, not the alarm ringtone -- hitting 7.5 hours
        // shouldn't sound like an alarm going off, just let you know.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CYCLE,
                context.getString(R.string.notification_channel_name_work_cycle),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_description_work_cycle)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.notification_channel_name_work_reminder),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_description_work_reminder)
            },
        )
    }
}
