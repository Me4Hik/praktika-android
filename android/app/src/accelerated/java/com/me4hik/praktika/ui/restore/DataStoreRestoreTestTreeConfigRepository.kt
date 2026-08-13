package com.me4hik.praktika.ui.restore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.me4hik.praktika.data.backup.storage.BackupTreeConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.restoreTestTreeConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "praktika_restore_test_tree_config",
)

class DataStoreRestoreTestTreeConfigRepository(
    context: Context,
) : BackupTreeConfigRepository {
    private val dataStore = context.applicationContext.restoreTestTreeConfigDataStore

    override val treeUriHint: Flow<String?> = dataStore.data.map { preferences ->
        preferences[RESTORE_TEST_TREE_URI_HINT_KEY]
    }

    override suspend fun saveTreeUri(uriString: String) {
        dataStore.edit { preferences ->
            preferences[RESTORE_TEST_TREE_URI_HINT_KEY] = uriString
        }
    }

    override suspend fun clearTreeUri() {
        dataStore.edit { preferences ->
            preferences.remove(RESTORE_TEST_TREE_URI_HINT_KEY)
        }
    }

    private companion object {
        val RESTORE_TEST_TREE_URI_HINT_KEY = stringPreferencesKey("restore_test_tree_uri_hint")
    }
}
