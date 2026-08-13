// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A authorized storage resolver
package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.storage.BackupStorageProvider

/**
 * Resolves LocalSafBackupStorage (or test double) for an already-authorized URI.
 * Must never take new persistable permission, mutate treeUriHint, or change authorization.
 */
fun interface AuthorizedBackupStorageResolver {
    suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult
}

sealed interface AuthorizedStorageResolveResult {
    data class Ready(
        val storage: BackupStorageProvider,
    ) : AuthorizedStorageResolveResult

    data object PermissionLost : AuthorizedStorageResolveResult

    data object Unavailable : AuthorizedStorageResolveResult

    data object ProviderError : AuthorizedStorageResolveResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
