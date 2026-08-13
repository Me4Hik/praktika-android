package com.me4hik.praktika.data.backup.write

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [BackupWriteStateRepository] for Stage 6.2A host tests.
 * Mirrors DataStore atomic compare-and-update semantics for conditional APIs.
 */
class InMemoryBackupWriteStateRepository(
    initial: BackupWriteState = BackupWriteState(
        treeUriHint = null,
        authorizedTreeUri = null,
        lastSuccessfulBackupAtEpochMillis = null,
        lastFailureCategory = null,
        needsReconnect = false,
    ),
) : BackupWriteStateRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow(initial)

    var statusPersistShouldFail: Boolean = false
    var commitAuthorizedShouldThrow: Boolean = false
    var disableAutomaticBackupShouldThrow: Boolean = false

    override fun observe(): Flow<BackupWriteState> = state

    override suspend fun snapshot(): BackupWriteState = state.value

    override suspend fun authorizeCurrentTreeUriHint(): AuthorizeCurrentHintResult = mutex.withLock {
        val hint = state.value.treeUriHint
        if (!BackupTreeUriSanitizer.isUsableTreeUriString(hint)) {
            return AuthorizeCurrentHintResult.NoUsableHint
        }
        val old = state.value.authorizedTreeUri
        state.value = state.value.copy(
            authorizedTreeUri = hint,
            needsReconnect = false,
            lastFailureCategory = null,
            lastSuccessfulBackupAtEpochMillis =
                if (old == hint) state.value.lastSuccessfulBackupAtEpochMillis else null,
        )
        AuthorizeCurrentHintResult.Success
    }

    override suspend fun commitAuthorizedFolderAfterVerifiedWrite(
        uriString: String,
        successfulBackupAtEpochMillis: Long,
    ): CommitAuthorizedFolderResult = mutex.withLock {
        if (commitAuthorizedShouldThrow) {
            throw IllegalStateException("injected commit failure")
        }
        if (!BackupTreeUriSanitizer.isUsableTreeUriString(uriString)) {
            return CommitAuthorizedFolderResult.InvalidUri
        }
        state.value = state.value.copy(
            treeUriHint = uriString,
            authorizedTreeUri = uriString,
            lastSuccessfulBackupAtEpochMillis = successfulBackupAtEpochMillis,
            lastFailureCategory = null,
            needsReconnect = false,
        )
        CommitAuthorizedFolderResult.Success
    }

    override suspend fun disableAutomaticBackup() = mutex.withLock {
        if (disableAutomaticBackupShouldThrow) {
            throw IllegalStateException("injected disable failure")
        }
        state.value = state.value.copy(
            authorizedTreeUri = null,
            lastSuccessfulBackupAtEpochMillis = null,
            lastFailureCategory = null,
            needsReconnect = false,
        )
    }

    override suspend fun markNeedsReconnect() = mutex.withLock {
        state.value = state.value.copy(
            needsReconnect = true,
            lastFailureCategory = BackupFailureStatus.PERMISSION_LOST,
        )
    }

    override suspend fun markReconnectSucceeded(matchingUriString: String): ReconnectResult =
        mutex.withLock {
            val auth = state.value.authorizedTreeUri
            if (!BackupTreeUriSanitizer.isUsableTreeUriString(auth)) {
                return ReconnectResult.NoAuthorizedFolder
            }
            if (auth != matchingUriString) {
                return ReconnectResult.UriMismatch
            }
            state.value = state.value.copy(
                needsReconnect = false,
                lastFailureCategory = null,
            )
            ReconnectResult.Success
        }

    override suspend fun markBackupSuccess(successfulBackupAtEpochMillis: Long) = mutex.withLock {
        failIfRequested()
        state.value = state.value.copy(
            lastSuccessfulBackupAtEpochMillis = successfulBackupAtEpochMillis,
            lastFailureCategory = null,
        )
    }

    override suspend fun markBackupFailure(category: BackupFailureStatus) = mutex.withLock {
        failIfRequested()
        state.value = state.value.copy(
            lastFailureCategory = category,
            needsReconnect = if (category == BackupFailureStatus.PERMISSION_LOST) {
                true
            } else {
                state.value.needsReconnect
            },
        )
    }

    override suspend fun markNoChangeHealthy(latestValidBackupCreatedAtEpochMillis: Long) =
        mutex.withLock {
            failIfRequested()
            state.value = state.value.copy(
                lastFailureCategory = null,
                lastSuccessfulBackupAtEpochMillis = latestValidBackupCreatedAtEpochMillis,
            )
        }

    override suspend fun markBackupSuccessIfAuthorized(
        expectedAuthorizedUri: String,
        successfulBackupAtEpochMillis: Long,
    ): ConditionalStatusUpdateResult = mutex.withLock {
        failIfRequested()
        if (state.value.authorizedTreeUri != expectedAuthorizedUri) {
            return ConditionalStatusUpdateResult.StaleAttempt
        }
        state.value = state.value.copy(
            lastSuccessfulBackupAtEpochMillis = successfulBackupAtEpochMillis,
            lastFailureCategory = null,
        )
        ConditionalStatusUpdateResult.Applied
    }

    override suspend fun markNoChangeHealthyIfAuthorized(
        expectedAuthorizedUri: String,
        latestValidBackupCreatedAtEpochMillis: Long,
    ): ConditionalStatusUpdateResult = mutex.withLock {
        failIfRequested()
        if (state.value.authorizedTreeUri != expectedAuthorizedUri) {
            return ConditionalStatusUpdateResult.StaleAttempt
        }
        state.value = state.value.copy(
            lastFailureCategory = null,
            lastSuccessfulBackupAtEpochMillis = latestValidBackupCreatedAtEpochMillis,
        )
        ConditionalStatusUpdateResult.Applied
    }

    override suspend fun markBackupFailureIfAuthorized(
        expectedAuthorizedUri: String,
        category: BackupFailureStatus,
    ): ConditionalStatusUpdateResult = mutex.withLock {
        failIfRequested()
        if (state.value.authorizedTreeUri != expectedAuthorizedUri) {
            return ConditionalStatusUpdateResult.StaleAttempt
        }
        state.value = state.value.copy(
            lastFailureCategory = category,
            needsReconnect = if (category == BackupFailureStatus.PERMISSION_LOST) {
                true
            } else {
                state.value.needsReconnect
            },
        )
        ConditionalStatusUpdateResult.Applied
    }

    override suspend fun markNeedsReconnectIfAuthorized(
        expectedAuthorizedUri: String,
    ): ConditionalStatusUpdateResult = mutex.withLock {
        failIfRequested()
        if (state.value.authorizedTreeUri != expectedAuthorizedUri) {
            return ConditionalStatusUpdateResult.StaleAttempt
        }
        state.value = state.value.copy(
            needsReconnect = true,
            lastFailureCategory = BackupFailureStatus.PERMISSION_LOST,
        )
        ConditionalStatusUpdateResult.Applied
    }

    suspend fun setHint(hint: String?) = mutex.withLock {
        state.value = state.value.copy(treeUriHint = hint)
    }

    suspend fun setAuthorized(uri: String?) = mutex.withLock {
        state.value = state.value.copy(authorizedTreeUri = uri)
    }

    suspend fun replace(state: BackupWriteState) = mutex.withLock {
        this.state.value = state
    }

    private fun failIfRequested() {
        if (statusPersistShouldFail) {
            throw IllegalStateException("injected status persistence failure")
        }
    }
}
