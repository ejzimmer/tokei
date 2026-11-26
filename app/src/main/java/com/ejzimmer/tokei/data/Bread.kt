package com.ejzimmer.tokei.data

/** A plain countdown named "bread" walks through a bake: five stretch & folds,
 * then the evening proof, then a wait for the bread to go in the fridge. */
const val BREAD_TIMER_NAME = "bread"

/** The looping alarm, which keeps its notification up until it's dealt with. */
const val BREAD_SOUND_ID = "marimba"

const val BREAD_FOLD_COUNT = 5
private const val BREAD_PROOF_STEP = BREAD_FOLD_COUNT + 1
private const val BREAD_FOLD_MS = 30L * 60L * 1000L
private const val BREAD_PROOF_MS = 2L * 60L * 60L * 1000L

/** How long after a step finishes the alarm goes off again if the next one
 * hasn't been started, and how often it repeats after that. */
const val BREAD_REMINDER_INTERVAL_MS = 60L * 1000L

const val BREAD_FRIDGE_MESSAGE = "Put the bread in the fridge"

val TimerData.isBread: Boolean
    get() = !isWorkTimer && !isPomodoro && !isStopwatch &&
        name.trim().equals(BREAD_TIMER_NAME, ignoreCase = true)

val TimerData.isBreadWaitingForFridge: Boolean
    get() = isBread && breadStepsFinished >= BREAD_PROOF_STEP

/** A step has finished and the next one hasn't been started yet (or, after
 * the proof, the bread hasn't gone in the fridge) -- which is when the
 * reminder alarm keeps going off. */
val TimerData.isBreadWaitingForNextStep: Boolean
    get() = isBread && breadStepsFinished > 0 &&
        (status == TimerStatus.IDLE || status == TimerStatus.RINGING)

/** The step shown on the card. It moves on when the next step is started,
 * not when the last one finishes. */
fun TimerData.breadStepLabel(): String {
    if (isBreadWaitingForFridge) return BREAD_FRIDGE_MESSAGE
    val isCounting = status == TimerStatus.RUNNING || status == TimerStatus.PAUSED
    val step = if (isCounting || breadStepsFinished == 0) breadStepsFinished + 1 else breadStepsFinished
    return if (step == BREAD_PROOF_STEP) "Evening proof" else "Stretch & fold $step"
}

/** Puts the bake back at the first stretch & fold. */
fun TimerData.restartBread() {
    breadStepsFinished = 0
    setRemainingMs(BREAD_FOLD_MS)
}

/** Counts the step that just rang and sets the clock for the next one. */
fun TimerData.finishBreadStep() {
    breadStepsFinished = (breadStepsFinished + 1).coerceAtMost(BREAD_PROOF_STEP)
    if (breadStepsFinished == BREAD_PROOF_STEP) return
    setRemainingMs(if (breadStepsFinished == BREAD_FOLD_COUNT) BREAD_PROOF_MS else BREAD_FOLD_MS)
}

/** The bread is in the fridge: the bake is over, so the reminders stop and
 * the timer is ready for the next one. */
fun TimerData.putBreadInFridge() {
    status = TimerStatus.IDLE
    finishedAtEpochMs = null
    restartBread()
}
