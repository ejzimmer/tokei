package com.ejzimmer.tokei.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejzimmer.tokei.alarm.AlarmEvents
import com.ejzimmer.tokei.alarm.AlarmScheduler
import com.ejzimmer.tokei.alarm.AlarmService
import com.ejzimmer.tokei.audio.AlarmPlayer
import com.ejzimmer.tokei.audio.soundById
import com.ejzimmer.tokei.data.RunCounts
import com.ejzimmer.tokei.data.TimerData
import com.ejzimmer.tokei.data.TimerRepository
import com.ejzimmer.tokei.data.TimerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DurationField { HOURS, MINUTES, SECONDS }

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimerRepository(application)
    private val previewPlayer = AlarmPlayer()

    private val _timers = MutableStateFlow(repository.load())
    val timers: StateFlow<List<TimerData>> = _timers.asStateFlow()

    // Bumped on every tick so Compose recomposes the live countdown text even
    // when nothing about the underlying TimerData actually changed.
    private val _clockTick = MutableStateFlow(0L)
    val clockTick: StateFlow<Long> = _clockTick.asStateFlow()

    init {
        persist()

        // AlarmReceiver is the single authority for "this timer just
        // finished" -- it always persists the transition, and tells us here
        // if we're also open so the UI updates immediately.
        viewModelScope.launch {
            AlarmEvents.timerFinished.collect { event ->
                mutate(event.timerId) {
                    it.status = TimerStatus.RINGING
                    it.finishedAtEpochMs = event.finishedAtEpochMs
                    it.lastFinishedAtEpochMs = event.finishedAtEpochMs
                    it.endAtEpochMs = null
                }
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
            while (true) {
                delay(250)
                _clockTick.value = System.currentTimeMillis()
            }
        }
    }

    private fun persist() = repository.save(_timers.value)

    private fun mutate(timerId: String, block: (TimerData) -> Unit) {
        val list = _timers.value.toMutableList()
        val timer = list.find { it.id == timerId } ?: return
        block(timer)
        _timers.value = list
        persist()
    }

    fun addTimer() {
        val list = _timers.value.toMutableList()
        list.add(repository.createTimer("Timer ${list.size + 1}"))
        _timers.value = list
        persist()
    }

    fun deleteTimer(timerId: String) {
        val timer = _timers.value.find { it.id == timerId } ?: return
        val context = getApplication<Application>()
        if (timer.status == TimerStatus.RUNNING) AlarmScheduler.cancel(context, timerId)
        if (timer.status == TimerStatus.RINGING) AlarmService.stop(context, timerId)
        RunCounts.clear(timerId)
        _timers.value = _timers.value.filterNot { it.id == timerId }
        persist()
    }

    fun rename(timerId: String, name: String) = mutate(timerId) { it.name = name.ifBlank { "Timer" } }

    fun setSound(timerId: String, soundId: String) = mutate(timerId) { it.soundId = soundId }

    fun previewSound(soundId: String) {
        previewPlayer.playOnce(soundById(soundId).previewNotes)
    }

    fun enterDigit(timerId: String, field: DurationField, digit: Int) = mutate(timerId) { timer ->
        if (timer.status != TimerStatus.IDLE) return@mutate
        val current = timer.fieldValue(field)
        timer.setFieldValue(field, (current * 10 + digit) % 100)
        timer.normalize()
    }

    fun backspaceDigit(timerId: String, field: DurationField) = mutate(timerId) { timer ->
        if (timer.status != TimerStatus.IDLE) return@mutate
        timer.setFieldValue(field, timer.fieldValue(field) / 10)
        timer.normalize()
    }

    fun start(timerId: String) {
        val timer = _timers.value.find { it.id == timerId } ?: return
        val durationMs = timer.pausedRemainingMs ?: timer.durationMs()
        if (durationMs <= 0) return
        val isFreshStart = timer.status == TimerStatus.IDLE
        val endAt = System.currentTimeMillis() + durationMs

        mutate(timerId) {
            it.endAtEpochMs = endAt
            it.pausedRemainingMs = null
            it.lastFinishedAtEpochMs = null
            it.status = TimerStatus.RUNNING
        }
        if (isFreshStart) RunCounts.recordRun(timerId)
        AlarmScheduler.schedule(getApplication<Application>(), timerId, endAt)
    }

    fun pause(timerId: String) {
        val timer = _timers.value.find { it.id == timerId } ?: return
        val endAt = timer.endAtEpochMs ?: return
        AlarmScheduler.cancel(getApplication<Application>(), timerId)
        mutate(timerId) {
            it.pausedRemainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            it.endAtEpochMs = null
            it.status = TimerStatus.PAUSED
        }
    }

    fun reset(timerId: String) {
        val timer = _timers.value.find { it.id == timerId } ?: return
        val context = getApplication<Application>()
        if (timer.status == TimerStatus.RUNNING) AlarmScheduler.cancel(context, timerId)
        if (timer.status == TimerStatus.RINGING) AlarmService.stop(context, timerId)
        mutate(timerId) {
            it.status = TimerStatus.IDLE
            it.endAtEpochMs = null
            it.pausedRemainingMs = null
            it.finishedAtEpochMs = null
        }
    }

    fun stopAlarm(timerId: String) {
        AlarmService.stop(getApplication<Application>(), timerId)
        mutate(timerId) {
            if (it.status == TimerStatus.RINGING) {
                it.status = TimerStatus.IDLE
                it.finishedAtEpochMs = null
            }
        }
    }
}

private fun TimerData.fieldValue(field: DurationField): Int = when (field) {
    DurationField.HOURS -> hours
    DurationField.MINUTES -> minutes
    DurationField.SECONDS -> seconds
}

private fun TimerData.setFieldValue(field: DurationField, value: Int) {
    when (field) {
        DurationField.HOURS -> hours = value
        DurationField.MINUTES -> minutes = value
        DurationField.SECONDS -> seconds = value
    }
}
