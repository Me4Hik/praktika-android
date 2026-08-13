// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - pure A/B slot read selection
package com.me4hik.praktika.data.backup.slot

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult

object BackupSlotSelector {
    fun selectBest(candidates: List<BackupSlotCandidate>): BackupSlotId? {
        val valid = candidates.mapNotNull { candidate ->
            val envelope = candidate.envelope ?: return@mapNotNull null
            candidate.slotId to envelope
        }

        if (valid.isEmpty()) {
            return null
        }

        val best = valid.maxWith(
            compareBy<Pair<BackupSlotId, PraktikaBackupEnvelope>> { it.second.backupSequence }
                .thenBy { it.second.createdAtEpochMillis }
                .thenBy { if (it.first == BackupSlotId.B) 1 else 0 },
        )

        return best.first
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
