// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A authorized backup result
package com.me4hik.praktika.data.backup.write

/**
 * Outward-safe AuthorizedBackupService result.
 * Never carries raw URI, answer text, JSON, or exception messages.
 */
data class AuthorizedBackupResult(
    val kind: AuthorizedBackupResultKind,
    val coordinatorOutcome: BackupOutcome? = null,
    val statusUpdate: ConditionalStatusUpdateResult? = null,
    val statusPersistenceFailed: Boolean = false,
)

sealed interface AuthorizedBackupResultKind {
    data object NotConfigured : AuthorizedBackupResultKind

    data object InvalidConfiguration : AuthorizedBackupResultKind

    data object Suppressed : AuthorizedBackupResultKind

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 init mutation suppress
    data object SuppressedInitialization : AuthorizedBackupResultKind
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    data object NeedsReconnect : AuthorizedBackupResultKind

    data object RetrySuppressed : AuthorizedBackupResultKind

    data object CoordinatorCompleted : AuthorizedBackupResultKind
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
