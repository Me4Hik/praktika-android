// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1
package com.me4hik.praktika.data.backup.restore

sealed interface BackupRestoreResult {
    data object Success : BackupRestoreResult

    data object TargetNotEmpty : BackupRestoreResult

    data class TargetDatabaseUnsafe(
        val reason: TargetDatabaseUnsafeReason,
    ) : BackupRestoreResult

    data class InvalidBackup(
        val reason: BackupRestoreDomainFailureReason,
    ) : BackupRestoreResult

    data class IncompatibleSeed(
        val backupSeed: Int,
        val targetSeed: Int,
        val classification: IncompatibleSeedClassification,
    ) : BackupRestoreResult

    data class StaticQuestionMismatch(
        val questionId: Int,
        val reason: StaticQuestionMismatchReason,
    ) : BackupRestoreResult

    data class PostValidationFailure(
        val reason: PostValidationFailureReason,
        val detail: String? = null,
    ) : BackupRestoreResult

    data class DatabaseWriteFailure(
        val exceptionClassName: String,
    ) : BackupRestoreResult
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
