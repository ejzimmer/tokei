package com.ejzimmer.tokei.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ejzimmer.tokei.MainActivity
import com.ejzimmer.tokei.R
import com.ejzimmer.tokei.audio.AlarmPlayer
import com.ejzimmer.tokei.audio.soundById

/**
 * Holds one AlarmPlayer + notification per currently-ringing timer, so
 * several can ring (and be stopped individually) at once. A foreground
 * service needs *some* live notification at all times; if the timer whose
 * notification is anchoring that gets stopped first, another ringing timer
 * is promoted to take its place.
 */
class AlarmService : Service() {
    private data class Ringing(val name: String, val player: AlarmPlayer)

    private val ringing = mutableMapOf<String, Ringing>()
    private var vibrator: Vibrator? = null
    private var anchorTimerId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val timerId = intent?.getStringExtra(EXTRA_TIMER_ID)
        if (intent?.action == ACTION_STOP) {
            if (timerId != null) stopOne(timerId)
            return START_NOT_STICKY
        }
        if (timerId == null) return START_NOT_STICKY

        val name = intent.getStringExtra(EXTRA_TIMER_NAME) ?: "Timer"
        val soundId = intent.getStringExtra(EXTRA_SOUND_ID) ?: "chime"
        startOne(timerId, name, soundId)
        return START_STICKY
    }

    override fun onDestroy() {
        ringing.values.forEach { it.player.stop() }
        ringing.clear()
        vibrator?.cancel()
        super.onDestroy()
    }

    private fun startOne(timerId: String, name: String, soundId: String) {
        ensureChannel()
        val player = AlarmPlayer().also { it.playLooping(soundById(soundId).notes) }
        ringing[timerId] = Ringing(name, player)
        restartVibration()

        val notification = buildNotification(timerId, name)
        val currentAnchor = anchorTimerId
        if (currentAnchor == null) {
            anchorTimerId = timerId
            startForeground(timerId.hashCode(), notification)
        } else {
            NotificationManagerCompat.from(this).notify(timerId.hashCode(), notification)
        }
    }

    private fun stopOne(timerId: String) {
        ringing.remove(timerId)?.player?.stop()
        NotificationManagerCompat.from(this).cancel(timerId.hashCode())

        when {
            ringing.isEmpty() -> {
                vibrator?.cancel()
                vibrator = null
                anchorTimerId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            anchorTimerId == timerId -> {
                val (nextId, next) = ringing.entries.first()
                anchorTimerId = nextId
                startForeground(nextId.hashCode(), buildNotification(nextId, next.name))
            }
        }
    }

    private fun restartVibration() {
        val v = vibrator ?: newVibrator().also { vibrator = it }
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        v.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun newVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

    private fun ensureChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_description)
            // Vibration is driven manually (see restartVibration) so it can
            // repeat for as long as the alarm rings.
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(timerId: String, name: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, StopAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            timerId.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title_format, name))
            .setContentText(getString(R.string.notification_body))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, getString(R.string.notification_stop_action), stopPendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tokei_alarms"
        private const val ACTION_STOP = "com.ejzimmer.tokei.action.STOP"

        fun start(context: Context, timerId: String, timerName: String, soundId: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_TIMER_ID, timerId)
                putExtra(EXTRA_TIMER_NAME, timerName)
                putExtra(EXTRA_SOUND_ID, soundId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context, timerId: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_TIMER_ID, timerId)
            }
            context.startService(intent)
        }
    }
}
