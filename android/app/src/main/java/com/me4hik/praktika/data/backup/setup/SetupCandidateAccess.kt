// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A setup candidate access
package com.me4hik.praktika.data.backup.setup

import com.me4hik.praktika.data.backup.storage.BackupStorageProvider

/**
 * Isolates SAF connect/grant/storage construction from setup orchestration.
 * Test doubles inject in-memory storage without real DocumentsContract.
 */
interface SetupCandidateAccess {
    suspend fun connectTransient(uriString: String, grantFlags: Int): SetupCandidateConnectResult

    fun createStorage(uriString: String): BackupStorageProvider?

    fun releasePersistedGrant(uriString: String, grantFlags: Int)

    fun hasPersistedGrant(uriString: String): Boolean
}

sealed interface SetupCandidateConnectResult {
    data object Success : SetupCandidateConnectResult

    data object PermissionLost : SetupCandidateConnectResult

    data object Unavailable : SetupCandidateConnectResult

    data object ProviderFailure : SetupCandidateConnectResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
