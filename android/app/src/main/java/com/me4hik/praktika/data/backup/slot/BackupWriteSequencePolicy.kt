// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - pure next-write slot and sequence policy
package com.me4hik.praktika.data.backup.slot

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

sealed class BackupWritePlan {
    data class Ready(
        val targetSlot: BackupSlotId,
        val nextSequence: Long,
    ) : BackupWritePlan()

    data object SequenceExhausted : BackupWritePlan()
}

object BackupWriteSequencePolicy {
    fun planNextWrite(validSlots: List<Pair<BackupSlotId, PraktikaBackupEnvelope>>): BackupWritePlan {
        if (validSlots.isEmpty()) {
            return BackupWritePlan.Ready(
                targetSlot = BackupSlotId.A,
                nextSequence = 1L,
            )
        }

        val latest = validSlots.maxWith(
            compareBy<Pair<BackupSlotId, PraktikaBackupEnvelope>> { it.second.backupSequence }
                .thenBy { it.second.createdAtEpochMillis }
                .thenBy { if (it.first == BackupSlotId.B) 1 else 0 },
        )

        if (latest.second.backupSequence == Long.MAX_VALUE) {
            return BackupWritePlan.SequenceExhausted
        }

        return BackupWritePlan.Ready(
            targetSlot = latest.first.other(),
            nextSequence = latest.second.backupSequence + 1L,
        )
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
