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

    private val _workChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val workChanged = _workChanged.asSharedFlow()

    fun notifyFinished(timerId: String, finishedAtEpochMs: Long) {
        _timerFinished.tryEmit(Finished(timerId, finishedAtEpochMs))
    }

    fun notifyStopped(timerId: String) {
        _timerStopped.tryEmit(timerId)
    }

    /** The work timer rolled into its next cycle. It carries no payload on
     * purpose: the ledger has just been written to disk, and re-reading it is
     * both simpler and safer than trying to replay the same transition in the
     * ViewModel. */
    fun notifyWorkChanged() {
        _workChanged.tryEmit(Unit)
    }
}
