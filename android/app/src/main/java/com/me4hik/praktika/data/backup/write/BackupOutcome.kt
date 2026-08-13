// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A trusted outcome timestamps
package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.model.BackupSlotId

sealed interface BackupOutcome {
    data class Written(
        val slot: BackupSlotId,
        val sequence: Long,
        val createdAtEpochMillis: Long,
    ) : BackupOutcome

    data class NoChange(
        val latestValidBackupCreatedAtEpochMillis: Long,
    ) : BackupOutcome

    data object BothSlotsInvalid : BackupOutcome

    data object UntrustedArtifactsPresent : BackupOutcome

    data object AmbiguousSlot : BackupOutcome

    data object StorageReadFailed : BackupOutcome

    data object StorageAccessUnreliable : BackupOutcome

    data object SequenceExhausted : BackupOutcome

    data object UnsafeDatabaseState : BackupOutcome

    data class WriteFailed(
        val category: BackupWriteFailureCategory,
    ) : BackupOutcome
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
