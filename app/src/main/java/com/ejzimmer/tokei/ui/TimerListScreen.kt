package com.ejzimmer.tokei.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejzimmer.tokei.data.RunCounts
import com.ejzimmer.tokei.data.TimerStatus

@Composable
fun TimerListScreen(
    viewModel: TimerViewModel,
    notificationsGranted: Boolean,
    exactAlarmGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
) {
    val timers by viewModel.timers.collectAsState()
    val nowMs by viewModel.clockTick.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text(
                "時計 Tokei",
                color = Face,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (!notificationsGranted) {
                PermissionBanner(
                    "Enable notifications so a finished timer can be stopped from the lock screen.",
                    "Enable",
                    onRequestNotifications,
                )
            }
            if (!exactAlarmGranted) {
                PermissionBanner(
                    "Allow exact alarms so timers ring precisely on time, even locked or backgrounded.",
                    "Allow",
                    onRequestExactAlarm,
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(timers, key = { it.id }) { timer ->
                    TimerCard(
                        timer = timer,
                        runCount = RunCounts.getRunCount(timer.id),
                        nowMs = nowMs,
                        onRename = { viewModel.rename(timer.id, it) },
                        onDelete = { viewModel.deleteTimer(timer.id) },
                        onSoundChange = { viewModel.setSound(timer.id, it) },
                        onPreviewSound = { viewModel.previewSound(timer.soundId) },
                        onDigit = { field, digit -> viewModel.enterDigit(timer.id, field, digit) },
                        onBackspace = { field -> viewModel.backspaceDigit(timer.id, field) },
                        onStartPause = {
                            if (timer.status == TimerStatus.RUNNING) {
                                viewModel.pause(timer.id)
                            } else {
                                viewModel.start(timer.id)
                            }
                        },
                        onReset = { viewModel.reset(timer.id) },
                        onStopAlarm = { viewModel.stopAlarm(timer.id) },
                    )
                }
            }

            Button(
                onClick = { viewModel.addTimer() },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised, contentColor = Face),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("+ Add timer")
            }
        }
    }
}

@Composable
private fun PermissionBanner(message: String, actionLabel: String, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(message, color = FaceDim, fontSize = 13.sp)
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(actionLabel)
        }
    }
}
