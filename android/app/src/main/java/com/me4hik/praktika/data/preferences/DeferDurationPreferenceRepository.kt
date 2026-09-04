// QUESTION_DEFER_FINAL_V1 — DataStore preference for question defer duration
package com.me4hik.praktika.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface DeferDurationPreferenceRepository {
    val deferDurationMinutes: Flow<Int>
    suspend fun setDeferDurationMinutes(minutes: Int)
}

class DeferDurationPreferenceException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class DataStoreDeferDurationPreferenceRepository(
    context: Context,
) : DeferDurationPreferenceRepository {
    private val dataStore = context.applicationContext.praktikaPreferencesDataStore

    override val deferDurationMinutes: Flow<Int> = dataStore.data
        .map { preferences ->
            DeferDurationOptions.sanitize(
                preferences[DEFER_DURATION_MINUTES_KEY] ?: DeferDurationOptions.DEFAULT_MINUTES,
            )
        }
        .catch { exception ->
            throw DeferDurationPreferenceException("Failed to read defer duration preference", exception)
        }

    override suspend fun setDeferDurationMinutes(minutes: Int) {
        val sanitized = DeferDurationOptions.sanitize(minutes)
        try {
            dataStore.edit { preferences ->
                preferences[DEFER_DURATION_MINUTES_KEY] = sanitized
            }
        } catch (exception: Exception) {
            throw DeferDurationPreferenceException("Failed to write defer duration preference", exception)
        }
    }

    private companion object {
        val DEFER_DURATION_MINUTES_KEY = intPreferencesKey("question_defer_duration_minutes")
    }
}
