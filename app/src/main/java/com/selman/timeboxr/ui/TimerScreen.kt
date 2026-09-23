package com.selman.timeboxr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
fun TimerScreen(viewModel: TimerViewModel) {
    val workMinutes by viewModel.workMinutes.collectAsState()
    val breakMinutes by viewModel.breakMinutes.collectAsState()
    val timerState by viewModel.timerState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("TimeBoxr") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
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
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Text("$value min", modifier = Modifier.width(64.dp))
        IconButton(onClick = { onValueChange(value + 1) }, enabled = enabled) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}
