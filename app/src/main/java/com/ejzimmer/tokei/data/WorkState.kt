package com.ejzimmer.tokei.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

const val WORK_TIMER_ID = "tokei-work-timer"
const val WORK_TIMER_NAME = "Work"

/**
 * One day's 7.5-hour obligation, queued up waiting to be worked off.
 *
 * [started] records whether the timer was actually started *on this cycle's
 * own day*, which is what separates a real debt from a provisional one. A
 * cycle created by working ahead (you blew past 7.5 hours on Tuesday, so the
 * overflow got labelled Wednesday) starts out unstarted: if Wednesday then
 * passes without you working at all, it was a day off, and the cycle gets
 * relabelled forward rather than counting as a day you owe.
 */
data class WorkCycle(val dayEpoch: Long, val started: Boolean) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("dayEpoch", dayEpoch)
        put("started", started)
    }

    companion object {
        fun fromJson(json: JSONObject) = WorkCycle(
            dayEpoch = json.getLong("dayEpoch"),
            started = json.optBoolean("started", true),
        )
    }
}

/**
 * The work timer's ledger. [cycles] is the queue of days still owing hours,
 * oldest first; the head is the one the timer is currently counting down.
 * [headRemainingMs] is how much of that head cycle is left -- it's the single
 * source of truth for the countdown, mirrored into the timer's h/m/s fields
 * so the existing digit editor can adjust it.
 *
 * Every cycle other than the head is a full [WorkSchedule.CYCLE_MS], which is
 * what makes "adjusting the time changes this cycle only, later cycles revert
 * to 7.5 hours" fall out for free.
 */
data class WorkState(
    val cycles: List<WorkCycle> = emptyList(),
    val headRemainingMs: Long = WorkSchedule.CYCLE_MS,
    val lastCycleDayEpoch: Long? = null,
    val lastStartedDayEpoch: Long? = null,
) {
    val headDay: LocalDate?
        get() = cycles.firstOrNull()?.let { LocalDate.ofEpochDay(it.dayEpoch) }

    /** Everything still owed across every queued day, not just this cycle. */
    fun totalOwedMs(): Long =
        if (cycles.isEmpty()) 0L
        else headRemainingMs + (cycles.size - 1) * WorkSchedule.CYCLE_MS

    fun startedOn(day: LocalDate): Boolean = lastStartedDayEpoch == day.toEpochDay()

    fun toJson(): JSONObject = JSONObject().apply {
        put("cycles", JSONArray().also { array -> cycles.forEach { array.put(it.toJson()) } })
        put("headRemainingMs", headRemainingMs)
        put("lastCycleDayEpoch", lastCycleDayEpoch ?: JSONObject.NULL)
        put("lastStartedDayEpoch", lastStartedDayEpoch ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): WorkState {
            val array = json.optJSONArray("cycles") ?: JSONArray()
            return WorkState(
                cycles = (0 until array.length()).map { WorkCycle.fromJson(array.getJSONObject(it)) },
                headRemainingMs = json.optLong("headRemainingMs", WorkSchedule.CYCLE_MS),
                lastCycleDayEpoch = if (json.isNull("lastCycleDayEpoch")) null else json.optLong("lastCycleDayEpoch"),
                lastStartedDayEpoch = if (json.isNull("lastStartedDayEpoch")) null else json.optLong("lastStartedDayEpoch"),
            )
        }

        fun parse(raw: String): WorkState =
            runCatching { fromJson(JSONObject(raw)) }.getOrDefault(WorkState())
    }
}

/**
 * Rolls a provisional cycle forward past days that turned out to be days off.
 *
 * Only ever applies to a lone unstarted cycle, which is the one shape a
 * "worked ahead" cycle can have: an unstarted cycle is always for a day later
 * than the most recent one, so nothing can be queued behind it, and nothing
 * new gets claimed while it's outstanding either (claiming needs a day later
 * than [WorkState.lastCycleDayEpoch]). So when its day passes without the
 * timer being started, the hours already banked against it aren't lost --
 * they just move to the next day that is actually worked, which is exactly
 * "roll over all the totals".
 */
fun WorkState.reconciled(today: LocalDate): WorkState {
    val head = cycles.singleOrNull() ?: return this
    if (head.started || head.dayEpoch >= today.toEpochDay()) return this

    val movedTo = WorkSchedule.firstWorkDayOnOrAfter(today).toEpochDay()
    return copy(
        cycles = listOf(WorkCycle(movedTo, started = false)),
        lastCycleDayEpoch = movedTo,
    )
}

/**
 * Brings the ledger up to date for a start happening on [today]: rolls past
 * any days off, marks today as genuinely worked, and claims today's own 7.5
 * hours if it's a work day we haven't claimed yet.
 *
 * Claiming only ever happens here, on a real start -- that's what makes a day
 * you never touch the timer on cost nothing.
 */
fun WorkState.claimedForStart(today: LocalDate): WorkState {
    val todayEpoch = today.toEpochDay()
    var state = reconciled(today)

    // Working on a cycle's own day is what confirms that day as a work day.
    state = state.copy(
        cycles = state.cycles.map { if (it.dayEpoch == todayEpoch) it.copy(started = true) else it },
    )

    val alreadyClaimed = state.lastCycleDayEpoch?.let { todayEpoch <= it } ?: false
    if (WorkSchedule.isWorkDay(today) && !alreadyClaimed) {
        state = state.copy(
            cycles = state.cycles + WorkCycle(todayEpoch, started = true),
            lastCycleDayEpoch = todayEpoch,
        )
    }

    // Nothing owing but the timer is being started anyway -- either it's a
    // non-work day, or today's hours are already done and we're getting ahead.
    if (state.cycles.isEmpty()) {
        state = state.withFreshCycle(today, resetRemaining = false)
    }

    return state.copy(lastStartedDayEpoch = todayEpoch)
}

/**
 * The head cycle just hit zero. Drops it and moves to the next day owing
 * hours, resetting the countdown to a full 7.5 hours -- an adjustment only
 * ever applies to the cycle it was made in.
 */
fun WorkState.cycleCompleted(today: LocalDate): WorkState {
    val remaining = copy(cycles = cycles.drop(1), headRemainingMs = WorkSchedule.CYCLE_MS)
    return if (remaining.cycles.isEmpty()) {
        remaining.withFreshCycle(today, resetRemaining = true)
    } else {
        remaining
    }
}

/** Queues the next work day after everything claimed so far, never earlier than [today]. */
private fun WorkState.withFreshCycle(today: LocalDate, resetRemaining: Boolean): WorkState {
    val todayEpoch = today.toEpochDay()
    val earliest = maxOf((lastCycleDayEpoch ?: (todayEpoch - 1)) + 1, todayEpoch)
    val day = WorkSchedule.firstWorkDayOnOrAfter(LocalDate.ofEpochDay(earliest)).toEpochDay()
    return copy(
        cycles = listOf(WorkCycle(day, started = day == todayEpoch)),
        headRemainingMs = if (resetRemaining) WorkSchedule.CYCLE_MS else headRemainingMs,
        lastCycleDayEpoch = day,
    )
}
