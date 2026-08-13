// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 DataStore write-state repository
package com.me4hik.praktika.data.backup.write

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.me4hik.praktika.data.backup.storage.BackupTreePreferencesKeys
import com.me4hik.praktika.data.backup.storage.backupTreePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Stage 6.1 persistence for URI-bound automatic backup authorization + folder-scoped status.
 * Shares the single production prefs file with [com.me4hik.praktika.data.backup.storage.DataStoreBackupTreeConfigRepository].
 *
 * [markBackupSuccess] preserves [BackupWriteState.needsReconnect]; reconnect is cleared only by
 * explicit reconnect / authorize / setup / disable transitions.
 *
 * DataStore edit failures propagate; CancellationException is never swallowed.
 * Compound transitions use a single [DataStore.edit] so failed transactions leave prior durable state.
 */
class DataStoreBackupWriteStateRepository(
    dataStore: DataStore<Preferences>,
) : BackupWriteStateRepository {
    constructor(context: Context) : this(context.applicationContext.backupTreePreferencesDataStore)

    private val dataStore = dataStore

    override fun observe(): Flow<BackupWriteState> = dataStore.data.map { preferences ->
        preferences.toWriteState()
    }

    override suspend fun snapshot(): BackupWriteState = observe().first()

    override suspend fun authorizeCurrentTreeUriHint(): AuthorizeCurrentHintResult {
        var result: AuthorizeCurrentHintResult = AuthorizeCurrentHintResult.NoUsableHint
        dataStore.edit { preferences ->
            val hintRaw = preferences[BackupTreePreferencesKeys.TREE_URI_HINT]
            if (!BackupTreeUriSanitizer.isUsableTreeUriString(hintRaw)) {
                result = AuthorizeCurrentHintResult.NoUsableHint
                return@edit
            }
            val newAuthorized = hintRaw!!
            val oldAuthorized = preferences[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI]
            preferences[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI] = newAuthorized
            preferences[BackupTreePreferencesKeys.NEEDS_RECONNECT] = false
            preferences.remove(BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY)
            if (oldAuthorized != newAuthorized) {
                preferences.remove(BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT)
            }
            result = AuthorizeCurrentHintResult.Success
        }
        return result
    }

    override suspend fun commitAuthorizedFolderAfterVerifiedWrite(
        uriString: String,
        successfulBackupAtEpochMillis: Long,
    ): CommitAuthorizedFolderResult {
        if (!BackupTreeUriSanitizer.isUsableTreeUriString(uriString)) {
            return CommitAuthorizedFolderResult.InvalidUri
        }
        dataStore.edit { preferences ->
            preferences[BackupTreePreferencesKeys.TREE_URI_HINT] = uriString
            preferences[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI] = uriString
            preferences[BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT] =
                successfulBackupAtEpochMillis
            preferences.remove(BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY)
            preferences[BackupTreePreferencesKeys.NEEDS_RECONNECT] = false
        }
        return CommitAuthorizedFolderResult.Success
    }

    override suspend fun disableAutomaticBackup() {
        dataStore.edit { preferences ->
            preferences.remove(BackupTreePreferencesKeys.AUTHORIZED_TREE_URI)
            preferences.remove(BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT)
            preferences.remove(BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY)
            preferences[BackupTreePreferencesKeys.NEEDS_RECONNECT] = false
        }
    }

    override suspend fun markNeedsReconnect() {
        dataStore.edit { preferences ->
            preferences[BackupTreePreferencesKeys.NEEDS_RECONNECT] = true
            preferences[BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY] =
                BackupFailureStatus.PERMISSION_LOST.name
        }
    }

    override suspend fun markReconnectSucceeded(matchingUriString: String): ReconnectResult {
        var result: ReconnectResult = ReconnectResult.NoAuthorizedFolder
        dataStore.edit { preferences ->
            val authorized = preferences[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI]
            if (!BackupTreeUriSanitizer.isUsableTreeUriString(authorized)) {
                result = ReconnectResult.NoAuthorizedFolder
                return@edit
            }
            if (authorized != matchingUriString) {
                result = ReconnectResult.UriMismatch
                return@edit
            }
            preferences[BackupTreePreferencesKeys.NEEDS_RECONNECT] = false
            preferences.remove(BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY)
            result = ReconnectResult.Success
        }
        return result
    }

    override suspend fun markBackupSuccess(successfulBackupAtEpochMillis: Long) {
        dataStore.edit { preferences ->
            preferences[BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT] =
                successfulBackupAtEpochMillis
            preferences.remove(BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY)
            // needsReconnect intentionally preserved — cleared only by reconnect/authorize/setup/disable
        }
    }

    override suspend fun markBackupFailure(category: BackupFailureStatus) {
        dataStore.edit { preferences ->
            preferences[BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY] = category.name
            if (category == BackupFailureStatus.PERMISSION_LOST) {
                preferences[BackupTreePreferencesKeys.NEEDS_RECONNECT] = true
            }
        }
    }

    override suspend fun markNoChangeHealthy(latestValidBackupCreatedAtEpochMillis: Long) {
        dataStore.edit { preferences ->
            preferences.remove(BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY)
            preferences[BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT] =
                latestValidBackupCreatedAtEpochMillis
        }
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A conditional status APIs
    override suspend fun markBackupSuccessIfAuthorized(
        expectedAuthorizedUri: String,
        successfulBackupAtEpochMillis: Long,
    ): ConditionalStatusUpdateResult {
        var result: ConditionalStatusUpdateResult = ConditionalStatusUpdateResult.StaleAttempt
        dataStore.edit { preferences ->
            if (preferences[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI] != expectedAuthorizedUri) {
                result = ConditionalStatusUpdateResult.StaleAttempt
                return@edit
            }
            preferences[BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT] =
                successfulBackupAtEpochMillis
            preferences.remove(BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY)
            result = ConditionalStatusUpdateResult.Applied
        }
        return result
    }

    override suspend fun markNoChangeHealthyIfAuthorized(
        expectedAuthorizedUri: String,
        latestValidBackupCreatedAtEpochMillis: Long,
    ): ConditionalStatusUpdateResult {
        var result: ConditionalStatusUpdateResult = ConditionalStatusUpdateResult.StaleAttempt
        dataStore.edit { preferences ->
            if (preferences[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI] != expectedAuthorizedUri) {
                result = ConditionalStatusUpdateResult.StaleAttempt
                return@edit
            }
            preferences.remove(BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY)
            preferences[BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT] =
                latestValidBackupCreatedAtEpochMillis
            result = ConditionalStatusUpdateResult.Applied
        }
        return result
    }

    override suspend fun markBackupFailureIfAuthorized(
        expectedAuthorizedUri: String,
        category: BackupFailureStatus,
    ): ConditionalStatusUpdateResult {
        var result: ConditionalStatusUpdateResult = ConditionalStatusUpdateResult.StaleAttempt
        dataStore.edit { preferences ->
            if (preferences[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI] != expectedAuthorizedUri) {
                result = ConditionalStatusUpdateResult.StaleAttempt
                return@edit
            }
            preferences[BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY] = category.name
            if (category == BackupFailureStatus.PERMISSION_LOST) {
                preferences[BackupTreePreferencesKeys.NEEDS_RECONNECT] = true
            }
            result = ConditionalStatusUpdateResult.Applied
        }
        return result
    }

    override suspend fun markNeedsReconnectIfAuthorized(
        expectedAuthorizedUri: String,
    ): ConditionalStatusUpdateResult {
        var result: ConditionalStatusUpdateResult = ConditionalStatusUpdateResult.StaleAttempt
        dataStore.edit { preferences ->
            if (preferences[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI] != expectedAuthorizedUri) {
                result = ConditionalStatusUpdateResult.StaleAttempt
                return@edit
            }
            preferences[BackupTreePreferencesKeys.NEEDS_RECONNECT] = true
            preferences[BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY] =
                BackupFailureStatus.PERMISSION_LOST.name
            result = ConditionalStatusUpdateResult.Applied
        }
        return result
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private fun Preferences.toWriteState(): BackupWriteState {
        val hint = this[BackupTreePreferencesKeys.TREE_URI_HINT]
        val authorizedRaw = this[BackupTreePreferencesKeys.AUTHORIZED_TREE_URI]
        val authorized = when {
            authorizedRaw == null -> null
            authorizedRaw.isBlank() -> authorizedRaw
            else -> authorizedRaw
        }
        return BackupWriteState(
            treeUriHint = hint,
            authorizedTreeUri = authorized,
            lastSuccessfulBackupAtEpochMillis = this[BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT],
            lastFailureCategory = BackupFailureStatus.fromPersistedName(
                this[BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY],
            ),
            needsReconnect = this[BackupTreePreferencesKeys.NEEDS_RECONNECT] ?: false,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
