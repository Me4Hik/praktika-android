// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.diagnosticIdentityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "praktika_diagnostic_identity",
)

class DiagnosticIdentityStore(
    private val context: Context,
) {
    val installIdFlow = context.diagnosticIdentityDataStore.data.map { preferences ->
        preferences[INSTALL_ID_KEY] ?: ""
    }

    fun getOrCreateInstallId(): String = runBlocking {
        val existing = context.diagnosticIdentityDataStore.data.first()[INSTALL_ID_KEY]
        if (!existing.isNullOrBlank()) {
            return@runBlocking existing
        }
        val created = UUID.randomUUID().toString()
        context.diagnosticIdentityDataStore.edit { preferences ->
            preferences[INSTALL_ID_KEY] = created
        }
        created
    }

    suspend fun getOrCreateInstallIdAsync(): String {
        val existing = context.diagnosticIdentityDataStore.data.first()[INSTALL_ID_KEY]
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        context.diagnosticIdentityDataStore.edit { preferences ->
            preferences[INSTALL_ID_KEY] = created
        }
        return created
    }

    private companion object {
        val INSTALL_ID_KEY = stringPreferencesKey("install_id")
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
