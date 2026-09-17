package com.ejzimmer.tokei.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejzimmer.tokei.alarm.AlarmEvents
import com.ejzimmer.tokei.alarm.AlarmScheduler
import com.ejzimmer.tokei.alarm.AlarmService
import com.ejzimmer.tokei.alarm.CountdownNotifier
import com.ejzimmer.tokei.alarm.WorkReminderScheduler
import com.ejzimmer.tokei.audio.AlarmPlayer
import com.ejzimmer.tokei.audio.soundById
import com.ejzimmer.tokei.data.PomodoroPhase
import com.ejzimmer.tokei.data.RunCounts
import com.ejzimmer.tokei.data.TimerData
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus
import com.ejzimmer.tokei.data.WORK_TIMER_ID
import com.ejzimmer.tokei.data.WorkSchedule
import com.ejzimmer.tokei.data.WorkState
import com.ejzimmer.tokei.data.claimedForStart
import com.ejzimmer.tokei.data.cycleCompleted
import com.ejzimmer.tokei.data.isWorkTimer
import com.ejzimmer.tokei.data.localDateOf
import com.ejzimmer.tokei.data.markFinished
import com.ejzimmer.tokei.data.reconciled
import com.ejzimmer.tokei.data.setRemainingMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DurationField { HOURS, MINUTES, SECONDS }

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimerRepository(application)
    private val previewPlayer = AlarmPlayer()

    private val _timers = MutableStateFlow<List<TimerData>>(repository.load())
    val timers: StateFlow<List<TimerData>> = _timers.asStateFlow()

    // Bumped on every tick so Compose recomposes the live countdown text even
    // when nothing about the underlying TimerData actually changed.
    private val _clockTick = MutableStateFlow(0L)
    val clockTick: StateFlow<Long> = _clockTick.asStateFlow()

    private val _workState = MutableStateFlow(repository.loadWorkState())
    val workState: StateFlow<WorkState> = _workState.asStateFlow()

    init {
        catchUpWork()
        persist()
        WorkReminderScheduler.scheduleAll(application)

        // AlarmReceiver is the single authority for "this timer just
        // finished" -- it always persists the transition, and tells us here
        // if we're also open so the UI updates immediately.
        viewModelScope.launch {
            AlarmEvents.timerFinished.collect { event ->
                CountdownNotifier.cancel(getApplication<Application>(), event.timerId)
                // Same transition AlarmReceiver just persisted, applied to the
                // copy held here -- both start from the pre-ring phase, so
                // they land on the same side rather than flipping twice.
                mutate(event.timerId) { it.markFinished(event.finishedAtEpochMs) }
            }
        }
        viewModelScope.launch {
            AlarmEvents.timerStopped.collect { timerId ->
                mutate(timerId) {
                    if (it.status == TimerStatus.RINGING) {
                        it.status = TimerStatus.IDLE
                        it.finishedAtEpochMs = null
                    }
                }
            }
        }
        viewModelScope.launch {
            AlarmEvents.workChanged.collect {
                // AlarmReceiver already wrote both halves of the rollover to
                // disk; re-read rather than trying to replay it here.
                _timers.value = repository.loadRaw()
                _workState.value = repository.loadWorkState()
            }
        }
        viewModelScope.launch {
            var lastDay = LocalDate.now().toEpochDay()
            while (true) {
                delay(250)
                _clockTick.value = System.currentTimeMillis()

                // Crossing midnight can retire a work day that was never
                // worked, which changes which day the timer is counting for.
                val today = LocalDate.now().toEpochDay()
                if (today != lastDay) {
                    lastDay = today
                    catchUpWork()
                }
            }
        }
    }

    private fun persist() = repository.save(_timers.value)

    private fun mutate(timerId: String, block: (TimerData) -> Unit) {
        val list = _timers.value.toMutableList()
        val index = list.indexOfFirst { it.id == timerId }
        if (index == -1) return
        // Mutate a fresh copy, not the object already sitting in _timers.value:
        // TimerData's fields are var, so mutating the original in place would
        // make the "old" and "new" list contents identical by the time
        // StateFlow compares them, and it silently drops updates that look
        // like no-ops -- which is exactly what looked like "typing does
        // nothing" from the UI.
        val copy = list[index].copy()
        block(copy)
        list[index] = copy
        _timers.value = list
        persist()
    }

    fun addTimer(isPomodoro: Boolean = false) {
        val list = _timers.value.toMutableList()
        val name = if (isPomodoro) {
            "Pomodoro ${list.count { it.isPomodoro } + 1}"
        } else {
            "Timer ${list.count { !it.isWorkTimer && !it.isPomodoro } + 1}"
        }
        list.add(repository.createTimer(name, isPomodoro))
        _timers.value = list
        persist()
    }

    fun deleteTimer(timerId: String) {
        val timer = _timers.value.find { it.id == timerId } ?: return
        // The work timer is part of the app, not a timer you made.
        if (timer.isWorkTimer) return
        val context = getApplication<Application>()
        if (timer.status == TimerStatus.RUNNING) {
            AlarmScheduler.cancel(context, timerId)
            CountdownNotifier.cancel(context, timerId)
        }
        if (timer.status == TimerStatus.RINGING) AlarmService.stop(context, timerId)
        RunCounts.clear(timerId)
        _timers.value = _timers.value.filterNot { it.id == timerId }
        persist()
    }

    // Blank is allowed here: the UI decides when an empty name should fall
    // back to "Timer" (on focus loss), not every debounced keystroke while
    // the user is still mid-edit.
    fun rename(timerId: String, name: String) = mutate(timerId) { it.name = name }

    fun setSound(timerId: String, soundId: String) = mutate(timerId) { it.soundId = soundId }

    fun previewSound(soundId: String) {
        previewPlayer.playOnce(soundById(soundId).previewNotes)
    }

    fun enterDigit(timerId: String, phase: PomodoroPhase, field: DurationField, digit: Int) {
        mutate(timerId) { timer ->
            if (timer.status != TimerStatus.IDLE) return@mutate
            val current = timer.fieldValue(phase, field)
            timer.setFieldValue(phase, field, (current * 10 + digit) % 100)
            timer.normalize()
        }
        syncWorkRemainingFromDigits(timerId)
    }

    fun backspaceDigit(timerId: String, phase: PomodoroPhase, field: DurationField) {
        mutate(timerId) { timer ->
            if (timer.status != TimerStatus.IDLE) return@mutate
            timer.setFieldValue(phase, field, timer.fieldValue(phase, field) / 10)
            timer.normalize()
        }
        syncWorkRemainingFromDigits(timerId)
    }

    /** For the work timer the duration fields *are* the current cycle's
     * remaining time, so editing them is how "I forgot to start/stop it" gets
     * corrected. Later cycles are untouched -- they're always a full 7.5
     * hours, held separately in the ledger. */
    private fun syncWorkRemainingFromDigits(timerId: String) {
        if (timerId != WORK_TIMER_ID) return
        val timer = _timers.value.find { it.isWorkTimer } ?: return
        // Only meaningful while stopped; mid-countdown the fields are a stale
        // snapshot and copying them into the ledger would lose real time.
        if (timer.status != TimerStatus.IDLE) return
        setWorkState(_workState.value.copy(headRemainingMs = timer.durationMs()))
    }

    fun start(timerId: String) {
        if (timerId == WORK_TIMER_ID) return startWork()
        val timer = _timers.value.find { it.id == timerId } ?: return
        val durationMs = timer.pausedRemainingMs ?: timer.durationMs()
        if (durationMs <= 0) return
        val context = getApplication<Application>()
        // Starting straight out of a ringing alarm silences it, so moving on
        // to the next phase is one press rather than Stop and then Start.
        if (timer.status == TimerStatus.RINGING) AlarmService.stop(context, timerId)
        // Resuming a pause isn't a new run; picking up from a finished alarm is
        // -- that used to be counted on the way back through IDLE.
        val isFreshStart = timer.status != TimerStatus.PAUSED
        val endAt = System.currentTimeMillis() + durationMs

        mutate(timerId) {
            it.endAtEpochMs = endAt
            it.pausedRemainingMs = null
            it.lastFinishedAtEpochMs = null
            it.finishedAtEpochMs = null
            it.status = TimerStatus.RUNNING
        }
        if (isFreshStart) RunCounts.recordRun(timerId)
        AlarmScheduler.schedule(context, timerId, endAt)
        CountdownNotifier.show(context, timerId, timer.name, endAt)
    }

    fun pause(timerId: String) {
        if (timerId == WORK_TIMER_ID) return pauseWork()
        val timer = _timers.value.find { it.id == timerId } ?: return
        val endAt = timer.endAtEpochMs ?: return
        val context = getApplication<Application>()
        AlarmScheduler.cancel(context, timerId)
        CountdownNotifier.cancel(context, timerId)
        mutate(timerId) {
            it.pausedRemainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            it.endAtEpochMs = null
            it.status = TimerStatus.PAUSED
        }
    }

    fun reset(timerId: String) {
        if (timerId == WORK_TIMER_ID) return resetWork()
        val timer = _timers.value.find { it.id == timerId } ?: return
        val context = getApplication<Application>()
        if (timer.status == TimerStatus.RUNNING) {
            AlarmScheduler.cancel(context, timerId)
            CountdownNotifier.cancel(context, timerId)
        }
        if (timer.status == TimerStatus.RINGING) AlarmService.stop(context, timerId)
        mutate(timerId) {
            it.status = TimerStatus.IDLE
            it.endAtEpochMs = null
            it.pausedRemainingMs = null
            it.finishedAtEpochMs = null
            // Resetting a pomodoro starts the whole cycle over, not just the
            // phase it happened to be sitting in.
            if (it.isPomodoro) {
                it.phase = PomodoroPhase.WORK
                it.otherPhaseRemainingMs = null
            }
        }
    }

    /** Silences the alarm and leaves the timer idle. A pomodoro's handover to
     * its other phase already happened when it rang, so this is only ever
     * "quiet, please" -- pressing Start instead skips it entirely. */
    fun stopAlarm(timerId: String) {
        AlarmService.stop(getApplication<Application>(), timerId)
        mutate(timerId) {
            if (it.status == TimerStatus.RINGING) {
                it.status = TimerStatus.IDLE
                it.finishedAtEpochMs = null
            }
        }
    }

    /**
     * Makes [phase] the indicated (running/about-to-run) side of a pomodoro
     * timer. If the other side was actively counting down, it's stopped --
     * its remaining time is kept, not discarded -- and [phase] does NOT
     * start automatically; the user still has to press Play.
     */
    fun selectPhase(timerId: String, phase: PomodoroPhase) {
        val timer = _timers.value.find { it.id == timerId } ?: return
        if (!timer.isPomodoro || timer.phase == phase) return

        when (timer.status) {
            TimerStatus.RUNNING -> {
                val endAt = timer.endAtEpochMs ?: return
                val leavingRemaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
                val enteringRemaining = timer.otherPhaseRemainingMs ?: timer.durationMs(phase)
                val context = getApplication<Application>()
                AlarmScheduler.cancel(context, timerId)
                CountdownNotifier.cancel(context, timerId)
                mutate(timerId) {
                    it.otherPhaseRemainingMs = leavingRemaining
                    it.phase = phase
                    it.pausedRemainingMs = enteringRemaining
                    it.endAtEpochMs = null
                    it.status = TimerStatus.PAUSED
                }
            }
            TimerStatus.PAUSED -> {
                val leavingRemaining = timer.pausedRemainingMs ?: timer.durationMs(timer.phase)
                val enteringRemaining = timer.otherPhaseRemainingMs ?: timer.durationMs(phase)
                mutate(timerId) {
                    it.otherPhaseRemainingMs = leavingRemaining
                    it.phase = phase
                    it.pausedRemainingMs = enteringRemaining
                }
            }
            TimerStatus.IDLE -> mutate(timerId) { it.phase = phase }
            TimerStatus.RINGING -> {}
        }
    }

    /**
     * Nudges a running or paused pomodoro's live countdown by a fixed
     * increment, without changing phase -- for correcting the clock after
     * forgetting to unpause, rather than restarting the phase from scratch.
     */
    fun skipForward(timerId: String) = nudge(timerId, SKIP_INCREMENT_MS)

    fun skipBack(timerId: String) = nudge(timerId, -SKIP_INCREMENT_MS)

    private fun nudge(timerId: String, deltaMs: Long) {
        val timer = _timers.value.find { it.id == timerId } ?: return
        if (!timer.isPomodoro) return
        val maxMs = timer.durationMs()
        when (timer.status) {
            TimerStatus.RUNNING -> {
                val endAt = timer.endAtEpochMs ?: return
                val now = System.currentTimeMillis()
                val remaining = (endAt - now).coerceIn(0L, maxMs)
                val newRemaining = (remaining - deltaMs).coerceIn(0L, maxMs)
                if (newRemaining <= 0L) {
                    finishNow(timerId)
                    return
                }
                val newEndAt = now + newRemaining
                val context = getApplication<Application>()
                AlarmScheduler.schedule(context, timerId, newEndAt)
                CountdownNotifier.show(context, timerId, timer.name, newEndAt)
                mutate(timerId) { it.endAtEpochMs = newEndAt }
            }
            TimerStatus.PAUSED -> {
                val remaining = timer.pausedRemainingMs ?: return
                val newRemaining = (remaining - deltaMs).coerceIn(0L, maxMs)
                mutate(timerId) { it.pausedRemainingMs = newRemaining }
            }
            else -> {}
        }
    }

    /** What AlarmReceiver would do if its alarm fired right now -- used when a
     * "skip ahead" nudge runs the remaining time down to zero early. */
    private fun finishNow(timerId: String) {
        val timer = _timers.value.find { it.id == timerId } ?: return
        val context = getApplication<Application>()
        AlarmScheduler.cancel(context, timerId)
        CountdownNotifier.cancel(context, timerId)
        val now = System.currentTimeMillis()
        mutate(timerId) { it.markFinished(now) }
        AlarmService.start(context, timerId, timer.name, timer.soundId)
    }

    // -- Work timer ---------------------------------------------------------

    private fun setWorkState(state: WorkState) {
        _workState.value = state
        repository.saveWorkState(state)
    }

    /**
     * Starting the work timer is also what claims today as a work day, which
     * is the whole mechanism behind "a day you never start it is a day off":
     * an untouched day simply never adds its 7.5 hours to the ledger.
     */
    private fun startWork() {
        val timer = _timers.value.find { it.isWorkTimer } ?: return
        // While stopped, the duration fields are the live remaining time --
        // including any adjustment just typed into them.
        val remaining = timer.durationMs().takeIf { it > 0L } ?: WorkSchedule.CYCLE_MS
        val state = _workState.value
            .copy(headRemainingMs = remaining)
            .claimedForStart(LocalDate.now())
        setWorkState(state)

        val endAt = System.currentTimeMillis() + state.headRemainingMs
        mutate(timer.id) {
            it.endAtEpochMs = endAt
            it.pausedRemainingMs = null
            it.status = TimerStatus.RUNNING
            it.setRemainingMs(state.headRemainingMs)
        }

        val context = getApplication<Application>()
        AlarmScheduler.schedule(context, timer.id, endAt)
        CountdownNotifier.show(context, timer.id, timer.name, endAt)
    }

    /**
     * Stopping banks what's left of the cycle rather than discarding it, and
     * drops back to IDLE rather than PAUSED so the duration fields become
     * editable again -- for this timer, "resume" and "adjust then start" are
     * the same gesture.
     */
    private fun pauseWork() {
        val timer = _timers.value.find { it.isWorkTimer } ?: return
        val endAt = timer.endAtEpochMs ?: return
        val context = getApplication<Application>()
        AlarmScheduler.cancel(context, timer.id)
        CountdownNotifier.cancel(context, timer.id)

        val remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        setWorkState(_workState.value.copy(headRemainingMs = remaining))
        mutate(timer.id) {
            it.status = TimerStatus.IDLE
            it.endAtEpochMs = null
            it.pausedRemainingMs = null
            it.setRemainingMs(remaining)
        }
    }

    /** Puts this cycle back to a full 7.5 hours without touching which day
     * it counts for, or any day queued behind it. */
    private fun resetWork() {
        val timer = _timers.value.find { it.isWorkTimer } ?: return
        val context = getApplication<Application>()
        if (timer.status == TimerStatus.RUNNING) {
            AlarmScheduler.cancel(context, timer.id)
            CountdownNotifier.cancel(context, timer.id)
        }
        setWorkState(_workState.value.copy(headRemainingMs = WorkSchedule.CYCLE_MS))
        mutate(timer.id) {
            it.status = TimerStatus.IDLE
            it.endAtEpochMs = null
            it.pausedRemainingMs = null
            it.setRemainingMs(WorkSchedule.CYCLE_MS)
        }
    }

    /**
     * Brings the ledger up to date on open and at each midnight: retires days
     * that went by unworked, and books any cycle boundaries that passed while
     * nothing was around to notice (AlarmReceiver normally gets there first,
     * but it can be missed if alarms were blocked or the app was reinstalled).
     */
    private fun catchUpWork() {
        val timer = _timers.value.find { it.isWorkTimer } ?: return
        val today = LocalDate.now()
        val endAt = timer.endAtEpochMs

        if (timer.status != TimerStatus.RUNNING || endAt == null) {
            setWorkState(_workState.value.reconciled(today))
            return
        }

        val now = System.currentTimeMillis()
        if (endAt > now) return

        var state = _workState.value
        var boundary = endAt
        var completed = 0
        do {
            state = state.cycleCompleted(localDateOf(boundary))
            boundary += state.headRemainingMs
            completed++
        } while (boundary <= now && completed < MAX_CATCH_UP_CYCLES)

        setWorkState(state)
        val newEndAt = boundary
        mutate(timer.id) {
            it.endAtEpochMs = newEndAt
            it.setRemainingMs(state.headRemainingMs)
        }
        val context = getApplication<Application>()
        AlarmScheduler.schedule(context, timer.id, newEndAt)
        CountdownNotifier.show(context, timer.id, timer.name, newEndAt)
    }

    private companion object {
        const val MAX_CATCH_UP_CYCLES = 32
        const val SKIP_INCREMENT_MS = 10 * 60_000L
    }
}

/** For a plain timer [phase] is always WORK, so this reads/writes the same
 * hours/minutes/seconds fields as before pomodoro existed. */
private fun TimerData.fieldValue(phase: PomodoroPhase, field: DurationField): Int = when (phase) {
    PomodoroPhase.WORK -> when (field) {
        DurationField.HOURS -> hours
        DurationField.MINUTES -> minutes
        DurationField.SECONDS -> seconds
    }
    PomodoroPhase.REST -> when (field) {
        DurationField.HOURS -> restHours
        DurationField.MINUTES -> restMinutes
        DurationField.SECONDS -> restSeconds
    }
}

private fun TimerData.setFieldValue(phase: PomodoroPhase, field: DurationField, value: Int) {
    when (phase) {
        PomodoroPhase.WORK -> when (field) {
            DurationField.HOURS -> hours = value
            DurationField.MINUTES -> minutes = value
            DurationField.SECONDS -> seconds = value
        }
        PomodoroPhase.REST -> when (field) {
            DurationField.HOURS -> restHours = value
            DurationField.MINUTES -> restMinutes = value
            DurationField.SECONDS -> restSeconds = value
        }
    }
}
