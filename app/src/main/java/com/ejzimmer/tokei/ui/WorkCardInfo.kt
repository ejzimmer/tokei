package com.ejzimmer.tokei.ui

import com.ejzimmer.tokei.data.WorkSchedule
import com.ejzimmer.tokei.data.WorkState
import com.ejzimmer.tokei.data.workDayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The work timer's extras: which day it's counting for (title line), how
 * much of today's 7.5 hours is left (negative once it's done), and when it
 * was last started or stopped.
 */
data class WorkCardInfo(
    val dayLabel: String,
    val todayRemainingMs: Long = WorkSchedule.CYCLE_MS,
    val lastToggleLabel: String? = null,
    val details: List<String> = emptyList(),
)

fun workCardInfo(state: WorkState, today: LocalDate, nowMs: Long, isRunning: Boolean): WorkCardInfo {
    val headDay = state.headDay ?: WorkSchedule.firstWorkDayOnOrAfter(today)
    return WorkCardInfo(
        dayLabel = workDayName(headDay, today),
        todayRemainingMs = state.todayRemainingMs(today, nowMs),
        lastToggleLabel = state.lastToggledAtEpochMs?.let { at ->
            val verb = if (isRunning) "Resumed" else "Paused"
            "$verb at ${formatToggleTime(at, today)}"
        },
    )
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dayAndTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE h:mm a")

/** Just the time for today; the weekday too once it's from an earlier day. */
private fun formatToggleTime(epochMs: Long, today: LocalDate): String {
    val at = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return at.format(if (at.toLocalDate() == today) timeFormat else dayAndTimeFormat)
}
