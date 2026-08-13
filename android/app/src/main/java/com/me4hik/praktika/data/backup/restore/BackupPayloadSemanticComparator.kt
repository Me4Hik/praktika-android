// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload

object BackupPayloadSemanticComparator {
    fun canonicalize(payload: PraktikaBackupPayload): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = payload.practiceState,
            scheduleSlots = payload.scheduleSlots.sortedBy { it.slotIndex },
            occurrences = payload.occurrences.sortedWith(
                compareBy({ it.cycleNumber }, { it.cyclePosition }),
            ),
            answers = payload.answers.sortedWith(
                compareBy({ it.cycleNumber }, { it.cyclePosition }),
            ),
        )
    }

    fun equalsSemantically(expected: PraktikaBackupPayload, actual: PraktikaBackupPayload): Boolean {
        return canonicalize(expected) == canonicalize(actual)
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
