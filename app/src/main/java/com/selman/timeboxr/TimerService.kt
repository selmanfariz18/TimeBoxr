package com.selman.timeboxr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Foreground service that owns the running countdown.
 *
 * It posts an ongoing, lock-screen-visible notification with Pause/Resume/Stop
 * actions while running, and a Complete action once the phase finishes. All UI
 * (the Activity) reads [uiState] and never runs the countdown itself, so the
 * timer keeps going even if the app is closed or the screen is locked.
 */
class TimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "timeboxr_timer"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.selman.timeboxr.action.START"
        const val ACTION_PAUSE = "com.selman.timeboxr.action.PAUSE"
        const val ACTION_RESUME = "com.selman.timeboxr.action.RESUME"
        const val ACTION_STOP = "com.selman.timeboxr.action.STOP"
        const val ACTION_COMPLETE = "com.selman.timeboxr.action.COMPLETE"

        const val EXTRA_WORK_MINUTES = "extra_work_minutes"
        const val EXTRA_BREAK_MINUTES = "extra_break_minutes"

        private val _uiState = MutableStateFlow(TimerUiState())
        val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()
    }

    private var countDownTimer: CountDownTimer? = null
    private var ringtone: Ringtone? = null

    private var workMinutes = SettingsRepository.DEFAULT_WORK_MINUTES
    private var breakMinutes = SettingsRepository.DEFAULT_BREAK_MINUTES

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                workMinutes = intent.getIntExtra(EXTRA_WORK_MINUTES, workMinutes)
                breakMinutes = intent.getIntExtra(EXTRA_BREAK_MINUTES, breakMinutes)
                startPhase(TimerPhase.WORK, workMinutes)
            }

            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()

            ACTION_COMPLETE -> {
                val next = if (_uiState.value.phase == TimerPhase.WORK) TimerPhase.BREAK else TimerPhase.WORK
                val minutes = if (next == TimerPhase.WORK) workMinutes else breakMinutes
                startPhase(next, minutes)
            }
        }
        return START_STICKY
    }

    private fun startPhase(phase: TimerPhase, minutes: Int) {
        stopRingtone()
        countDownTimer?.cancel()

        val totalMillis = minutes.coerceAtLeast(1) * 60_000L
        _uiState.value = TimerUiState(
            phase = phase,
            runState = TimerRunState.RUNNING,
            totalMillis = totalMillis,
            remainingMillis = totalMillis
        )
        startForegroundCompat(buildNotification())
        runCountDown(totalMillis)
    }

    private fun runCountDown(millis: Long) {
        countDownTimer = object : CountDownTimer(millis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                _uiState.value = _uiState.value.copy(remainingMillis = millisUntilFinished)
                updateNotification()
            }

            override fun onFinish() {
                _uiState.value = _uiState.value.copy(
                    remainingMillis = 0L,
                    runState = TimerRunState.FINISHED
                )
                updateNotification()
                playAlarmSound()
            }
        }.start()
    }

    private fun pauseTimer() {
        if (_uiState.value.runState != TimerRunState.RUNNING) return
        countDownTimer?.cancel()
        _uiState.value = _uiState.value.copy(runState = TimerRunState.PAUSED)
        updateNotification()
    }

    private fun resumeTimer() {
        if (_uiState.value.runState != TimerRunState.PAUSED) return
        _uiState.value = _uiState.value.copy(runState = TimerRunState.RUNNING)
        updateNotification()
        runCountDown(_uiState.value.remainingMillis)
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        stopRingtone()
        val resetMillis = workMinutes * 60_000L
        _uiState.value = TimerUiState(
            phase = TimerPhase.WORK,
            runState = TimerRunState.IDLE,
            totalMillis = resetMillis,
            remainingMillis = resetMillis
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playAlarmSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = _uiState.value
        val phaseLabel = getString(if (state.phase == TimerPhase.WORK) R.string.phase_work else R.string.phase_break)

        val contentText = when (state.runState) {
            TimerRunState.FINISHED -> getString(R.string.notification_finished, phaseLabel)
            TimerRunState.PAUSED -> getString(R.string.notification_paused, formatTime(state.remainingMillis))
            else -> formatTime(state.remainingMillis)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(phaseLabel)
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(state.runState == TimerRunState.RUNNING || state.runState == TimerRunState.PAUSED)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        when (state.runState) {
            TimerRunState.RUNNING -> {
                builder.addAction(0, getString(R.string.action_pause), servicePendingIntent(ACTION_PAUSE))
                builder.addAction(0, getString(R.string.action_stop), servicePendingIntent(ACTION_STOP))
            }

            TimerRunState.PAUSED -> {
                builder.addAction(0, getString(R.string.action_resume), servicePendingIntent(ACTION_RESUME))
                builder.addAction(0, getString(R.string.action_stop), servicePendingIntent(ACTION_STOP))
            }

            TimerRunState.FINISHED -> {
                builder.addAction(0, getString(R.string.action_complete), servicePendingIntent(ACTION_COMPLETE))
                builder.addAction(0, getString(R.string.action_stop), servicePendingIntent(ACTION_STOP))
            }

            TimerRunState.IDLE -> Unit
        }

        return builder.build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        countDownTimer?.cancel()
        stopRingtone()
        super.onDestroy()
    }
}
