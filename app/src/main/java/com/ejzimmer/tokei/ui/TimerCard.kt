package com.ejzimmer.tokei.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejzimmer.tokei.audio.SOUNDS
import com.ejzimmer.tokei.data.PomodoroPhase
import com.ejzimmer.tokei.data.TimerData
import com.ejzimmer.tokei.data.TimerStatus
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
    workInfo: WorkCardInfo? = null,
) {
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
        if (workInfo == null) {
            Header(name = timer.name, onRename = onRename, onDelete = onDelete)
        } else {
            // Fixed name, no delete: this one is part of the app rather than
            // a timer you made.
            Text(
                timer.name,
                color = Face,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }

        if (timer.status == TimerStatus.IDLE) {
            if (timer.isPomodoro) {
                PhaseLabel(PomodoroPhase.WORK)
                EditableDurationFields(
                    timer.hours, timer.minutes, timer.seconds,
                    onDigit = { field, digit -> onDigit(PomodoroPhase.WORK, field, digit) },
                    onBackspace = { field -> onBackspace(PomodoroPhase.WORK, field) },
                )
                PhaseLabel(PomodoroPhase.REST)
                EditableDurationFields(
                    timer.restHours, timer.restMinutes, timer.restSeconds,
                    onDigit = { field, digit -> onDigit(PomodoroPhase.REST, field, digit) },
                    onBackspace = { field -> onBackspace(PomodoroPhase.REST, field) },
                )
            } else {
                EditableDurationFields(
                    timer.hours, timer.minutes, timer.seconds,
                    onDigit = { field, digit -> onDigit(PomodoroPhase.WORK, field, digit) },
                    onBackspace = { field -> onBackspace(PomodoroPhase.WORK, field) },
                )
            }
        } else {
            if (timer.isPomodoro) PhaseLabel(timer.phase)
            val (h, m, s) = remainingParts(timer, nowMs)
            ReadOnlyDuration(h, m, s, accent = timer.status == TimerStatus.RUNNING)
        }

        if (workInfo != null) {
            Text(
                workInfo.headline,
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            workInfo.details.forEach { detail ->
                Text(detail, color = FaceDim, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }

        if (!isRinging && timer.lastFinishedAtEpochMs != null) {
            Text(
                if (workInfo == null) {
                    "Last finished at ${formatClockTime(timer.lastFinishedAtEpochMs!!)}"
                } else {
                    "Last cycle finished at ${formatClockTime(timer.lastFinishedAtEpochMs!!)}"
                },
                color = FaceDim,
                fontSize = 12.sp,
            )
        }

        if (workInfo == null && runCount > 0) {
            Text(
                if (runCount == 1) "Run once today" else "Run $runCount times today",
                color = FaceDim,
                fontSize = 12.sp,
            )
        }

        // The work timer never rings and waits to be silenced -- it rolls
        // straight into the next cycle -- so there's no alarm sound to pick.
        if (workInfo == null) {
            SoundRow(soundId = timer.soundId, onSoundChange = onSoundChange, onPreview = onPreviewSound)
        }

        if (timer.isPomodoro && (timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onSkipBack) {
                    Text("◀ 1 min", color = FaceDim)
                }
                TextButton(onClick = onSkipForward) {
                    Text("1 min ▶", color = FaceDim)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised, contentColor = Face),
            ) {
                Text(if (workInfo == null) "Reset" else "Reset to 7:30")
            }
            Button(
                onClick = onStartPause,
                enabled = !isRinging,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
            ) {
                Text(startPauseLabel(timer.status, isWork = workInfo != null))
            }
        }

        if (isRinging) {
            Text(ringingLabel(timer), color = Face, fontWeight = FontWeight.Bold)
            timer.finishedAtEpochMs?.let {
                Text("Finished at ${formatClockTime(it)}", color = Face, fontSize = 13.sp)
            }
            Button(
                onClick = onStopAlarm,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised, contentColor = Face),
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun PhaseLabel(phase: PomodoroPhase) {
    Text(
        if (phase == PomodoroPhase.WORK) "WORK" else "REST",
        color = if (phase == PomodoroPhase.WORK) Accent else FaceDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

// While ringing, timer.phase is still the phase that just finished --
// stopAlarm() is what flips it once the user dismisses the alarm.
private fun ringingLabel(timer: TimerData): String = when {
    !timer.isPomodoro -> "Time's up!"
    timer.phase == PomodoroPhase.WORK -> "Work session done!"
    else -> "Break's over!"
}

@Composable
private fun Header(name: String, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember(name) { mutableStateOf(name) }
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
            Text("▶", color = FaceDim, fontSize = 16.sp)
        }
    }
}

// The work timer drops back to IDLE when stopped rather than PAUSED, so that
// its duration fields stay editable -- "Stop" then "Start" is also how you
// correct the time after forgetting to do either.
private fun startPauseLabel(status: TimerStatus, isWork: Boolean): String = when (status) {
    TimerStatus.IDLE -> "Start"
    TimerStatus.PAUSED -> "Resume"
    TimerStatus.RUNNING -> if (isWork) "Stop" else "Pause"
    TimerStatus.RINGING -> "Pause"
}

private fun remainingParts(timer: TimerData, nowMs: Long): Triple<Int, Int, Int> {
    val remainingMs = when (timer.status) {
        TimerStatus.RUNNING -> (timer.endAtEpochMs ?: nowMs) - nowMs
        TimerStatus.PAUSED -> timer.pausedRemainingMs ?: 0L
        else -> 0L
    }.coerceAtLeast(0L)
    val totalSeconds = remainingMs / 1000
    val hours = (totalSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return Triple(hours, minutes, seconds)
}

private val clockFormat by lazy { SimpleDateFormat("h:mm a", Locale.getDefault()) }

private fun formatClockTime(epochMs: Long): String = clockFormat.format(Date(epochMs))
