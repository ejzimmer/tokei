package com.ejzimmer.tokei.alarm

import android.content.Context
import com.ejzimmer.tokei.R
import com.ejzimmer.tokei.data.BREAD_FRIDGE_MESSAGE
import com.ejzimmer.tokei.data.TimerData
import com.ejzimmer.tokei.data.isBreadWaitingForFridge

/** What a ringing alarm's notification says, and what its one action is called. */
data class AlarmText(val title: String, val body: String, val stopLabel: String)

/** Call it after the timer has been marked finished: a bread timer's
 * notification depends on which step it has just moved past. */
fun createAlarmText(context: Context, timer: TimerData): AlarmText =
    if (timer.isBreadWaitingForFridge) {
        AlarmText(
            title = BREAD_FRIDGE_MESSAGE,
            body = context.getString(R.string.notification_bread_fridge_body),
            stopLabel = context.getString(R.string.notification_bread_fridge_action),
        )
    } else {
        AlarmText(
            title = context.getString(R.string.notification_title_format, timer.name),
            body = context.getString(R.string.notification_body),
            stopLabel = context.getString(R.string.notification_stop_action),
        )
    }
