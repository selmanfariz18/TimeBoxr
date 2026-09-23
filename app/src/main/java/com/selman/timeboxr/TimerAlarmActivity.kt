package com.selman.timeboxr

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selman.timeboxr.ui.theme.TimeBoxrTheme

/**
 * Shown full-screen, over the lock screen, when a work/break timer finishes.
 * Triggered by [TimerService] via a notification full-screen intent, so it
 * wakes the screen the same way an alarm clock or incoming call does instead
 * of leaving the user to notice a silent notification.
 */
class TimerAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreenAndWake()

        setContent {
            TimeBoxrTheme {
                val state by TimerService.uiState.collectAsState()

                // If the timer already moved on some other way (e.g. Complete
                // was tapped straight from the notification), close this screen.
                LaunchedEffect(state.runState) {
                    if (state.runState != TimerRunState.FINISHED) finish()
                }

                AlarmScreen(
                    phase = state.phase,
                    onComplete = {
                        sendServiceAction(TimerService.ACTION_COMPLETE)
                        finish()
                    },
                    onStop = {
                        sendServiceAction(TimerService.ACTION_STOP)
                        finish()
                    }
                )
            }
        }
    }

    private fun showOverLockScreenAndWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun sendServiceAction(action: String) {
        startForegroundService(Intent(this, TimerService::class.java).setAction(action))
    }
}

@Composable
private fun AlarmScreen(
    phase: TimerPhase,
    onComplete: () -> Unit,
    onStop: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val phaseLabel = if (phase == TimerPhase.WORK) "Work" else "Break"
            Text("$phaseLabel session finished", fontSize = 28.sp)

            Row(
                modifier = Modifier.padding(top = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = onComplete) { Text("Complete") }
                OutlinedButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}
