package com.selman.timeboxr

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Bridges the Compose UI with [TimerService]. The actual countdown lives in the
 * service (so it survives the Activity being destroyed); this class just reads
 * its state and forwards user actions as intents.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val workMinutes: StateFlow<Int> = settingsRepository.workMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_WORK_MINUTES)

    val breakMinutes: StateFlow<Int> = settingsRepository.breakMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_BREAK_MINUTES)

    val timerState: StateFlow<TimerUiState> = TimerService.uiState

    fun setWorkMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setWorkMinutes(minutes) }
    }

    fun setBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setBreakMinutes(minutes) }
    }

    fun start() {
        val app = getApplication<Application>()
        val intent = Intent(app, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_WORK_MINUTES, workMinutes.value)
            putExtra(TimerService.EXTRA_BREAK_MINUTES, breakMinutes.value)
        }
        app.startForegroundService(intent)
    }

    fun pause() = sendAction(TimerService.ACTION_PAUSE)
    fun resume() = sendAction(TimerService.ACTION_RESUME)
    fun stop() = sendAction(TimerService.ACTION_STOP)
    fun complete() = sendAction(TimerService.ACTION_COMPLETE)

    private fun sendAction(action: String) {
        val app = getApplication<Application>()
        app.startForegroundService(Intent(app, TimerService::class.java).setAction(action))
    }
}
