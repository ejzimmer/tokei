package com.ejzimmer.tokei.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejzimmer.tokei.audio.SOUNDS
import com.ejzimmer.tokei.data.PomodoroPhase
import com.ejzimmer.tokei.data.TimerData
import com.ejzimmer.tokei.data.TimerStatus
import com.ejzimmer.tokei.data.WorkSchedule
import com.ejzimmer.tokei.data.stopwatchElapsedAt
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimerCard(
    timer: TimerData,
    runCount: Int,
    nowMs: Long,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSoundChange: (String) -> Unit,
    onPreviewSound: () -> Unit,
    onDigit: (PomodoroPhase, DurationField, Int) -> Unit,
    onBackspace: (PomodoroPhase, DurationField) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onStopAlarm: () -> Unit,
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onSelectPhase: (PomodoroPhase) -> Unit = {},
    workInfo: WorkCardInfo? = null,
    autoFocus: Boolean = false,
    onAutoFocused: () -> Unit = {},
) {
    // A freshly added timer takes focus in its first duration box -- setting
    // the time is the next thing to do with it, and the focused box pulls
    // the card into view above the keyboard.
    val firstFieldFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            // Only an idle timer has duration boxes to focus; a new one
            // always is, but don't crash if that ever stops being true.
            runCatching { firstFieldFocus.requestFocus() }
            onAutoFocused()
        }
    }
    if (workInfo != null) {
        WorkCard(timer = timer, info = workInfo, nowMs = nowMs, onDigit = onDigit, onBackspace = onBackspace, onStartPause = onStartPause)
        return
    }
    val isRinging = timer.status == TimerStatus.RINGING
    val borderColor = when (timer.status) {
        TimerStatus.RUNNING -> Accent
        TimerStatus.RINGING -> Danger
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isRinging) Danger.copy(alpha = 0.25f) else Surface, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Header(name = timer.name, onRename = onRename, onDelete = onDelete)

        if (timer.isStopwatch) {
            // Counts up, so there's nothing to set: the same big read-only
            // clock a running countdown shows, from zero.
            val totalSeconds = timer.stopwatchElapsedAt(nowMs) / 1000
            ReadOnlyDuration(
                (totalSeconds / 3600).toInt(),
                ((totalSeconds % 3600) / 60).toInt(),
                (totalSeconds % 60).toInt(),
                accent = timer.status == TimerStatus.RUNNING,
            )
        } else if (timer.isPomodoro) {
            PomodoroDualDisplay(
                timer = timer,
                nowMs = nowMs,
                onSelectPhase = onSelectPhase,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onSubmit = onStartPause,
                firstFieldFocus = firstFieldFocus,
            )
        } else if (timer.status == TimerStatus.IDLE) {
            EditableDurationFields(
                timer.hours, timer.minutes, timer.seconds,
                onDigit = { field, digit -> onDigit(PomodoroPhase.WORK, field, digit) },
                onBackspace = { field -> onBackspace(PomodoroPhase.WORK, field) },
                // Only editable while idle, so this is always a Start.
                onSubmit = onStartPause,
                focusRequester = firstFieldFocus,
            )
        } else {
            val (h, m, s) = remainingParts(timer, nowMs)
            ReadOnlyDuration(h, m, s, accent = timer.status == TimerStatus.RUNNING)
        }

        timer.lastFinishedAtEpochMs?.let {
            Text(
                "Last finished ${formatClockTime(it)}",
                color = FaceDim,
                fontSize = 12.sp,
            )
        }

        if (runCount > 0) {
            Text(
                if (runCount == 1) "Run once today" else "Run $runCount times today",
                color = FaceDim,
                fontSize = 12.sp,
            )
        }

        if (timer.isPomodoro && (timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onSkipBack) {
                    Text("◀ 10 min", color = FaceDim)
                }
                TextButton(onClick = onSkipForward) {
                    Text("10 min ▶", color = FaceDim)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised, contentColor = Face),
            ) {
                Icon(
                    ResetIcon,
                    contentDescription = "Reset",
                    modifier = Modifier.size(20.dp),
                )
            }
            Button(
                onClick = onStartPause,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
            ) {
                Icon(
                    if (timer.status == TimerStatus.RUNNING) PauseIcon else StartIcon,
                    contentDescription = startPauseLabel(timer.status, isWork = false),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (isRinging) {
            // A square, not the pause bars: this one silences the alarm
            // outright rather than holding anything mid-count.
            Button(
                onClick = onStopAlarm,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised, contentColor = Face),
            ) {
                Icon(StopIcon, contentDescription = "Stop", modifier = Modifier.size(20.dp))
            }
        }

        // Below the buttons: picking a tone is setup, not something you reach
        // for mid-countdown. A stopwatch never ends, so it has no alarm
        // sound to pick.
        if (!timer.isStopwatch) {
            SoundRow(soundId = timer.soundId, onSoundChange = onSoundChange, onPreview = onPreviewSound)
        }
    }
}

/**
 * The work timer as one dial: the outer ring fills as the day being counted
 * down is worked off, the inner ring fills with today's 7.5 hours and laps
 * in [Done] past them, and the start/stop button sits in the middle. Beside
 * it, the counter, then two icon-led times: when it was last started or
 * stopped, and the flag -- when the outer ring fills with no more breaks, or
 * when it did fill, once the timer has moved on to a later day.
 *
 * No reset and no name: the time is corrected by typing over the counter
 * while it's stopped, and the dial is what marks this card as the work timer.
 */
@Composable
private fun WorkCard(
    timer: TimerData,
    info: WorkCardInfo,
    nowMs: Long,
    onDigit: (PomodoroPhase, DurationField, Int) -> Unit,
    onBackspace: (PomodoroPhase, DurationField) -> Unit,
    onStartPause: () -> Unit,
) {
    val isIdle = timer.status == TimerStatus.IDLE
    val ringRemainingMs = if (isIdle) timer.durationMs() else remainingMs(timer, nowMs)
    val ringFraction = 1f - ringRemainingMs.toFloat() / WorkSchedule.CYCLE_MS
    val todayFraction = info.workedTodayMs.toFloat() / WorkSchedule.CYCLE_MS

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(20.dp))
            .border(1.dp, if (info.isRunning) Accent else Color.Transparent, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            WorkDial(ringFraction = ringFraction, todayFraction = todayFraction, modifier = Modifier.fillMaxSize())
            Button(
                onClick = onStartPause,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
            ) {
                Icon(
                    if (info.isRunning) PauseIcon else StartIcon,
                    contentDescription = startPauseLabel(timer.status, isWork = true),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                info.dayLabel.uppercase(),
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            if (isIdle) {
                EditableDurationFields(
                    timer.hours, timer.minutes, timer.seconds,
                    onDigit = { field, digit -> onDigit(PomodoroPhase.WORK, field, digit) },
                    onBackspace = { field -> onBackspace(PomodoroPhase.WORK, field) },
                    onSubmit = onStartPause,
                    boxWidth = 42.dp,
                    fontSize = 26.sp,
                )
            } else {
                val (h, m, s) = remainingParts(timer, nowMs)
                Text("$h:${pad2(m)}:${pad2(s)}", color = Face, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            info.lastToggleLabel?.let {
                WorkTime(
                    icon = if (info.isRunning) StartIcon else PauseIcon,
                    description = if (info.isRunning) "Started at" else "Stopped at",
                    text = it,
                    color = Face,
                )
            }
            WorkTime(
                icon = FlagIcon,
                description = if (info.flagIsFilled) "Ring filled at" else "Ring fills at",
                text = info.flagLabel,
                color = if (info.flagIsFilled) Done else Face,
            )
        }
    }
}

/** Two concentric rings, both starting from 12 o'clock. Past a full inner
 * ring, the overtime lap draws over it in [Done]. */
@Composable
private fun WorkDial(ringFraction: Float, todayFraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outerWidth = 10.dp.toPx()
        val innerWidth = 5.dp.toPx()
        val gap = 9.dp.toPx()

        fun ring(inset: Float, width: Float, fraction: Float, color: Color, track: Boolean) {
            val topLeft = Offset(inset + width / 2, inset + width / 2)
            val size = Size(this.size.width - 2 * topLeft.x, this.size.height - 2 * topLeft.y)
            if (track) drawArc(Track, 0f, 360f, false, topLeft, size, style = Stroke(width))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(color, -90f, sweep, false, topLeft, size, style = Stroke(width, cap = StrokeCap.Round))
            }
        }

        ring(0f, outerWidth, ringFraction, Accent, track = true)
        val innerInset = outerWidth + gap
        ring(innerInset, innerWidth, todayFraction, Face.copy(alpha = 0.8f), track = true)
        if (todayFraction > 1f) ring(innerInset, innerWidth, todayFraction - 1f, Done, track = false)
    }
}

@Composable
private fun WorkTime(icon: ImageVector, description: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = description, tint = if (color == Face) FaceDim else color, modifier = Modifier.size(16.dp))
        Text(text, color = color, fontSize = 15.sp)
    }
}

private val Track = Color(0xFF34345C)

/**
 * The work/rest timers side by side, mm:ss only. Whichever is indicated
 * (running, or about to run on Play) carries the dot; tapping the other
 * makes IT the indicated side, stopping the first without discarding its
 * remaining time. Editable while IDLE, read-only countdowns otherwise.
 */
@Composable
private fun PomodoroDualDisplay(
    timer: TimerData,
    nowMs: Long,
    onSelectPhase: (PomodoroPhase) -> Unit,
    onDigit: (PomodoroPhase, DurationField, Int) -> Unit,
    onBackspace: (PomodoroPhase, DurationField) -> Unit,
    onSubmit: () -> Unit,
    firstFieldFocus: FocusRequester,
) {
    val editable = timer.status == TimerStatus.IDLE
    val (workMinutes, workSeconds) = if (editable) {
        timer.minutes to timer.seconds
    } else {
        pomodoroRemainingParts(timer, PomodoroPhase.WORK, nowMs)
    }
    val (restMinutes, restSeconds) = if (editable) {
        timer.restMinutes to timer.restSeconds
    } else {
        pomodoroRemainingParts(timer, PomodoroPhase.REST, nowMs)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PomodoroPhaseColumn(
            label = "Work",
            isIndicated = timer.phase == PomodoroPhase.WORK,
            isRunning = timer.phase == PomodoroPhase.WORK && timer.status == TimerStatus.RUNNING,
            minutes = workMinutes,
            seconds = workSeconds,
            editable = editable,
            onSelect = { onSelectPhase(PomodoroPhase.WORK) },
            onDigit = { field, digit -> onDigit(PomodoroPhase.WORK, field, digit) },
            onBackspace = { field -> onBackspace(PomodoroPhase.WORK, field) },
            onSubmit = onSubmit,
            focusRequester = firstFieldFocus,
        )
        PomodoroPhaseColumn(
            label = "Rest",
            isIndicated = timer.phase == PomodoroPhase.REST,
            isRunning = timer.phase == PomodoroPhase.REST && timer.status == TimerStatus.RUNNING,
            minutes = restMinutes,
            seconds = restSeconds,
            editable = editable,
            onSelect = { onSelectPhase(PomodoroPhase.REST) },
            onDigit = { field, digit -> onDigit(PomodoroPhase.REST, field, digit) },
            onBackspace = { field -> onBackspace(PomodoroPhase.REST, field) },
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun PomodoroPhaseColumn(
    label: String,
    isIndicated: Boolean,
    isRunning: Boolean,
    minutes: Int,
    seconds: Int,
    editable: Boolean,
    onSelect: () -> Unit,
    onDigit: (DurationField, Int) -> Unit,
    onBackspace: (DurationField) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PhaseIndicatorDot(isIndicated = isIndicated, isRunning = isRunning)
        Text(
            label,
            color = if (isIndicated) Accent else FaceDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        if (editable) {
            EditableDurationFieldsMmSs(minutes, seconds, onDigit, onBackspace, onSubmit, focusRequester = focusRequester)
        } else {
            ReadOnlyDurationMmSs(minutes, seconds, accent = isRunning)
        }
    }
}

/** A solid dot marks the side that's actually counting down; an outline
 * marks one that's indicated but waiting on Play. Neither is text, so it
 * reads the same whichever phase it's pointing at. */
@Composable
private fun PhaseIndicatorDot(isIndicated: Boolean, isRunning: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .then(
                when {
                    isIndicated && isRunning -> Modifier.background(Accent, CircleShape)
                    isIndicated -> Modifier.border(1.5.dp, Accent, CircleShape)
                    else -> Modifier
                },
            ),
    )
}

/** [phase]'s remaining time for display: the live countdown if it's the
 * indicated side and actually counting, otherwise exactly what Start would
 * run -- which after a ring is the phase just handed over to, at full
 * length. A side that isn't indicated shows whatever was saved the last time
 * it was switched away from, or its full duration if it's never been touched. */
private fun pomodoroRemainingParts(timer: TimerData, phase: PomodoroPhase, nowMs: Long): Pair<Int, Int> {
    val remainingMs = when {
        phase != timer.phase -> timer.otherPhaseRemainingMs ?: timer.durationMs(phase)
        timer.status == TimerStatus.RUNNING -> (timer.endAtEpochMs ?: nowMs) - nowMs
        else -> timer.pausedRemainingMs ?: timer.durationMs(phase)
    }.coerceAtLeast(0L)
    val totalSeconds = remainingMs / 1000
    return (totalSeconds / 60).toInt() to (totalSeconds % 60).toInt()
}

@Composable
private fun Header(name: String, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember(name) { mutableStateOf(name) }
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    // Only fall back to the default name once the user is
                    // done editing, not on every keystroke -- otherwise
                    // clearing the field to type a custom name immediately
                    // snapped back to "Timer" before they could type it.
                    if (!focusState.isFocused && text.isBlank()) {
                        text = "Timer"
                    }
                },
            singleLine = true,
            // Enter finishes the rename: commit what's there and drop focus,
            // which closes the keyboard. The commit is explicit rather than
            // left to the debounce below, so a name isn't lost if the card
            // scrolls out of composition inside that half second; the blank
            // fallback is applied first so Enter on an empty field saves
            // "Timer" rather than nothing.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isBlank()) text = "Timer"
                    onRename(text)
                    focusManager.clearFocus()
                },
            ),
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Face, fontWeight = FontWeight.Bold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Accent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        TextButton(onClick = onDelete) {
            Text("×", color = FaceDim, fontSize = 20.sp)
        }
    }
    // Debounced commit so a rename isn't lost without needing an explicit
    // save action, without persisting on every single keystroke.
    LaunchedEffect(text) {
        if (text != name) {
            delay(500)
            onRename(text)
        }
    }
}

@Composable
private fun SoundRow(soundId: String, onSoundChange: (String) -> Unit, onPreview: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = SOUNDS.find { it.id == soundId } ?: SOUNDS.first()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            TextButton(onClick = { expanded = true }) {
                Text(current.label, color = Face)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SOUNDS.forEach { sound ->
                    DropdownMenuItem(
                        text = { Text(sound.label) },
                        onClick = {
                            onSoundChange(sound.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        TextButton(onClick = onPreview) {
            Icon(MusicNotesIcon, contentDescription = "Preview tone", tint = FaceDim, modifier = Modifier.size(18.dp))
        }
    }
}

// The work timer drops back to IDLE when stopped rather than PAUSED, so that
// its duration fields stay editable -- "Stop" then "Start" is also how you
// correct the time after forgetting to do either.
//
// A ringing timer offers Start, not Pause: for a pomodoro that's the handover
// to the phase now indicated, and pressing it silences the alarm on the way.
private fun startPauseLabel(status: TimerStatus, isWork: Boolean): String = when (status) {
    TimerStatus.IDLE, TimerStatus.RINGING -> "Start"
    TimerStatus.PAUSED -> "Resume"
    TimerStatus.RUNNING -> if (isWork) "Stop" else "Pause"
}

private fun remainingMs(timer: TimerData, nowMs: Long): Long = when (timer.status) {
    TimerStatus.RUNNING -> (timer.endAtEpochMs ?: nowMs) - nowMs
    TimerStatus.PAUSED -> timer.pausedRemainingMs ?: 0L
    else -> 0L
}.coerceAtLeast(0L)

private fun remainingParts(timer: TimerData, nowMs: Long): Triple<Int, Int, Int> {
    val totalSeconds = remainingMs(timer, nowMs) / 1000
    val hours = (totalSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return Triple(hours, minutes, seconds)
}

private val clockFormat by lazy { SimpleDateFormat("h:mm a", Locale.getDefault()) }

private fun formatClockTime(epochMs: Long): String = clockFormat.format(Date(epochMs))
