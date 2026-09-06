package com.me4hik.praktika.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface QuestionWordingPreferenceRepository {
    val wordingMode: Flow<QuestionWordingMode>
    suspend fun setWordingMode(mode: QuestionWordingMode)
}

class QuestionWordingPreferenceException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class DataStoreQuestionWordingPreferenceRepository(
    context: Context,
) : QuestionWordingPreferenceRepository {
    private val dataStore = context.applicationContext.praktikaPreferencesDataStore

    override val wordingMode: Flow<QuestionWordingMode> = dataStore.data
        .map { preferences ->
            QuestionWordingMode.fromStorage(preferences[WORDING_MODE_KEY])
        }
        .catch { exception ->
            throw QuestionWordingPreferenceException("Failed to read question wording preference", exception)
        }

    override suspend fun setWordingMode(mode: QuestionWordingMode) {
        try {
            dataStore.edit { preferences ->
                preferences[WORDING_MODE_KEY] = mode.name
            }
        } catch (exception: Exception) {
            throw QuestionWordingPreferenceException("Failed to write question wording preference", exception)
        }
    }

    private companion object {
        val WORDING_MODE_KEY = stringPreferencesKey("question_wording_mode")
    }
}

fun interface QuestionWordingModeSource {
    suspend fun currentMode(): QuestionWordingMode
}
