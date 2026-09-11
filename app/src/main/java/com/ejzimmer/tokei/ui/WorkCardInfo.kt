package com.ejzimmer.tokei.ui

import com.ejzimmer.tokei.data.WorkSchedule
import com.ejzimmer.tokei.data.WorkState
import com.ejzimmer.tokei.data.formatWorkDuration
import com.ejzimmer.tokei.data.workDayLabel
import java.time.LocalDate

/** The work timer's running commentary: which day it's counting for, and
 * whether that leaves you ahead, behind, or square. */
data class WorkCardInfo(val headline: String, val details: List<String>)

/** [remainingMs] is the cycle's live countdown, which while running is ahead
 * of the value the ledger last persisted. */
fun workCardInfo(state: WorkState, today: LocalDate, remainingMs: Long): WorkCardInfo {
    val headDay = state.headDay
        ?: return WorkCardInfo(
            headline = "Starts counting toward " +
                workDayLabel(WorkSchedule.firstWorkDayOnOrAfter(today), today),
            details = emptyList(),
        )

    val label = workDayLabel(headDay, today)
    val queued = state.cycles.size - 1
    val details = buildList {
        when {
            headDay.isBefore(today) ->
                add("Catching up — $label's hours weren't finished.")
            headDay.isAfter(today) ->
                add("Working ahead — $label's hours have already started.")
        }
        if (queued > 0) {
            val days = if (queued == 1) "day" else "days"
            add("$queued more full $days queued after this one.")
        }
        if (queued > 0 || headDay.isBefore(today)) {
            val total = remainingMs + queued * WorkSchedule.CYCLE_MS
            add("Still owed in total: ${formatWorkDuration(total)}")
        }
    }

    return WorkCardInfo(headline = "Counting down $label's hours", details = details)
}
