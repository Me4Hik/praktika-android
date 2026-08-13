// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 Backup Now mapper
package com.me4hik.praktika.data.backup.settings

import com.me4hik.praktika.data.backup.write.AuthorizedBackupResult
import com.me4hik.praktika.data.backup.write.AuthorizedBackupResultKind
import com.me4hik.praktika.data.backup.write.BackupFailureStatus
import com.me4hik.praktika.data.backup.write.BackupOutcome
import com.me4hik.praktika.data.backup.write.BackupOutcomeStatusMapper

/**
 * Maps [AuthorizedBackupResult] to URI-free [BackupNowResult].
 * Never exposes coordinator outcome slot/sequence/URI.
 */
object BackupNowResultMapper {
    fun map(result: AuthorizedBackupResult): BackupNowResult {
        return when (val kind = result.kind) {
            AuthorizedBackupResultKind.NotConfigured,
            AuthorizedBackupResultKind.InvalidConfiguration,
            -> BackupNowResult.NotConfigured

            AuthorizedBackupResultKind.NeedsReconnect -> BackupNowResult.NeedsReconnect

            AuthorizedBackupResultKind.Suppressed,
            AuthorizedBackupResultKind.SuppressedInitialization,
            -> BackupNowResult.TemporarilyUnavailable

            AuthorizedBackupResultKind.RetrySuppressed -> BackupNowResult.NeedsReconnect

            AuthorizedBackupResultKind.CoordinatorCompleted -> mapCoordinatorCompleted(result)
        }
    }

    private fun mapCoordinatorCompleted(result: AuthorizedBackupResult): BackupNowResult {
        val outcome = result.coordinatorOutcome
        if (outcome is BackupOutcome.Written) {
            return if (result.statusPersistenceFailed) {
                BackupNowResult.StatusTrackingIncomplete(BackupNowPhysicalResult.WRITTEN)
            } else {
                BackupNowResult.Written
            }
        }
        if (outcome is BackupOutcome.NoChange) {
            return if (result.statusPersistenceFailed) {
                BackupNowResult.StatusTrackingIncomplete(BackupNowPhysicalResult.NO_CHANGE)
            } else {
                BackupNowResult.NoChange
            }
        }
        if (result.statusPersistenceFailed && outcome == null) {
            return BackupNowResult.TransientFailure
        }
        val failure = outcome?.let { BackupOutcomeStatusMapper.failureStatusOf(it) }
            ?: BackupFailureStatus.UNKNOWN
        return when (failure) {
            BackupFailureStatus.PERMISSION_LOST -> BackupNowResult.NeedsReconnect
            BackupFailureStatus.AMBIGUOUS_SLOT,
            BackupFailureStatus.UNTRUSTED_ARTIFACTS,
            BackupFailureStatus.SEQUENCE_EXHAUSTED,
            -> BackupNowResult.NeedsAttention
            BackupFailureStatus.UNSAFE_DATABASE -> BackupNowResult.DataProblem
            BackupFailureStatus.WRITE_FAILED,
            BackupFailureStatus.EXPORT_FAILED,
            BackupFailureStatus.STORAGE_UNAVAILABLE,
            BackupFailureStatus.STORAGE_READ_FAILED,
            BackupFailureStatus.STORAGE_ACCESS_UNRELIABLE,
            BackupFailureStatus.POST_WRITE_VALIDATION_FAILED,
            BackupFailureStatus.UNKNOWN,
            -> BackupNowResult.TransientFailure
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
