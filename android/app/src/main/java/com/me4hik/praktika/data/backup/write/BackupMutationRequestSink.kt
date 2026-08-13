// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C mutation backup request sink
package com.me4hik.praktika.data.backup.write

/**
 * Narrow domain-facing request surface for post-commit automatic backup.
 * Implementations must be non-blocking / fire-and-forget and must not throw
 * ordinary backup failures into mutation callers.
 */
fun interface BackupMutationRequestSink {
    fun requestBackup(reason: BackupRequestReason)
}

object NoOpBackupMutationRequestSink : BackupMutationRequestSink {
    override fun requestBackup(reason: BackupRequestReason) = Unit
}

class AuthorizedBackupMutationRequestSink(
    private val authorizedBackupService: AuthorizedBackupService,
) : BackupMutationRequestSink {
    override fun requestBackup(reason: BackupRequestReason) {
        authorizedBackupService.requestBackup(
            reason = reason,
            trigger = BackupAttemptTrigger.MUTATION,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
