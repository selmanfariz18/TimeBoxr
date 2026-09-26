package com.selman.timeboxr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timeboxr_settings")

/**
 * Persists the user's settings: last-set work/break durations, the chosen
 * alert sound, whether break-complete should also play a sound, the daily
 * time goal, and today's running total of work+break time.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        val WORK_MINUTES = intPreferencesKey("work_minutes")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val NOTIFICATION_SOUND_URI = stringPreferencesKey("notification_sound_uri")
        val PLAY_SOUND_ON_BREAK_COMPLETE = booleanPreferencesKey("play_sound_on_break_complete")
        val DAILY_GOAL_HOURS = intPreferencesKey("daily_goal_hours")
        val TODAY_TOTAL_MILLIS = longPreferencesKey("today_total_millis")
        val TODAY_DATE = stringPreferencesKey("today_date")

        const val DEFAULT_WORK_MINUTES = 30
        const val DEFAULT_BREAK_MINUTES = 1
        const val DEFAULT_PLAY_SOUND_ON_BREAK_COMPLETE = false
        const val DEFAULT_DAILY_GOAL_HOURS = 8

        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
        const val MIN_GOAL_HOURS = 1
        const val MAX_GOAL_HOURS = 16
    }

    val workMinutes: Flow<Int> =
        context.dataStore.data.map { it[WORK_MINUTES] ?: DEFAULT_WORK_MINUTES }

    val breakMinutes: Flow<Int> =
        context.dataStore.data.map { it[BREAK_MINUTES] ?: DEFAULT_BREAK_MINUTES }

    /** null means "use the phone's default alarm/notification sound." */
    val notificationSoundUri: Flow<String?> =
        context.dataStore.data.map { it[NOTIFICATION_SOUND_URI] }

    val playSoundOnBreakComplete: Flow<Boolean> =
        context.dataStore.data.map { it[PLAY_SOUND_ON_BREAK_COMPLETE] ?: DEFAULT_PLAY_SOUND_ON_BREAK_COMPLETE }

    val dailyGoalHours: Flow<Int> =
        context.dataStore.data.map { it[DAILY_GOAL_HOURS] ?: DEFAULT_DAILY_GOAL_HOURS }

    /** Today's accumulated work+break time, resetting automatically at midnight. */
    val todayTotalMillis: Flow<Long> = context.dataStore.data.map { prefs ->
        if (prefs[TODAY_DATE] == todayDateString()) prefs[TODAY_TOTAL_MILLIS] ?: 0L else 0L
    }

    suspend fun setWorkMinutes(minutes: Int) {
        context.dataStore.edit { it[WORK_MINUTES] = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) }
    }

    suspend fun setBreakMinutes(minutes: Int) {
        context.dataStore.edit { it[BREAK_MINUTES] = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) }
    }

    suspend fun setNotificationSoundUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(NOTIFICATION_SOUND_URI) else prefs[NOTIFICATION_SOUND_URI] = uri
        }
    }

    suspend fun setPlaySoundOnBreakComplete(enabled: Boolean) {
        context.dataStore.edit { it[PLAY_SOUND_ON_BREAK_COMPLETE] = enabled }
    }

    suspend fun setDailyGoalHours(hours: Int) {
        context.dataStore.edit { it[DAILY_GOAL_HOURS] = hours.coerceIn(MIN_GOAL_HOURS, MAX_GOAL_HOURS) }
    }

    /** Adds elapsed running time to today's total, rolling over at midnight. */
    suspend fun addElapsedTodayMillis(deltaMillis: Long) {
        context.dataStore.edit { prefs ->
            val today = todayDateString()
            val existingTotal = if (prefs[TODAY_DATE] == today) prefs[TODAY_TOTAL_MILLIS] ?: 0L else 0L
            prefs[TODAY_DATE] = today
            prefs[TODAY_TOTAL_MILLIS] = existingTotal + deltaMillis
        }
    }

    private fun todayDateString(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
