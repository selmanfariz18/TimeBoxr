package com.selman.timeboxr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timeboxr_settings")

/**
 * Persists the user's last-set work/break durations so they're remembered
 * the next time the app is opened.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        val WORK_MINUTES = intPreferencesKey("work_minutes")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")

        const val DEFAULT_WORK_MINUTES = 30
        const val DEFAULT_BREAK_MINUTES = 1

        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
    }

    val workMinutes: Flow<Int> =
        context.dataStore.data.map { it[WORK_MINUTES] ?: DEFAULT_WORK_MINUTES }

    val breakMinutes: Flow<Int> =
        context.dataStore.data.map { it[BREAK_MINUTES] ?: DEFAULT_BREAK_MINUTES }

    suspend fun setWorkMinutes(minutes: Int) {
        context.dataStore.edit { it[WORK_MINUTES] = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) }
    }

    suspend fun setBreakMinutes(minutes: Int) {
        context.dataStore.edit { it[BREAK_MINUTES] = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) }
    }
}
