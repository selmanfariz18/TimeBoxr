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
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        /** An unattended alarm rings for at most this long, then goes quiet on its own. */
        private const val MAX_RING_MILLIS = 60_000L

        /** How often accumulated running time is written to disk. */
        private const val FLUSH_INTERVAL_MILLIS = 10_000L

        const val ACTION_START = "com.selman.timeboxr.action.START"
        const val ACTION_PAUSE = "com.selman.timeboxr.action.PAUSE"
        const val ACTION_RESUME = "com.selman.timeboxr.action.RESUME"
        const val ACTION_STOP = "com.selman.timeboxr.action.STOP"
        const val ACTION_COMPLETE = "com.selman.timeboxr.action.COMPLETE"

        const val EXTRA_WORK_MINUTES = "extra_work_minutes"
        const val EXTRA_BREAK_MINUTES = "extra_break_minutes"

        private val _uiState = MutableStateFlow(TimerUiState())
        val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

        private val _todayTotalMillis = MutableStateFlow(0L)
        val todayTotalMillis: StateFlow<Long> = _todayTotalMillis.asStateFlow()
    }

    private var countDownTimer: CountDownTimer? = null
    private var ringtone: Ringtone? = null
    private val ringtoneStopHandler = Handler(Looper.getMainLooper())

    private var workMinutes = SettingsRepository.DEFAULT_WORK_MINUTES
    private var breakMinutes = SettingsRepository.DEFAULT_BREAK_MINUTES

    private lateinit var settingsRepository: SettingsRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var unflushedElapsedMillis = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        settingsRepository = SettingsRepository(this)
        serviceScope.launch {
            _todayTotalMillis.value = settingsRepository.todayTotalMillis.first()
        }
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
                accumulateElapsed(1000L)
            }

            override fun onFinish() {
                _uiState.value = _uiState.value.copy(
                    remainingMillis = 0L,
                    runState = TimerRunState.FINISHED
                )
                updateNotification()
                flushElapsed()
                vibrateForAlarm()
                maybePlayAlarmSound()
                launchAlarmActivityDirectly()
            }
        }.start()
    }

    /**
     * Belt-and-suspenders alongside the notification's full-screen intent
     * (which some ROMs, e.g. certain LineageOS builds, never actually grant
     * through their Settings UI despite the permission being declared).
     * "Display over other apps" is a much older, more universally supported
     * special permission that also exempts this call from Android's
     * background-activity-start restrictions, so if it's granted this will
     * pop the alarm screen and wake the device even where the full-screen
     * intent path silently does nothing.
     */
    private fun launchAlarmActivityDirectly() {
        val canLaunch = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (!canLaunch) return

        val intent = Intent(this, TimerAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { startActivity(intent) }
    }

    private fun pauseTimer() {
        if (_uiState.value.runState != TimerRunState.RUNNING) return
        countDownTimer?.cancel()
        flushElapsed()
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
        flushElapsed()
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

    /** Tracks running (work+break) time for the day, flushing to disk periodically. */
    private fun accumulateElapsed(deltaMillis: Long) {
        _todayTotalMillis.value += deltaMillis
        unflushedElapsedMillis += deltaMillis
        if (unflushedElapsedMillis >= FLUSH_INTERVAL_MILLIS) {
            flushElapsed()
        }
    }

    private fun flushElapsed() {
        if (unflushedElapsedMillis <= 0L) return
        val toFlush = unflushedElapsedMillis
        unflushedElapsedMillis = 0L
        serviceScope.launch { settingsRepository.addElapsedTodayMillis(toFlush) }
    }

    private fun vibrateForAlarm() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return

        val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    /** Work always alerts with sound; break only does if the user opted in. */
    private fun maybePlayAlarmSound() {
        serviceScope.launch {
            val phase = _uiState.value.phase
            val shouldPlaySound = phase == TimerPhase.WORK || settingsRepository.playSoundOnBreakComplete.first()
            if (shouldPlaySound) {
                val customUri = settingsRepository.notificationSoundUri.first()
                playAlarmSound(customUri)
            }
        }
    }

    private fun playAlarmSound(customUriString: String?) {
        val uri = customUriString?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }

        // If nobody's there to dismiss it (Complete/Stop), don't let it ring
        // forever — go quiet on its own after a while. The timer itself stays
        // in FINISHED, waiting for Complete/Stop, whenever you get back to it.
        ringtoneStopHandler.removeCallbacksAndMessages(null)
        ringtoneStopHandler.postDelayed({ stopRingtone() }, MAX_RING_MILLIS)
    }

    private fun stopRingtone() {
        ringtoneStopHandler.removeCallbacksAndMessages(null)
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(phaseLabel)
            .setContentText(contentText)
            .setOngoing(state.runState == TimerRunState.RUNNING || state.runState == TimerRunState.PAUSED)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        if (state.runState == TimerRunState.FINISHED) {
            // Wake the screen and pop the Complete/Stop screen over the lock
            // screen, the same way an alarm clock does, instead of leaving
            // the user to notice a silent notification.
            val alarmIntent = alarmActivityPendingIntent()
            builder.setContentIntent(alarmIntent)
            builder.setFullScreenIntent(alarmIntent, true)
        } else {
            builder.setContentIntent(openAppPendingIntent())
        }

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

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun alarmActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, TimerAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        flushElapsed()
        super.onDestroy()
    }
}
