package com.selman.timeboxr.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selman.timeboxr.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TimerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val soundUriString by viewModel.notificationSoundUri.collectAsState()
    val playSoundOnBreak by viewModel.playSoundOnBreakComplete.collectAsState()
    val dailyGoalHours by viewModel.dailyGoalHours.collectAsState()

    val soundPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setNotificationSoundUri(uri?.toString())
        }
    }

    val soundName = remember(soundUriString) {
        val uri = soundUriString?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        uri?.let {
            runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull()
        } ?: "Default"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column {
                Text("Timer alert sound", style = MaterialTheme.typography.titleMedium)
                Text(
                    soundName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                OutlinedButton(onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Timer alert sound")
                        val current = soundUriString?.let { Uri.parse(it) }
                            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                    }
                    soundPickerLauncher.launch(intent)
                }) { Text("Change sound") }
                Text(
                    "Only changes TimeBoxr's own alert — not your phone's system alarm.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(end = 12.dp)) {
                    Text("Play sound after break", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "When off, a finished break only vibrates.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = playSoundOnBreak, onCheckedChange = { viewModel.setPlaySoundOnBreakComplete(it) })
            }

            HorizontalDivider()

            Column {
                Text("Daily goal", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Total work + break time you're aiming for each day.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        viewModel.setDailyGoalHours(dailyGoalHours - 1)
                    }) {
                        Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("$dailyGoalHours h", modifier = Modifier.width(56.dp))
                    IconButton(onClick = {
                        viewModel.setDailyGoalHours(dailyGoalHours + 1)
                    }) {
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
