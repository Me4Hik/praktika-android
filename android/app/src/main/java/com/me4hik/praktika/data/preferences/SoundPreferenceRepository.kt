// 06.08.2026 Settings Schedule cursor by Me4Hik START - DataStore для звука уведомлений
package com.me4hik.praktika.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface SoundPreferenceRepository {
    val soundEnabled: Flow<Boolean>
    suspend fun setSoundEnabled(enabled: Boolean)
}

class SoundPreferenceException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class DataStoreSoundPreferenceRepository(
    context: Context,
) : SoundPreferenceRepository {
    private val dataStore = context.applicationContext.praktikaPreferencesDataStore

    override val soundEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SOUND_ENABLED_KEY] ?: DEFAULT_SOUND_ENABLED
        }
        .catch { exception ->
            throw SoundPreferenceException("Failed to read sound preference", exception)
        }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        try {
            dataStore.edit { preferences ->
                preferences[SOUND_ENABLED_KEY] = enabled
            }
        } catch (exception: Exception) {
            throw SoundPreferenceException("Failed to write sound preference", exception)
        }
    }

    private companion object {
        val SOUND_ENABLED_KEY = booleanPreferencesKey("notification_sound_enabled")
        const val DEFAULT_SOUND_ENABLED = true
    }
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
