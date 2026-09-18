package com.ejzimmer.tokei.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TimerStatus { IDLE, RUNNING, PAUSED, RINGING }

/** A pomodoro's longest settable phase is 59:59 -- its card has no hours box. */
private const val MAX_POMODORO_MINUTES = 59

/** Which half of a pomodoro cycle a timer is currently in. Irrelevant for a
 * plain (non-pomodoro) timer, which always behaves as if it were WORK. */
enum class PomodoroPhase { WORK, REST }

fun PomodoroPhase.next(): PomodoroPhase = when (this) {
    PomodoroPhase.WORK -> PomodoroPhase.REST
    PomodoroPhase.REST -> PomodoroPhase.WORK
}

/**
 * One timer's full state. hours/minutes/seconds are the *configured* duration
 * and never change while running/paused/ringing -- endAtEpochMs and
 * pausedRemainingMs track the live countdown separately, mirroring the
 * original web version's model.
 *
 * A pomodoro timer reuses hours/minutes/seconds as its *work* duration and
 * adds a separate rest duration, alternating between the two via [phase]
 * every time it rings and is dismissed.
 */
data class TimerData(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var hours: Int = 0,
    var minutes: Int = 5,
    var seconds: Int = 0,
    var soundId: String,
    var status: TimerStatus = TimerStatus.IDLE,
    var endAtEpochMs: Long? = null,
    var pausedRemainingMs: Long? = null,
    var finishedAtEpochMs: Long? = null,
    var lastFinishedAtEpochMs: Long? = null,
    var isPomodoro: Boolean = false,
    var restHours: Int = 0,
    var restMinutes: Int = 5,
    var restSeconds: Int = 0,
    var phase: PomodoroPhase = PomodoroPhase.WORK,
    // Remaining ms for whichever pomodoro phase ISN'T [phase] -- populated
    // when tapping away from a phase that was running or paused, so its
    // progress survives the switch. Null means "untouched": use its full
    // configured duration. Meaningless for a non-pomodoro timer.
    var otherPhaseRemainingMs: Long? = null,
) {
    fun durationMs(phase: PomodoroPhase = this.phase): Long {
        val useRest = isPomodoro && phase == PomodoroPhase.REST
        // A pomodoro is mm:ss, so its hours never count toward the total --
        // including on a timer saved before normalize() started capping them,
        // which would otherwise still run an hour longer than the card reads.
        val h = if (isPomodoro) 0 else hours
        val m = if (useRest) restMinutes else minutes
        val s = if (useRest) restSeconds else seconds
        return ((h * 60L + m) * 60L + s) * 1000L
    }

    /**
     * Carries overflowing seconds into minutes, and overflowing minutes into
     * hours -- for both the work and rest fields, since a pomodoro timer can
     * have either one being edited.
     *
     * A pomodoro has no hours box to carry into, so its minutes stop at 59
     * instead: typing 99 there used to leave an invisible hour behind, so the
     * timer ran for 1h39m while the card read 39:00.
     */
    fun normalize() {
        if (seconds >= 60) {
            minutes += seconds / 60
            seconds %= 60
        }
        if (restSeconds >= 60) {
            restMinutes += restSeconds / 60
            restSeconds %= 60
        }
        if (isPomodoro) {
            hours = 0
            restHours = 0
            minutes = minutes.coerceAtMost(MAX_POMODORO_MINUTES)
            restMinutes = restMinutes.coerceAtMost(MAX_POMODORO_MINUTES)
            return
        }
        if (minutes >= 60) {
            hours += minutes / 60
            minutes %= 60
        }
        if (restMinutes >= 60) {
            restHours += restMinutes / 60
            restMinutes %= 60
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("hours", hours)
        put("minutes", minutes)
        put("seconds", seconds)
        put("soundId", soundId)
        put("status", status.name)
        put("endAtEpochMs", endAtEpochMs ?: JSONObject.NULL)
        put("pausedRemainingMs", pausedRemainingMs ?: JSONObject.NULL)
        put("finishedAtEpochMs", finishedAtEpochMs ?: JSONObject.NULL)
        put("lastFinishedAtEpochMs", lastFinishedAtEpochMs ?: JSONObject.NULL)
        put("isPomodoro", isPomodoro)
        put("restHours", restHours)
        put("restMinutes", restMinutes)
        put("restSeconds", restSeconds)
        put("phase", phase.name)
        put("otherPhaseRemainingMs", otherPhaseRemainingMs ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): TimerData = TimerData(
            id = json.getString("id"),
            name = json.getString("name"),
            hours = json.optInt("hours", 0),
            minutes = json.optInt("minutes", 5),
            seconds = json.optInt("seconds", 0),
            soundId = json.getString("soundId"),
            status = runCatching { TimerStatus.valueOf(json.getString("status")) }
                .getOrDefault(TimerStatus.IDLE),
            endAtEpochMs = json.optLongOrNull("endAtEpochMs"),
            pausedRemainingMs = json.optLongOrNull("pausedRemainingMs"),
            finishedAtEpochMs = json.optLongOrNull("finishedAtEpochMs"),
            lastFinishedAtEpochMs = json.optLongOrNull("lastFinishedAtEpochMs"),
            isPomodoro = json.optBoolean("isPomodoro", false),
            restHours = json.optInt("restHours", 0),
            restMinutes = json.optInt("restMinutes", 5),
            restSeconds = json.optInt("restSeconds", 0),
            phase = runCatching { PomodoroPhase.valueOf(json.getString("phase")) }
                .getOrDefault(PomodoroPhase.WORK),
            otherPhaseRemainingMs = json.optLongOrNull("otherPhaseRemainingMs"),
        )
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)

fun List<TimerData>.toJsonString(): String {
    val array = JSONArray()
    forEach { array.put(it.toJson()) }
    return array.toString()
}

fun parseTimerList(json: String): List<TimerData> {
    val array = JSONArray(json)
    return (0 until array.length()).map { i -> TimerData.fromJson(array.getJSONObject(i)) }
}

val TimerData.isWorkTimer: Boolean get() = id == WORK_TIMER_ID

/**
 * The one "this timer just ran out" transition, shared by every path that can
 * discover it: the alarm firing, a skip-ahead landing on zero, and the
 * catch-up pass for a boundary missed while the app was dead.
 *
 * A pomodoro hands over to its other phase right here, rather than waiting to
 * be dismissed. Dismissal used to own the handover, which meant it never
 * happened at all when the alarm was silenced from the notification instead of
 * the card -- and even in the app it left the finished phase indicated, with
 * nothing to press but Stop, while a self-silencing alarm had already gone
 * quiet on its own.
 */
fun TimerData.markFinished(finishedAtEpochMs: Long) {
    status = TimerStatus.RINGING
    this.finishedAtEpochMs = finishedAtEpochMs
    lastFinishedAtEpochMs = finishedAtEpochMs
    endAtEpochMs = null
    if (isPomodoro) {
        phase = phase.next()
        // The phase that just rang counted all the way down, so it starts
        // over at its full duration next time round.
        otherPhaseRemainingMs = null
    }
}

/** Writes a millisecond remainder back into the h/m/s fields, which is where
 * the work timer keeps its current cycle's remaining time while stopped (and
 * therefore what the duration editor adjusts). */
fun TimerData.setRemainingMs(ms: Long) {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    hours = (totalSeconds / 3600L).toInt()
    minutes = ((totalSeconds % 3600L) / 60L).toInt()
    seconds = (totalSeconds % 60L).toInt()
}
