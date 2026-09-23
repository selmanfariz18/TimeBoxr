package com.selman.timeboxr

/** Which half of the pomodoro cycle is active. */
enum class TimerPhase {
    WORK,
    BREAK
}

/** Where the running timer currently is. */
enum class TimerRunState {
    /** Nothing started yet (or was just stopped). */
    IDLE,

    /** Counting down. */
    RUNNING,

    /** Counting down but paused. */
    PAUSED,

    /** Reached zero; waiting for the user to tap Complete to move to the next phase. */
    FINISHED
}

/** Snapshot of the timer, shared between the foreground service and the UI. */
data class TimerUiState(
    val phase: TimerPhase = TimerPhase.WORK,
    val runState: TimerRunState = TimerRunState.IDLE,
    val totalMillis: Long = SettingsRepository.DEFAULT_WORK_MINUTES * 60_000L,
    val remainingMillis: Long = SettingsRepository.DEFAULT_WORK_MINUTES * 60_000L
)
