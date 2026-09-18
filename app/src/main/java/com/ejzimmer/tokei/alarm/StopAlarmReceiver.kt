package com.ejzimmer.tokei.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the notification's "Stop" action: silences that timer's alarm
 * and persists it back to idle, then tells any live app UI directly. */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        clearRingingTimer(context, timerId)
        AlarmService.stop(context, timerId)
    }
}
