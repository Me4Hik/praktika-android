package com.me4hik.praktika.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface LanguagePreferenceRepository {
    val language: Flow<AppLanguage>
    val languageSelected: Flow<Boolean>
    suspend fun setLanguage(language: AppLanguage)
    suspend fun markLanguageSelected()
    suspend fun ensureExistingUserDefault(isPracticeStarted: Boolean)
}

class LanguagePreferenceException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class DataStoreLanguagePreferenceRepository(
    context: Context,
) : LanguagePreferenceRepository {
    private val dataStore = context.applicationContext.praktikaPreferencesDataStore

    override val language: Flow<AppLanguage> = dataStore.data
        .map { preferences ->
            AppLanguage.fromStorage(preferences[APP_LANGUAGE_KEY])
        }
        .catch { exception ->
            throw LanguagePreferenceException("Failed to read app language preference", exception)
        }

    override val languageSelected: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[APP_LANGUAGE_SELECTED_KEY] == true
        }
        .catch { exception ->
            throw LanguagePreferenceException("Failed to read app language selected flag", exception)
        }

    override suspend fun setLanguage(language: AppLanguage) {
        try {
            dataStore.edit { preferences ->
                preferences[APP_LANGUAGE_KEY] = language.tag
            }
        } catch (exception: Exception) {
            throw LanguagePreferenceException("Failed to write app language preference", exception)
        }
    }

    override suspend fun markLanguageSelected() {
        try {
            dataStore.edit { preferences ->
                preferences[APP_LANGUAGE_SELECTED_KEY] = true
            }
        } catch (exception: Exception) {
            throw LanguagePreferenceException("Failed to mark app language selected", exception)
        }
    }

    override suspend fun ensureExistingUserDefault(isPracticeStarted: Boolean) {
        val selected = languageSelected.first()
        if (selected) {
            return
        }
        if (!isPracticeStarted) {
            return
        }
        try {
            dataStore.edit { preferences ->
                preferences[APP_LANGUAGE_KEY] = AppLanguage.DEFAULT.tag
                preferences[APP_LANGUAGE_SELECTED_KEY] = true
            }
        } catch (exception: Exception) {
            throw LanguagePreferenceException(
                "Failed to migrate existing-user language default",
                exception,
            )
        }
    }

    private companion object {
        val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")
        val APP_LANGUAGE_SELECTED_KEY = booleanPreferencesKey("app_language_selected")
    }
}
