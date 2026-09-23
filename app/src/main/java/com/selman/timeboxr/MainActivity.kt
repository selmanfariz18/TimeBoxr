package com.selman.timeboxr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.selman.timeboxr.ui.TimerScreen
import com.selman.timeboxr.ui.theme.TimeBoxrTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()

    // Drives the "screen won't wake up" banner. Backed by the "Display over
    // other apps" permission — see TimerService.launchAlarmActivityDirectly
    // for why that's what actually matters, not USE_FULL_SCREEN_INTENT.
    private var wakeScreenPermissionGranted by mutableStateOf(true)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            TimeBoxrTheme {
                TimerScreen(
                    viewModel = viewModel,
                    wakeScreenPermissionGranted = wakeScreenPermissionGranted,
                    onRequestWakeScreenPermission = { openOverlaySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time we come back to the foreground, since the only
        // way to grant this is the Settings screen we may have just sent the
        // user to.
        wakeScreenPermissionGranted = isOverlayPermissionGranted()
    }

    private fun isOverlayPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.canDrawOverlays(this)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
