// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 shared DataStore ownership
package com.me4hik.praktika.data.backup.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreBackupTreeConfigRepository(
    dataStore: DataStore<Preferences>,
) : BackupTreeConfigRepository {
    constructor(context: Context) : this(context.applicationContext.backupTreePreferencesDataStore)

    private val dataStore = dataStore

    override val treeUriHint: Flow<String?> = dataStore.data.map { preferences ->
        preferences[BackupTreePreferencesKeys.TREE_URI_HINT]
    }

    override suspend fun saveTreeUri(uriString: String) {
        dataStore.edit { preferences ->
            preferences[BackupTreePreferencesKeys.TREE_URI_HINT] = uriString
        }
    }

    override suspend fun clearTreeUri() {
        dataStore.edit { preferences ->
            preferences.remove(BackupTreePreferencesKeys.TREE_URI_HINT)
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
