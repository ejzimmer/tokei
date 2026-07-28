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

/**
 * One ongoing, silent notification per running timer showing time
 * remaining, so it can be checked without opening the app. Built on the
 * notification's own countdown chronometer rather than a repeating
 * service: the system ticks it every second on its own, and it keeps
 * working even after the app process is killed.
 */
object CountdownNotifier {
    private const val CHANNEL_ID = "tokei_countdown"

    // XORed against the timerId hash so this never collides with
    // AlarmService's ringing-notification id for the same timer.
    private const val ID_SALT = 0x636f756e // "coun"

    fun show(context: Context, timerId: String, name: String, endAtEpochMs: Long) {
        ensureChannel(context)

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(context.getString(R.string.notification_countdown_body))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAtEpochMs)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(timerId), notification)
    }

    fun cancel(context: Context, timerId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(timerId))
    }

    private fun notificationId(timerId: String) = timerId.hashCode() xor ID_SALT

    private fun ensureChannel(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name_countdown),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description_countdown)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
