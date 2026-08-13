// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 shared backup prefs owner
package com.me4hik.praktika.data.backup.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single production Preferences DataStore for backup tree hint + Stage 6.1 write auth/status.
 * Exactly one preferencesDataStore(name = "praktika_backup_tree_config") declaration.
 */
internal val Context.backupTreePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = BackupTreePreferencesKeys.DATA_STORE_NAME,
)

internal object BackupTreePreferencesKeys {
    const val DATA_STORE_NAME = "praktika_backup_tree_config"

    val TREE_URI_HINT = stringPreferencesKey("backup_tree_uri_hint")
    val AUTHORIZED_TREE_URI = stringPreferencesKey("automatic_backup_authorized_tree_uri")
    val LAST_SUCCESSFUL_BACKUP_AT = longPreferencesKey("last_successful_backup_at_epoch_ms")
    val LAST_FAILURE_CATEGORY = stringPreferencesKey("last_failure_category")
    val NEEDS_RECONNECT = booleanPreferencesKey("needs_reconnect")
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
