package com.ejzimmer.tokei.ui

import com.ejzimmer.tokei.data.WorkSchedule
import com.ejzimmer.tokei.data.WorkState
import com.ejzimmer.tokei.data.workDayLabel
import java.time.LocalDate

/** The work timer's headline: which day it's counting for. */
data class WorkCardInfo(val headline: String, val details: List<String> = emptyList())

fun workCardInfo(state: WorkState, today: LocalDate): WorkCardInfo {
    val headDay = state.headDay ?: WorkSchedule.firstWorkDayOnOrAfter(today)
    return WorkCardInfo(headline = "Counting down for " + workDayLabel(headDay, today))
}
