// 05.09.2026 Sound Library V1 cursor by Me4Hik START - sound preferences selected/hidden
package com.me4hik.praktika.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.sound.SoundAssetIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface SoundPreferenceRepository {
    val soundEnabled: Flow<Boolean>
    val selectedSoundId: Flow<String>
    val hiddenBuiltinIds: Flow<Set<String>>

    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun selectSound(id: String)
    suspend fun hideBuiltin(id: String)
    suspend fun restoreBuiltin(id: String)
    suspend fun restoreAllHidden()
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

    override val selectedSoundId: Flow<String> = dataStore.data
        .map { preferences ->
            BuiltinSoundCatalog.resolveOrDefault(preferences[SELECTED_SOUND_ID_KEY]).id
        }
        .catch { exception ->
            throw SoundPreferenceException("Failed to read selected sound id", exception)
        }

    override val hiddenBuiltinIds: Flow<Set<String>> = dataStore.data
        .map { preferences ->
            sanitizeHidden(preferences[HIDDEN_BUILTIN_IDS_KEY].orEmpty())
        }
        .catch { exception ->
            throw SoundPreferenceException("Failed to read hidden builtin ids", exception)
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

    override suspend fun selectSound(id: String) {
        val resolved = BuiltinSoundCatalog.resolveOrDefault(id)
        try {
            dataStore.edit { preferences ->
                val hidden = sanitizeHidden(preferences[HIDDEN_BUILTIN_IDS_KEY].orEmpty())
                if (resolved.isBuiltin && resolved.id in hidden) {
                    // Selecting a hidden builtin restores it into the visible library.
                    preferences[HIDDEN_BUILTIN_IDS_KEY] = hidden - resolved.id
                }
                preferences[SELECTED_SOUND_ID_KEY] = resolved.id
            }
        } catch (exception: Exception) {
            throw SoundPreferenceException("Failed to write selected sound id", exception)
        }
    }

    override suspend fun hideBuiltin(id: String) {
        val asset = BuiltinSoundCatalog.findById(id) ?: return
        if (!asset.isBuiltin) return
        try {
            dataStore.edit { preferences ->
                val currentSelected = BuiltinSoundCatalog
                    .resolveOrDefault(preferences[SELECTED_SOUND_ID_KEY])
                    .id
                if (currentSelected == asset.id) {
                    preferences[SELECTED_SOUND_ID_KEY] = SoundAssetIds.SYSTEM_DEFAULT
                }
                val hidden = sanitizeHidden(preferences[HIDDEN_BUILTIN_IDS_KEY].orEmpty())
                preferences[HIDDEN_BUILTIN_IDS_KEY] = hidden + asset.id
            }
        } catch (exception: Exception) {
            throw SoundPreferenceException("Failed to hide builtin sound", exception)
        }
    }

    override suspend fun restoreBuiltin(id: String) {
        if (!BuiltinSoundCatalog.containsBuiltinId(id)) return
        try {
            dataStore.edit { preferences ->
                val hidden = sanitizeHidden(preferences[HIDDEN_BUILTIN_IDS_KEY].orEmpty())
                preferences[HIDDEN_BUILTIN_IDS_KEY] = hidden - id
            }
        } catch (exception: Exception) {
            throw SoundPreferenceException("Failed to restore builtin sound", exception)
        }
    }

    override suspend fun restoreAllHidden() {
        try {
            dataStore.edit { preferences ->
                preferences[HIDDEN_BUILTIN_IDS_KEY] = emptySet()
            }
        } catch (exception: Exception) {
            throw SoundPreferenceException("Failed to restore all hidden sounds", exception)
        }
    }

    private fun sanitizeHidden(raw: Set<String>): Set<String> {
        return raw.filterTo(linkedSetOf()) { BuiltinSoundCatalog.containsBuiltinId(it) }
    }

    private companion object {
        val SOUND_ENABLED_KEY = booleanPreferencesKey("notification_sound_enabled")
        val SELECTED_SOUND_ID_KEY = stringPreferencesKey("notification_selected_sound_id")
        val HIDDEN_BUILTIN_IDS_KEY = stringSetPreferencesKey("notification_hidden_builtin_ids")
        const val DEFAULT_SOUND_ENABLED = true
    }
}
// 05.09.2026 Sound Library V1 cursor by Me4Hik END
