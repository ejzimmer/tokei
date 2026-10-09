package com.ejzimmer.tokei.ui

import com.ejzimmer.tokei.data.TimerData
import com.ejzimmer.tokei.data.TimerStatus
import com.ejzimmer.tokei.data.WorkSchedule
import com.ejzimmer.tokei.data.WorkState
import com.ejzimmer.tokei.data.localDateOf
import com.ejzimmer.tokei.data.workDayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the work card's dial needs beyond the counter itself: which day
 * the outer ring is counting for, how much of today's 7.5 hours has been
 * worked (the inner ring, which laps once it passes 7.5), when the timer was
 * last started or stopped, and the time on the flag.
 */
data class WorkCardInfo(
    val dayLabel: String,
    val workedTodayMs: Long = 0L,
    val isRunning: Boolean = false,
    val lastToggleLabel: String? = null,
    val flagLabel: String = "",
    /** The flag is showing when the ring filled, rather than when it will. */
    val flagIsFilled: Boolean = false,
)

fun workCardInfo(state: WorkState, timer: TimerData, today: LocalDate, nowMs: Long): WorkCardInfo {
    val headDay = state.headDay ?: WorkSchedule.firstWorkDayOnOrAfter(today)
    val isRunning = timer.status == TimerStatus.RUNNING

    // Once today's ring has filled and the timer has moved on to a later
    // day's, a projection for that day means nothing yet -- keep showing when
    // the ring actually filled instead.
    val filledAt = timer.lastFinishedAtEpochMs
        ?.takeIf { localDateOf(it) == today && headDay.isAfter(today) }
    // Otherwise: when the ring fills if there are no more breaks. While
    // stopped, the duration fields hold the remaining time, edits included.
    val fullAt = filledAt ?: if (isRunning) {
        timer.endAtEpochMs ?: nowMs
    } else {
        nowMs + timer.durationMs()
    }

    return WorkCardInfo(
        dayLabel = workDayName(headDay, today),
        workedTodayMs = state.workedTodayMs(today, nowMs),
        isRunning = isRunning,
        lastToggleLabel = state.lastToggledAtEpochMs?.let { formatClock(it, today) },
        flagLabel = formatClock(fullAt, today),
        flagIsFilled = filledAt != null,
    )
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayAndTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm")

/** Just the time for today; the weekday too for any other day. */
private fun formatClock(epochMs: Long, today: LocalDate): String {
    val at = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return at.format(if (at.toLocalDate() == today) timeFormat else dayAndTimeFormat)
}
