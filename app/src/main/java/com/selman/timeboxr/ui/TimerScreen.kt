package com.selman.timeboxr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selman.timeboxr.TimerPhase
import com.selman.timeboxr.TimerRunState
import com.selman.timeboxr.TimerViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    wakeScreenPermissionGranted: Boolean = true,
    onRequestWakeScreenPermission: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val workMinutes by viewModel.workMinutes.collectAsState()
    val breakMinutes by viewModel.breakMinutes.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val todayTotalMillis by viewModel.todayTotalMillis.collectAsState()
    val dailyGoalHours by viewModel.dailyGoalHours.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TimeBoxr") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!wakeScreenPermissionGranted) {
                WakeScreenPermissionBanner(onRequestWakeScreenPermission)
            }

            val phaseLabel = if (timerState.phase == TimerPhase.WORK) "Work" else "Break"
            Text(phaseLabel, style = MaterialTheme.typography.headlineSmall)

            val totalSeconds = timerState.remainingMillis / 1000
            val minutesLeft = totalSeconds / 60
            val secondsLeft = totalSeconds % 60
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", minutesLeft, secondsLeft),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )

            when (timerState.runState) {
                TimerRunState.IDLE -> {
                    Button(onClick = { viewModel.start() }) { Text("Start") }
                }

                TimerRunState.RUNNING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = { viewModel.pause() }) { Text("Pause") }
                        OutlinedButton(onClick = { viewModel.stop() }) { Text("Stop") }
                    }
                }

                TimerRunState.PAUSED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { viewModel.resume() }) { Text("Resume") }
                        OutlinedButton(onClick = { viewModel.stop() }) { Text("Stop") }
                    }
                }

                TimerRunState.FINISHED -> {
                    Text("Time's up!", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { viewModel.complete() }) { Text("Complete") }
                        OutlinedButton(onClick = { viewModel.stop() }) { Text("Stop") }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val editable = timerState.runState == TimerRunState.IDLE
            DurationStepper(
                label = "Work minutes",
                value = workMinutes,
                enabled = editable,
                onValueChange = { viewModel.setWorkMinutes(it) }
            )
            DurationStepper(
                label = "Break minutes",
                value = breakMinutes,
                enabled = editable,
                onValueChange = { viewModel.setBreakMinutes(it) }
            )
            if (!editable) {
                Text(
                    "Stop the timer to change durations",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            DailyGoalSummary(todayTotalMillis = todayTotalMillis, goalHours = dailyGoalHours)
        }
    }
}

@Composable
private fun DailyGoalSummary(todayTotalMillis: Long, goalHours: Int) {
    val goalMillis = goalHours * 3_600_000L
    val fraction = if (goalMillis > 0) (todayTotalMillis.toFloat() / goalMillis).coerceIn(0f, 1f) else 0f
    val totalMinutes = todayTotalMillis / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Today: ${hours}h ${minutes}m of ${goalHours}h goal",
            style = MaterialTheme.typography.bodyMedium
        )
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
}

@Composable
private fun WakeScreenPermissionBanner(onRequest: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Screen wake-up is off for the finished-timer alert",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Needs the \"Display over other apps\" permission, otherwise you'll only hear the alarm sound and the screen won't turn on.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Button(onClick = onRequest) { Text("Enable in Settings") }
        }
    }
}

@Composable
private fun DurationStepper(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        IconButton(onClick = { onValueChange(value - 1) }, enabled = enabled) {
            Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text("$value min", modifier = Modifier.width(64.dp))
        IconButton(onClick = { onValueChange(value + 1) }, enabled = enabled) {
            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
