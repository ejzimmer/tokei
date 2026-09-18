package com.ejzimmer.tokei.ui

import com.ejzimmer.tokei.data.WorkSchedule
import com.ejzimmer.tokei.data.WorkState
import com.ejzimmer.tokei.data.workDayName
import java.time.LocalDate

/** The work timer's title line: which day it's counting for. */
data class WorkCardInfo(val dayLabel: String, val details: List<String> = emptyList())

fun workCardInfo(state: WorkState, today: LocalDate): WorkCardInfo {
    val headDay = state.headDay ?: WorkSchedule.firstWorkDayOnOrAfter(today)
    return WorkCardInfo(dayLabel = workDayName(headDay, today))
}
