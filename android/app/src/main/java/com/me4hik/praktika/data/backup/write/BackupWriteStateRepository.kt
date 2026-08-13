// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 write state repository API
package com.me4hik.praktika.data.backup.write

import kotlinx.coroutines.flow.Flow

interface BackupWriteStateRepository {
    fun observe(): Flow<BackupWriteState>

    suspend fun snapshot(): BackupWriteState

    suspend fun authorizeCurrentTreeUriHint(): AuthorizeCurrentHintResult

    suspend fun commitAuthorizedFolderAfterVerifiedWrite(
        uriString: String,
        successfulBackupAtEpochMillis: Long,
    ): CommitAuthorizedFolderResult

    suspend fun disableAutomaticBackup()

    suspend fun markNeedsReconnect()

    suspend fun markReconnectSucceeded(matchingUriString: String): ReconnectResult

    suspend fun markBackupSuccess(successfulBackupAtEpochMillis: Long)

    suspend fun markBackupFailure(category: BackupFailureStatus)

    suspend fun markNoChangeHealthy(latestValidBackupCreatedAtEpochMillis: Long)

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A conditional status APIs
    suspend fun markBackupSuccessIfAuthorized(
        expectedAuthorizedUri: String,
        successfulBackupAtEpochMillis: Long,
    ): ConditionalStatusUpdateResult

    suspend fun markNoChangeHealthyIfAuthorized(
        expectedAuthorizedUri: String,
        latestValidBackupCreatedAtEpochMillis: Long,
    ): ConditionalStatusUpdateResult

    suspend fun markBackupFailureIfAuthorized(
        expectedAuthorizedUri: String,
        category: BackupFailureStatus,
    ): ConditionalStatusUpdateResult

    suspend fun markNeedsReconnectIfAuthorized(
        expectedAuthorizedUri: String,
    ): ConditionalStatusUpdateResult
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
}

sealed interface AuthorizeCurrentHintResult {
    data object Success : AuthorizeCurrentHintResult

    data object NoUsableHint : AuthorizeCurrentHintResult
}

sealed interface CommitAuthorizedFolderResult {
    data object Success : CommitAuthorizedFolderResult

    data object InvalidUri : CommitAuthorizedFolderResult
}

sealed interface ReconnectResult {
    data object Success : ReconnectResult

    data object UriMismatch : ReconnectResult

    data object NoAuthorizedFolder : ReconnectResult
}

// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A conditional status result
sealed interface ConditionalStatusUpdateResult {
    data object Applied : ConditionalStatusUpdateResult

    data object StaleAttempt : ConditionalStatusUpdateResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
