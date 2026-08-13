// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A outcome → status mapping
package com.me4hik.praktika.data.backup.write

/**
 * Maps Stage 6.0 [BackupOutcome] to Stage 6.1 [BackupFailureStatus].
 * Written / NoChange produce no failure category.
 */
object BackupOutcomeStatusMapper {
    fun failureStatusOf(outcome: BackupOutcome): BackupFailureStatus? {
        return when (outcome) {
            is BackupOutcome.Written,
            is BackupOutcome.NoChange,
            -> null

            BackupOutcome.BothSlotsInvalid,
            BackupOutcome.UntrustedArtifactsPresent,
            -> BackupFailureStatus.UNTRUSTED_ARTIFACTS

            BackupOutcome.AmbiguousSlot -> BackupFailureStatus.AMBIGUOUS_SLOT
            BackupOutcome.StorageReadFailed -> BackupFailureStatus.STORAGE_READ_FAILED
            BackupOutcome.StorageAccessUnreliable -> BackupFailureStatus.STORAGE_ACCESS_UNRELIABLE
            BackupOutcome.SequenceExhausted -> BackupFailureStatus.SEQUENCE_EXHAUSTED
            BackupOutcome.UnsafeDatabaseState -> BackupFailureStatus.UNSAFE_DATABASE
            is BackupOutcome.WriteFailed -> mapWriteFailure(outcome.category)
        }
    }

    fun mapWriteFailure(category: BackupWriteFailureCategory): BackupFailureStatus {
        return when (category) {
            BackupWriteFailureCategory.PERMISSION_LOST -> BackupFailureStatus.PERMISSION_LOST
            BackupWriteFailureCategory.STORAGE_UNAVAILABLE -> BackupFailureStatus.STORAGE_UNAVAILABLE
            BackupWriteFailureCategory.PROVIDER_FAILURE -> BackupFailureStatus.STORAGE_UNAVAILABLE
            BackupWriteFailureCategory.SLOT_AMBIGUOUS -> BackupFailureStatus.AMBIGUOUS_SLOT
            BackupWriteFailureCategory.DELETE_FAILED,
            BackupWriteFailureCategory.CREATE_FAILED,
            BackupWriteFailureCategory.NAME_MISMATCH,
            BackupWriteFailureCategory.WRITE_FAILED,
            BackupWriteFailureCategory.ENVELOPE_ASSEMBLY_FAILED,
            -> BackupFailureStatus.WRITE_FAILED

            BackupWriteFailureCategory.POST_WRITE_READ_FAILED,
            BackupWriteFailureCategory.POST_WRITE_VALIDATION_FAILED,
            BackupWriteFailureCategory.EXPECTED_IDENTITY_MISMATCH,
            -> BackupFailureStatus.POST_WRITE_VALIDATION_FAILED

            BackupWriteFailureCategory.EXPORT_READ_FAILURE -> BackupFailureStatus.EXPORT_FAILED
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
