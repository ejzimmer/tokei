package com.ejzimmer.tokei.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

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
    val todayEpoch: Long? = null,
    val todayBankedMs: Long = 0L,
    val runStartedAtEpochMs: Long? = null,
    val lastToggledAtEpochMs: Long? = null,
) {
    val headDay: LocalDate?
        get() = cycles.firstOrNull()?.let { LocalDate.ofEpochDay(it.dayEpoch) }

    /** Everything still owed across every queued day, not just this cycle. */
    fun totalOwedMs(): Long =
        if (cycles.isEmpty()) 0L
        else headRemainingMs + (cycles.size - 1) * WorkSchedule.CYCLE_MS

    /**
     * Time worked on [today] so far: everything banked from earlier runs and
     * edits today, plus whatever part of the current run falls after
     * midnight. Measured by the clock rather than by the cycle countdown, so
     * a cycle rolling over mid-run doesn't disturb it.
     */
    fun workedTodayMs(today: LocalDate, nowMs: Long): Long {
        val banked = if (todayEpoch == today.toEpochDay()) todayBankedMs else 0L
        val running = runStartedAtEpochMs?.let { started ->
            (nowMs - maxOf(started, startOfDayMs(today))).coerceAtLeast(0L)
        } ?: 0L
        // Never below zero: adding back more than was worked today (say,
        // resetting a cycle left over from yesterday) can't make today longer
        // than 7.5 hours.
        return (banked + running).coerceAtLeast(0L)
    }

    /** The today counter: a 7.5-hour day counting down, negative once it's done. */
    fun todayRemainingMs(today: LocalDate, nowMs: Long): Long =
        WorkSchedule.CYCLE_MS - workedTodayMs(today, nowMs)

    fun startedOn(day: LocalDate): Boolean = lastStartedDayEpoch == day.toEpochDay()

    fun toJson(): JSONObject = JSONObject().apply {
        put("cycles", JSONArray().also { array -> cycles.forEach { array.put(it.toJson()) } })
        put("headRemainingMs", headRemainingMs)
        put("lastCycleDayEpoch", lastCycleDayEpoch ?: JSONObject.NULL)
        put("lastStartedDayEpoch", lastStartedDayEpoch ?: JSONObject.NULL)
        put("todayEpoch", todayEpoch ?: JSONObject.NULL)
        put("todayBankedMs", todayBankedMs)
        put("runStartedAtEpochMs", runStartedAtEpochMs ?: JSONObject.NULL)
        put("lastToggledAtEpochMs", lastToggledAtEpochMs ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): WorkState {
            val array = json.optJSONArray("cycles") ?: JSONArray()
            return WorkState(
                cycles = (0 until array.length()).map { WorkCycle.fromJson(array.getJSONObject(it)) },
                headRemainingMs = json.optLong("headRemainingMs", WorkSchedule.CYCLE_MS),
                lastCycleDayEpoch = if (json.isNull("lastCycleDayEpoch")) null else json.optLong("lastCycleDayEpoch"),
                lastStartedDayEpoch = if (json.isNull("lastStartedDayEpoch")) null else json.optLong("lastStartedDayEpoch"),
                todayEpoch = if (json.isNull("todayEpoch")) null else json.optLong("todayEpoch"),
                todayBankedMs = json.optLong("todayBankedMs", 0L),
                runStartedAtEpochMs = if (json.isNull("runStartedAtEpochMs")) null else json.optLong("runStartedAtEpochMs"),
                lastToggledAtEpochMs = if (json.isNull("lastToggledAtEpochMs")) null else json.optLong("lastToggledAtEpochMs"),
            )
        }

        fun parse(raw: String): WorkState =
            runCatching { fromJson(JSONObject(raw)) }.getOrDefault(WorkState())
    }
}

private fun startOfDayMs(day: LocalDate): Long =
    day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Today's banked time, discarding a previous day's. */
private fun WorkState.bankedFor(today: LocalDate): Long =
    if (todayEpoch == today.toEpochDay()) todayBankedMs else 0L

/** The timer just started running at [nowMs]. */
fun WorkState.runStarted(today: LocalDate, nowMs: Long): WorkState = copy(
    todayEpoch = today.toEpochDay(),
    todayBankedMs = bankedFor(today),
    runStartedAtEpochMs = nowMs,
    lastToggledAtEpochMs = nowMs,
)

/** The timer just stopped at [nowMs]: banks today's share of the run. */
fun WorkState.runStopped(today: LocalDate, nowMs: Long): WorkState = copy(
    todayEpoch = today.toEpochDay(),
    todayBankedMs = workedTodayMs(today, nowMs),
    runStartedAtEpochMs = null,
    lastToggledAtEpochMs = nowMs,
)

/**
 * The current cycle's remaining time was changed by hand (typed in, or reset
 * to 7.5 hours). Taking time off the main counter counts as time worked
 * today, and adding it back un-works it, which is what keeps the today
 * counter following every correction made to the main one.
 */
fun WorkState.withHeadRemainingEdited(newRemainingMs: Long, today: LocalDate): WorkState = copy(
    headRemainingMs = newRemainingMs,
    todayEpoch = today.toEpochDay(),
    // Deliberately not clamped here: the duration editor changes the counter
    // one keystroke at a time, so an intermediate value can briefly add back
    // far more than was worked, and the next keystroke has to undo it exactly.
    todayBankedMs = bankedFor(today) + (headRemainingMs - newRemainingMs),
)

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
