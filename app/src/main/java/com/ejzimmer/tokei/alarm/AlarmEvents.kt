package com.ejzimmer.tokei.alarm

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * AlarmReceiver is the single authority for the running-to-ringing
 * transition (it's the one guaranteed to run whether or not the app process
 * is alive, since it's driven by a real system alarm). If the app also
 * happens to be open at that moment, its ViewModel needs to hear about the
 * change directly rather than keep its own stale in-memory copy -- this is
 * that in-process bridge, equivalent to the web version's service-worker-to
 * -page postMessage.
 */
object AlarmEvents {
    data class Finished(val timerId: String, val finishedAtEpochMs: Long)

    private val _timerFinished = MutableSharedFlow<Finished>(extraBufferCapacity = 8)
    val timerFinished = _timerFinished.asSharedFlow()

    private val _timerStopped = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val timerStopped = _timerStopped.asSharedFlow()

    fun notifyFinished(timerId: String, finishedAtEpochMs: Long) {
        _timerFinished.tryEmit(Finished(timerId, finishedAtEpochMs))
    }

    fun notifyStopped(timerId: String) {
        _timerStopped.tryEmit(timerId)
    }
}
