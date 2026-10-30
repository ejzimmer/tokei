package com.ejzimmer.tokei.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TimerStatus { IDLE, RUNNING, PAUSED, RINGING }

/** A pomodoro's longest settable phase is 59:59 -- its card has no hours box. */
const val MAX_POMODORO_MINUTES = 59

/** A pomodoro's rest alarm keeps going until it's stopped, so a break ending
 * isn't missed the way a short self-silencing alarm can be. Also what
 * pomodoros saved before the rest phase had its own sound pick up. */
const val REST_DEFAULT_SOUND_ID = "marimba"

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
 *
 * Typing over a pomodoro's durations is a one-off adjustment: [savedWorkMs]
 * and [savedRestMs] hold the durations it was set up with, and each phase
 * goes back to its saved length once it has rung (or the timer is reset).
 * Null means nothing has been saved yet -- a pomodoro that has never been
 * started -- so whatever it reads at its first Start becomes the saved pair.
 * Each phase also has its own alarm: [soundId] for work, [restSoundId] for
 * rest.
 *
 * A stopwatch ignores the duration fields and counts up instead: while
 * running, [stopwatchStartAtEpochMs] is the moment it would have started had
 * it never been paused, so elapsed time is just now minus that; while paused,
 * [stopwatchElapsedMs] holds the banked total. It never rings.
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
    var savedWorkMs: Long? = null,
    var savedRestMs: Long? = null,
    var restSoundId: String = REST_DEFAULT_SOUND_ID,
    var isStopwatch: Boolean = false,
    var stopwatchStartAtEpochMs: Long? = null,
    var stopwatchElapsedMs: Long = 0L,
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
        put("savedWorkMs", savedWorkMs ?: JSONObject.NULL)
        put("savedRestMs", savedRestMs ?: JSONObject.NULL)
        put("restSoundId", restSoundId)
        put("isStopwatch", isStopwatch)
        put("stopwatchStartAtEpochMs", stopwatchStartAtEpochMs ?: JSONObject.NULL)
        put("stopwatchElapsedMs", stopwatchElapsedMs)
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
            savedWorkMs = json.optLongOrNull("savedWorkMs"),
            savedRestMs = json.optLongOrNull("savedRestMs"),
            restSoundId = json.optString("restSoundId", REST_DEFAULT_SOUND_ID),
            isStopwatch = json.optBoolean("isStopwatch", false),
            stopwatchStartAtEpochMs = json.optLongOrNull("stopwatchStartAtEpochMs"),
            stopwatchElapsedMs = json.optLong("stopwatchElapsedMs", 0L),
        )
    }
}

// isNull() is also true for a missing key, so fields added later read as null
// on timers saved before they existed.
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

/** The three kinds of timer you can add yourself (the work timer is built in). */
enum class TimerKind { COUNTDOWN, POMODORO, STOPWATCH }

/** How long a stopwatch has been counting, as of [nowMs]. */
fun TimerData.stopwatchElapsedAt(nowMs: Long): Long {
    val startAt = stopwatchStartAtEpochMs
    return if (status == TimerStatus.RUNNING && startAt != null) {
        (nowMs - startAt).coerceAtLeast(0L)
    } else {
        stopwatchElapsedMs
    }
}

/**
 * The one "this timer just ran out" transition, shared by every path that can
 * discover it: the alarm firing (seen by AlarmReceiver and the live UI), and
 * the catch-up pass for a boundary missed while the app was dead.
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
        // A one-off adjustment only lasts the one run: the phase that just
        // rang goes back to the length it was set up with.
        savedMsFor(phase)?.let { setPhaseDurationMs(phase, it) }
        phase = phase.next()
        // The phase that just rang counted all the way down, so it starts
        // over at its full duration next time round.
        otherPhaseRemainingMs = null
    }
}

/** The alarm for the phase that's counting down right now -- i.e. the one
 * that rings next. Call it before [markFinished], which moves past it. */
fun TimerData.alarmSoundId(): String =
    if (isPomodoro && phase == PomodoroPhase.REST) restSoundId else soundId

fun TimerData.savedMsFor(phase: PomodoroPhase): Long? = when (phase) {
    PomodoroPhase.WORK -> savedWorkMs
    PomodoroPhase.REST -> savedRestMs
}

/** Remembers the current durations as the ones to go back to, if nothing has
 * been remembered yet. */
fun TimerData.saveDurationsIfUnset() {
    if (!isPomodoro) return
    if (savedWorkMs == null) savedWorkMs = durationMs(PomodoroPhase.WORK)
    if (savedRestMs == null) savedRestMs = durationMs(PomodoroPhase.REST)
}

/** Puts both phases back to their saved lengths, as Reset does. */
fun TimerData.restoreSavedDurations() {
    PomodoroPhase.entries.forEach { phase ->
        savedMsFor(phase)?.let { setPhaseDurationMs(phase, it) }
    }
}

/** Writes [ms] into a pomodoro phase's mm:ss fields. */
fun TimerData.setPhaseDurationMs(phase: PomodoroPhase, ms: Long) {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val m = (totalSeconds / 60L).toInt().coerceAtMost(MAX_POMODORO_MINUTES)
    val s = (totalSeconds % 60L).toInt()
    when (phase) {
        PomodoroPhase.WORK -> { minutes = m; seconds = s }
        PomodoroPhase.REST -> { restMinutes = m; restSeconds = s }
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
