// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

data class BackupRestoreSelectedIdentity(
    val slot: BackupSlotId,
    val backupSequence: Long,
    val backupChecksumSha256: String,
    val createdAtEpochMillis: Long,
) {
    fun matches(other: BackupRestoreSelectedIdentity): Boolean {
        return slot == other.slot &&
            backupSequence == other.backupSequence &&
            backupChecksumSha256 == other.backupChecksumSha256
    }

    companion object {
        fun from(slot: BackupSlotId, envelope: PraktikaBackupEnvelope): BackupRestoreSelectedIdentity {
            return BackupRestoreSelectedIdentity(
                slot = slot,
                backupSequence = envelope.backupSequence,
                backupChecksumSha256 = envelope.backupChecksumSha256,
                createdAtEpochMillis = envelope.createdAtEpochMillis,
            )
        }
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
